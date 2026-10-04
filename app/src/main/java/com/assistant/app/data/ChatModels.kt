package com.assistant.app.data

import com.assistant.app.llm.LlmProvider
import com.assistant.app.llm.model.ReasoningConfig
import com.assistant.app.llm.model.ThinkCapability
import com.assistant.app.llm.model.UiAttachment
import com.assistant.app.llm.model.UiMessage

sealed interface ChatStatus {
    data object Idle : ChatStatus
    data object Searching : ChatStatus
    data object Generating : ChatStatus
    data class Error(val message: String) : ChatStatus
}

enum class VoiceStatus {
    Idle,
    Listening,
    Processing,
    Speaking,
    Error;

    companion object {
        val Transcribing: VoiceStatus get() = Processing
    }
}

data class ChatUiState(
    val conversationId: String? = null,
    val messages: List<UiMessage> = emptyList(),
    val status: ChatStatus = ChatStatus.Idle,
    val draft: String = "",
    val needsSetup: Boolean = false,
    val voiceStatus: VoiceStatus = VoiceStatus.Idle,

    val voiceHint: Boolean = false,

    val pendingAttachments: List<UiAttachment> = emptyList(),

    val attachmentError: String? = null,

    val searchEnabled: Boolean = false,

    val searchNotice: String? = null,

    val isIngestingAttachments: Boolean = false,

    val thinkCapability: ThinkCapability = ThinkCapability.Unsupported,

    val thinkConfig: ReasoningConfig = ReasoningConfig.Auto,
)

sealed interface ChatLlmState {
    data object Loading : ChatLlmState
    data class Ready(
        val provider: LlmProvider,
        val model: String,
        val providerId: Long = 0,

        val modelId: Long = 0,
        val name: String = "",

        val thinkCapability: ThinkCapability = ThinkCapability.Unsupported,
    ) : ChatLlmState
    data object NeedsSetup : ChatLlmState
}
