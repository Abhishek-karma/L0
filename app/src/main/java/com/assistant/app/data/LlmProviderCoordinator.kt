package com.assistant.app.data

import com.assistant.app.llm.GeminiProvider
import com.assistant.app.llm.LlmProvider
import com.assistant.app.llm.OpenAICompatibleProvider
import com.assistant.app.llm.model.inferThinkCapability
import com.assistant.app.ui.settings.isGemini
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class LlmProviderCoordinator(
    private val providerStore: ProviderStore,
    private val httpClient: OkHttpClient,
    scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _chatLlm = MutableStateFlow<ChatLlmState>(ChatLlmState.Loading)
    val chatLlm: StateFlow<ChatLlmState> = _chatLlm.asStateFlow()

    init {
        scope.launch(ioDispatcher) {
            try {
                providerStore.ensureSeeded()
                providerStore.activeSelection().collect { selection ->
                    val provider = selection?.provider
                    val model = selection?.model
                    _chatLlm.value = if (provider == null) {
                        ChatLlmState.NeedsSetup
                    } else {
                        val key = providerStore.apiKey(provider.id)
                        val baseUrl = provider.baseUrl.trim()
                        if (baseUrl.isBlank() || model == null || key.isNullOrBlank()) {
                            ChatLlmState.NeedsSetup
                        } else {
                            val llm: LlmProvider = if (isGemini(baseUrl)) {
                                GeminiProvider(
                                    client = httpClient,
                                    apiKey = key,
                                    model = model.model,
                                    baseUrl = if (baseUrl == "gemini" || baseUrl.isBlank()) {
                                        GeminiProvider.DEFAULT_BASE_URL
                                    } else {
                                        baseUrl
                                    },
                                )
                            } else {
                                OpenAICompatibleProvider(httpClient, baseUrl, key, model.model)
                            }
                            ChatLlmState.Ready(
                                provider = llm,
                                model = model.model,
                                providerId = provider.id,
                                modelId = model.id,
                                name = provider.name,
                                thinkCapability = inferThinkCapability(model.model),
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _chatLlm.value = ChatLlmState.NeedsSetup
            }
        }
    }
}