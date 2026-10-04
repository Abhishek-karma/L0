package com.assistant.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.assistant.app.data.ChatLlmState
import com.assistant.app.data.ChatRepository
import com.assistant.app.data.ConversationStore
import com.assistant.app.data.FollowUpSuggestions
import com.assistant.app.data.local.ChatDatabase
import com.assistant.app.llm.LlmProvider
import com.assistant.app.llm.model.ChatChunk
import com.assistant.app.llm.model.ChatRequest
import com.assistant.app.ui.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FollowUpReasoningModelTest {

    @After
    fun tearDown() {
        database?.close()
        Dispatchers.resetMain()
    }

    private var database: ChatDatabase? = null

    private val directExecutor = Executor { it.run() }

    private fun runScenario(thinkMs: Long): TestResult = runTest {
        val db = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ChatDatabase::class.java)
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .allowMainThreadQueries()
            .build()
        database = db
        val store = ConversationStore(db)
        val mainDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(mainDispatcher)

        var call = 0
        val provider = object : LlmProvider {
            override fun stream(request: ChatRequest): Flow<ChatChunk> = flow {
                call++
                if (call == 1) {
                    emit(ChatChunk.Delta("Paris is the capital of France."))
                    emit(ChatChunk.Done)
                } else {
                    repeat((thinkMs / 500).toInt()) {
                        emit(ChatChunk.Reasoning("thinking..."))
                        delay(500)
                    }
                    emit(ChatChunk.Delta("1. What is the population?\n2. What is the best time to visit?"))
                    emit(ChatChunk.Done)
                }
            }
        }

        val chatLlm = MutableStateFlow<ChatLlmState>(ChatLlmState.Ready(provider, "test-model"))
        val repository = ChatRepository(
            chatLlm = chatLlm,
            generationDispatcher = mainDispatcher,
            store = store,
            followUpSuggestions = { p, m, q, a -> FollowUpSuggestions.generate(p, m, q, a) },
            clock = { testScheduler.currentTime },
        )
        val viewModel = ChatViewModel(repository, chatLlm)

        viewModel.send("What is the capital of France?")
        advanceUntilIdle()

        assertEquals(
            listOf("What is the population?", "What is the best time to visit?"),
            viewModel.uiState.value.messages.last().followUps,
        )
    }

    @Test
    fun fastModelShowsFollowUps() {
        runScenario(thinkMs = 1_000)
    }

    @Test
    fun reasoningModelShowsFollowUps() {
        runScenario(thinkMs = 20_000)
    }
}