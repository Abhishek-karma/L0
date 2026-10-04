package com.assistant.app.llm.model

enum class Role { USER, ASSISTANT, SYSTEM }

data class UiMessage(
    val id: String,
    val role: Role,
    val content: String,
    val createdAt: Long,
    val versions: List<String> = emptyList(),
    val selectedVersion: Int = 0,
    val attachments: List<UiAttachment> = emptyList(),
    val reasoning: String = "",

    val sources: List<SearchResult> = emptyList(),
    val followUps: List<String> = emptyList(),

    val webResults: List<SearchResult> = emptyList(),
)

data class ChatRequest(
    val model: String,
    val messages: List<Pair<Role, String>>,

    val images: List<String> = emptyList(),

    val reasoning: ReasoningConfig? = null,
)

data class UiAttachment(
    val id: String,
    val kind: Kind,
    val displayName: String,
    val mime: String,
    val path: String,
    val sizeBytes: Long,
) {
    enum class Kind { IMAGE, TEXT }
}

sealed interface ChatChunk {
    data class Delta(val text: String) : ChatChunk

    data class Reasoning(val text: String) : ChatChunk
    data object Done : ChatChunk

    data class Failure(val error: ProviderError, val detail: String? = null) : ChatChunk
}

enum class ProviderError(val userMessage: String) {
    InvalidCredentials("Invalid API key"),
    AuthenticationFailed("Authentication failed"),
    ModelNotFound("Model not found"),
    QuotaExceeded("Quota exceeded"),
    RateLimited("Rate limited"),
    NetworkUnavailable("Network unavailable"),
    Timeout("Request timed out"),
    ServerError("Provider error"),
    UnsupportedRequest("Unsupported request"),
    InvalidResponse("Malformed response"),
    Unknown("Something went wrong"),
}
