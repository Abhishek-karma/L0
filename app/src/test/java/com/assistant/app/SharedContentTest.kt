package com.assistant.app

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.assistant.app.data.ChatLlmState
import com.assistant.app.data.ChatRepository
import com.assistant.app.data.ChatStatus
import com.assistant.app.data.PromptTemplate
import com.assistant.app.data.PromptTemplateStore
import com.assistant.app.data.SharedContent
import com.assistant.app.data.local.ChatDatabase
import com.assistant.app.llm.FakeLlmProvider
import com.assistant.app.llm.ScriptedEvent
import com.assistant.app.ui.chat.ChatViewModel
import com.assistant.app.ui.chat.QuickAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
class SharedContentTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runViewModelTest(block: suspend (ChatViewModel) -> Unit) = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val chatLlm = MutableStateFlow<ChatLlmState>(
            ChatLlmState.Ready(FakeLlmProvider(listOf(ScriptedEvent.Emit("ok"))), "test-model"),
        )
        val repository = ChatRepository(chatLlm = chatLlm, generationDispatcher = dispatcher)
        block(ChatViewModel(repository, chatLlm))
    }

    @Test
    fun `shared text is staged in the composer without sending`() = runViewModelTest { viewModel ->
        viewModel.stageSharedContent(SharedContent(text = "https://example.com/article"))

        val state = viewModel.uiState.value
        assertEquals("https://example.com/article", state.draft)
        assertTrue("shared content must not be sent on its own", state.messages.isEmpty())
        assertEquals(ChatStatus.Idle, state.status)
    }

    @Test
    fun `shared text is appended to what the user already typed`() = runViewModelTest { viewModel ->
        viewModel.setDraft("Summarize this:")

        viewModel.stageSharedContent(SharedContent(text = "the article body"))

        assertEquals("Summarize this:\n\nthe article body", viewModel.uiState.value.draft)
    }

    @Test
    fun `empty share stages nothing`() = runViewModelTest { viewModel ->
        viewModel.setDraft("keep me")

        viewModel.stageSharedContent(SharedContent())

        assertEquals("keep me", viewModel.uiState.value.draft)
    }

    @Test
    fun `quick action builds a prompt from the current composer`() = runViewModelTest { viewModel ->
        viewModel.setDraft("some text")

        viewModel.applyQuickAction(QuickAction.Summarize)

        val draft = viewModel.uiState.value.draft
        assertTrue(draft.startsWith("Summarize the following"))
        assertTrue(draft.contains("some text"))
        assertTrue("quick action must not send on its own", viewModel.uiState.value.messages.isEmpty())
    }

    @Test
    fun `quick action on an empty composer asks for the text`() = runViewModelTest { viewModel ->
        viewModel.applyQuickAction(QuickAction.Explain)

        val draft = viewModel.uiState.value.draft
        assertTrue(draft.startsWith("Explain the following in simple terms:"))
        assertTrue(viewModel.uiState.value.messages.isEmpty())
    }

    @Test
    fun `reapplying an action keeps the text the user started from`() = runViewModelTest { viewModel ->
        viewModel.setDraft("source material")

        viewModel.applyQuickAction(QuickAction.Summarize)
        viewModel.applyQuickAction(QuickAction.KeyPoints)

        assertTrue(viewModel.uiState.value.draft.contains("source material"))
    }

    @Test
    fun `template reuse fills the text placeholder from the composer`() = runViewModelTest { viewModel ->
        viewModel.setDraft("meeting notes")

        viewModel.applyTemplate(PromptTemplate(title = "Actions", body = "List action items from {{text}}"))

        assertEquals("List action items from meeting notes", viewModel.uiState.value.draft)
    }

    @Test
    fun `template without a usable placeholder is staged verbatim`() =
        runViewModelTest { viewModel ->
            viewModel.applyTemplate(PromptTemplate(title = "Rewrite", body = "Rewrite this as a bulleted list."))

            assertEquals("Rewrite this as a bulleted list.", viewModel.uiState.value.draft)
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PromptTemplateChatTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val database: ChatDatabase = Room
        .inMemoryDatabaseBuilder(context, ChatDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val store = PromptTemplateStore(database)

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `saved template is offered to the composer and reused`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val chatLlm = MutableStateFlow<ChatLlmState>(
            ChatLlmState.Ready(FakeLlmProvider(emptyList()), "test-model"),
        )
        val repository = ChatRepository(chatLlm = chatLlm, generationDispatcher = dispatcher)
        val viewModel = ChatViewModel(
            repository = repository,
            chatLlm = chatLlm,
            promptTemplates = store.templates(),
            templateStore = store,
        )

        viewModel.saveTemplate(null, "Meeting notes", "Summarize {{text}}")
        val templates = viewModel.templates.first { it.isNotEmpty() }

        viewModel.setDraft("notes from today")
        viewModel.applyTemplate(templates.single())

        assertEquals("Summarize notes from today", viewModel.uiState.value.draft)
    }

    @Test
    fun `deleting a template leaves already sent messages untouched`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val provider = FakeLlmProvider(listOf(ScriptedEvent.Emit("answer")))
        val chatLlm = MutableStateFlow<ChatLlmState>(ChatLlmState.Ready(provider, "test-model"))
        val repository = ChatRepository(chatLlm = chatLlm, generationDispatcher = dispatcher)
        val viewModel = ChatViewModel(
            repository = repository,
            chatLlm = chatLlm,
            promptTemplates = store.templates(),
            templateStore = store,
        )

        val id = store.save(null, "Reusable", "Reuse {{text}}")
        viewModel.send("a message that must survive")
        testScheduler.advanceUntilIdle()

        viewModel.deleteTemplate(id!!)
        assertTrue(viewModel.templates.first { it.isEmpty() }.isEmpty())

        val state = viewModel.uiState.value
        assertEquals(2, state.messages.size)
        assertEquals("a message that must survive", state.messages.first().content)
        assertEquals("answer", state.messages.last().content)
    }

    @Test
    fun `switching conversation discards staged shared content`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val chatLlm = MutableStateFlow<ChatLlmState>(
            ChatLlmState.Ready(FakeLlmProvider(emptyList()), "test-model"),
        )
        val repository = ChatRepository(chatLlm = chatLlm, generationDispatcher = dispatcher)
        val viewModel = ChatViewModel(repository, chatLlm)

        viewModel.stageSharedContent(SharedContent(text = "shared draft"))
        assertEquals("shared draft", viewModel.uiState.value.draft)

        viewModel.newConversation()

        assertEquals("", viewModel.uiState.value.draft)
    }

    @Test
    fun `staging a share never contacts the provider`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val provider = FakeLlmProvider(emptyList())
        val chatLlm = MutableStateFlow<ChatLlmState>(ChatLlmState.Ready(provider, "test-model"))
        val repository = ChatRepository(chatLlm = chatLlm, generationDispatcher = dispatcher)
        val viewModel = ChatViewModel(repository, chatLlm)

        viewModel.stageSharedContent(SharedContent(text = "hello"))
        viewModel.applyQuickAction(QuickAction.Summarize)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.messages.isEmpty())
    }
}