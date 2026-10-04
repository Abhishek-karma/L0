package com.assistant.app.ui

import androidx.lifecycle.ViewModelProvider
import com.assistant.app.data.ChatLlmState
import com.assistant.app.data.ChatRepository
import com.assistant.app.llm.FakeLlmProvider
import com.assistant.app.llm.ScriptedEvent
import com.assistant.app.ui.chat.ChatViewModel
import com.assistant.app.voice.VoiceOption
import com.assistant.app.voice.VoiceOutput
import kotlinx.coroutines.flow.MutableStateFlow

class ScriptedChatFixture(
    script: List<ScriptedEvent>,
    initialChatLlm: ChatLlmState? = null,

    ttsAvailable: Boolean = false,

    voiceOutputEnabled: Boolean = false,

    followUps: List<String> = emptyList(),

    webSearch: (suspend (String) -> com.assistant.app.llm.model.SearchOutcome?)? = null,
) {
    val provider: FakeLlmProvider = FakeLlmProvider(script)

    val chatLlm: MutableStateFlow<ChatLlmState> =
        MutableStateFlow(initialChatLlm ?: ChatLlmState.Ready(provider, "test-model"))

    private val voiceOutputEnabledFlow = MutableStateFlow(voiceOutputEnabled)

    val factory: ViewModelProvider.Factory =
        ChatViewModel.Factory(
            repository = ChatRepository(
                chatLlm,
                followUpSuggestions = { _, _, _, _ -> followUps },
                webSearch = webSearch,
            ),
            chatLlm = chatLlm,
            voiceOutput = VoiceOutput(
                if (ttsAvailable) ScriptedVoiceOutput() else UnavailableVoiceOutput(),
            ),
            isVoiceOutputEnabled = { voiceOutputEnabledFlow.value },
            voiceOutputEnabled = voiceOutputEnabledFlow,
            setVoiceOutput = { voiceOutputEnabledFlow.value = it },
        )
}

private class ScriptedVoiceOutput : VoiceOutput.Engine {
    override fun isAvailable(): Boolean = true
    override fun voices(onReady: (List<VoiceOption>) -> Unit) = onReady(emptyList())
    override fun speak(text: String, speechRate: Float, voiceId: String?, onDone: () -> Unit) = onDone()
    override fun stop() = Unit
}

private class UnavailableVoiceOutput : VoiceOutput.Engine {
    override fun isAvailable(): Boolean = false
    override fun voices(onReady: (List<VoiceOption>) -> Unit) = onReady(emptyList())
    override fun speak(text: String, speechRate: Float, voiceId: String?, onDone: () -> Unit) = onDone()
    override fun stop() = Unit
}
