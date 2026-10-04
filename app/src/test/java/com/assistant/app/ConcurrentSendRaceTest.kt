package com.assistant.app

import com.assistant.app.data.ChatLlmState
import com.assistant.app.data.ChatRepository
import com.assistant.app.llm.FakeLlmProvider
import com.assistant.app.llm.ScriptedEvent
import com.assistant.app.llm.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CyclicBarrier

/**
 * `send` guards on `generationController.isGenerating`, but the generation session is only claimed
 * at the very end of the turn, after the database writes and the web search. Two sends that overlap
 * inside that window both pass the guard, so both append a user message and an assistant
 * placeholder before one of them is rejected by `tryStartSession`. The loser leaves an empty
 * assistant bubble behind forever.
 */
class ConcurrentSendRaceTest {

    @Test
    fun `overlapping sends admit only one turn`() = runBlocking {
        val provider = FakeLlmProvider(listOf(ScriptedEvent.Delay(300), ScriptedEvent.Emit("ok")))
        val chatLlm = MutableStateFlow<ChatLlmState>(ChatLlmState.Ready(provider, "test-model"))
        val repository = ChatRepository(
            chatLlm = chatLlm,
            generationDispatcher = Dispatchers.IO,
        )

        val barrier = CyclicBarrier(2)
        val sends = listOf("first", "second").map { text ->
            async(Dispatchers.Default) {
                barrier.await()
                repository.send(text)
            }
        }
        withTimeout(20_000) { sends.awaitAll() }

        val messages = repository.uiState.value.messages
        val userMessages = messages.filter { it.role == Role.USER }
        assertEquals("only one send may be admitted", 1, userMessages.size)

        val assistantMessages = messages.filter { it.role == Role.ASSISTANT }
        assertEquals("an admitted turn keeps exactly one answer", 1, assistantMessages.size)
        assertTrue(
            "no orphan placeholder may be left behind",
            assistantMessages.single().content.isNotBlank(),
        )
        assertEquals(1, provider.requests.size)
    }
}