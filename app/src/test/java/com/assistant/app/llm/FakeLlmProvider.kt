package com.assistant.app.llm

import com.assistant.app.llm.model.ChatChunk
import com.assistant.app.llm.model.ChatRequest
import com.assistant.app.llm.model.ProviderError
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

sealed interface ScriptedEvent {
    data class Delay(val ms: Long) : ScriptedEvent
    data class Emit(val text: String) : ScriptedEvent

    data class Reasoning(val text: String) : ScriptedEvent
    data class Fail(val error: ProviderError, val detail: String? = null) : ScriptedEvent
}

class FakeLlmProvider(var script: List<ScriptedEvent>) : LlmProvider {

        val requests = mutableListOf<ChatRequest>()

    override fun stream(request: ChatRequest): Flow<ChatChunk> = channelFlow {
        requests += request
        launch {
            for (event in script) {
                when (event) {
                    is ScriptedEvent.Delay -> delay(event.ms)
                    is ScriptedEvent.Emit -> send(ChatChunk.Delta(event.text))
                    is ScriptedEvent.Reasoning -> send(ChatChunk.Reasoning(event.text))
                    is ScriptedEvent.Fail -> {
                        send(ChatChunk.Failure(event.error, event.detail))
                        return@launch
                    }
                }
            }
            send(ChatChunk.Done)
        }
    }
}
