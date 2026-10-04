package com.assistant.app.llm

import com.assistant.app.llm.model.ChatChunk
import com.assistant.app.llm.model.ChatRequest
import kotlinx.coroutines.flow.Flow

interface LlmProvider {
    fun stream(request: ChatRequest): Flow<ChatChunk>
}
