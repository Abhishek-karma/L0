package com.assistant.app.llm

import com.assistant.app.llm.model.ChatChunk
import com.assistant.app.llm.model.ChatRequest
import com.assistant.app.llm.model.ProviderError
import com.assistant.app.llm.model.ReasoningConfig
import com.assistant.app.llm.model.Role
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException

class OpenAICompatibleProvider(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) : LlmProvider {

    override fun stream(request: ChatRequest): Flow<ChatChunk> = callbackFlow {
        val call = try {
            client.newCall(httpRequest(request))
        } catch (_: IllegalArgumentException) {

            trySend(ChatChunk.Failure(ProviderError.Unknown))
            close()
            return@callbackFlow
        }

        var settled = false

        fun finish(chunk: ChatChunk) {
            if (!settled) {
                settled = true
                trySendBlocking(chunk)
                close()
            }
        }

        call.enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!response.isSuccessful) {
                                finish(statusFailure(response.code, readErrorBody(response.body)))
                                return
                            }
                            val body = response.body
                            if (body == null) {
                                finish(ChatChunk.Failure(ProviderError.InvalidResponse))
                                return
                            }
                            readStream(
                                source = body.source(),
                                sendDelta = { chunk -> !trySendBlocking(chunk).isClosed },
                                settle = ::finish,
                            )
                        } catch (e: IOException) {

                            if (!call.isCanceled()) finish(ioFailure(e))
                        }
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (!call.isCanceled()) finish(ioFailure(e))
                }
            },
        )

        awaitClose { call.cancel() }
    }

    private fun readStream(
        source: BufferedSource,
        sendDelta: (ChatChunk) -> Boolean,
        settle: (ChatChunk) -> Unit,
    ) {
        val parser = SseParser()
        var answerSeen = false

        fun handle(event: SseEvent): Boolean {
            when {
                event.data == DONE_MARKER -> {

                    settle(
                        if (answerSeen) ChatChunk.Done
                        else ChatChunk.Failure(ProviderError.InvalidResponse),
                    )
                    return false
                }

                event.event == ERROR_EVENT -> {
                    settle(errorEventFailure(event.data))
                    return false
                }

                else -> {
                    val chunks = deltasOf(event.data)
                    if (chunks == null) {
                        settle(ChatChunk.Failure(ProviderError.InvalidResponse))
                        return false
                    }
                    for (chunk in chunks) {
                        if (chunk is ChatChunk.Delta || chunk is ChatChunk.Reasoning) {
                            if (!sendDelta(chunk)) return false
                            if (chunk is ChatChunk.Delta && chunk.text.isNotEmpty()) {
                                answerSeen = true
                            }
                        }
                    }
                }
            }
            return true
        }

        while (true) {
            when (val read = readLine(source)) {
                SseLine.Eof -> break
                SseLine.Overlong -> {

                    settle(ChatChunk.Failure(ProviderError.InvalidResponse))
                    return
                }
                is SseLine.Line -> {
                    for (event in parser.parseSse("${read.text}\n")) {
                        if (!handle(event)) return
                    }
                }
            }
        }

        for (event in parser.flush()) {
            if (!handle(event)) return
        }
        settle(
            if (answerSeen) ChatChunk.Done
            else ChatChunk.Failure(ProviderError.InvalidResponse),
        )
    }

    private fun errorEventFailure(data: String): ChatChunk.Failure {
        val detail = errorMessage(data)
        val error = if (detail == null) ProviderError.ServerError else ProviderError.Unknown
        return ChatChunk.Failure(error, detail)
    }

    private fun readLine(source: BufferedSource): SseLine {
        var scanned = 0L
        while (true) {
            val newline = source.buffer.indexOf('\n'.code.toByte(), scanned)
            if (newline != -1L) {
                val text = source.readUtf8(newline)
                source.skip(1)
                return SseLine.Line(text.removeSuffix("\r"))
            }
            if (source.buffer.size > MAX_LINE_BYTES) return SseLine.Overlong
            scanned = source.buffer.size

            if (!source.request(scanned + 1)) {
                val text = if (scanned == 0L) "" else source.readUtf8(scanned)

                val line = text.removeSuffix("\r")
                return if (line.isEmpty()) SseLine.Eof else SseLine.Line(line)
            }
        }
    }

    private fun deltasOf(data: String): List<ChatChunk>? = try {
        val choices = JSONObject(data).optJSONArray("choices")
        when {
            choices == null || choices.length() == 0 -> emptyList()
            else -> {
                val delta = choices.getJSONObject(0).optJSONObject("delta")
                if (delta == null) {
                    emptyList()
                } else {
                    buildList {
                        val reasoning = delta.optString("reasoning_content")
                            .ifEmpty { delta.optString("reasoning") }
                        if (reasoning.isNotEmpty()) add(ChatChunk.Reasoning(reasoning))
                        val content = delta.optString("content")
                        if (content.isNotEmpty()) add(ChatChunk.Delta(content))
                    }
                }
            }
        }
    } catch (_: JSONException) {
        null
    }

    private fun statusFailure(code: Int, body: String?): ChatChunk.Failure {
        val error = when {
            code == 401 || code == 403 -> ProviderError.InvalidCredentials
            code == 429 -> ProviderError.RateLimited
            code >= 500 -> ProviderError.ServerError
            else -> ProviderError.Unknown
        }

        val detail = if (error == ProviderError.Unknown) errorMessage(body) else null
        return ChatChunk.Failure(error, detail)
    }

    private fun errorMessage(body: String?): String? = try {
        body
            ?.let { JSONObject(it).optJSONObject("error")?.optString("message") }
            ?.ifEmpty { null }
    } catch (_: JSONException) {
        null
    }

    private fun readErrorBody(body: ResponseBody?): String? {
        if (body == null) return null
        val source = body.source()
        return if (source.request(MAX_ERROR_BODY_BYTES + 1)) null else source.readUtf8()
    }

    private fun ioFailure(e: IOException): ChatChunk.Failure =
        ChatChunk.Failure(
            if (e is SocketTimeoutException) ProviderError.Timeout else ProviderError.NetworkUnavailable,
        )

    private fun httpRequest(request: ChatRequest): Request {
        val payload = JSONObject().apply {
            put("model", request.model.ifBlank { model })
            put("stream", true)

            (request.reasoning as? ReasoningConfig.Effort)?.let {
                put("reasoning_effort", it.level.name.lowercase())
            }
            put("messages", JSONArray().apply {
                request.messages.forEachIndexed { index, (role, content) ->
                    val message = JSONObject().put("role", role.name.lowercase())

                    if (role == Role.USER && index == request.messages.lastIndex && request.images.isNotEmpty()) {
                        val parts = JSONArray()
                        if (content.isNotBlank()) {
                            parts.put(JSONObject().put("type", "text").put("text", content))
                        }
                        request.images.forEach { url ->
                            parts.put(
                                JSONObject()
                                    .put("type", "image_url")
                                    .put("image_url", JSONObject().put("url", url)),
                            )
                        }
                        message.put("content", parts)
                    } else {
                        message.put("content", content)
                    }
                    put(message)
                }
            })
        }
        val trimmedKey = apiKey.trim()
        val requestBuilder = Request.Builder()
            .url(baseUrl.trimEnd('/') + PATH)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        if (trimmedKey.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $trimmedKey")
        }
        return requestBuilder.build()
    }

    private companion object {
        const val PATH = "/chat/completions"
        const val DONE_MARKER = "[DONE]"

        const val ERROR_EVENT = "error"

        const val MAX_ERROR_BODY_BYTES = 64L * 1024

        const val MAX_LINE_BYTES = 64L * 1024

        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private sealed interface SseLine {

    data class Line(val text: String) : SseLine

    data object Overlong : SseLine

    data object Eof : SseLine
}
