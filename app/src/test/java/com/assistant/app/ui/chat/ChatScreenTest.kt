package com.assistant.app.ui.chat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.assistant.app.R
import com.assistant.app.data.ChatLlmState
import com.assistant.app.llm.ScriptedEvent
import com.assistant.app.llm.model.ProviderError
import com.assistant.app.llm.model.SearchOutcome
import com.assistant.app.llm.model.SearchResult
import com.assistant.app.ui.ScriptedChatFixture
import com.assistant.app.ui.components.ComposerInputTag
import com.assistant.app.ui.theme.ChatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.CompletableDeferred

private const val WAIT_MS = 5_000L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(
        fixture: ScriptedChatFixture,
        onOpenSettings: () -> Unit = {},
    ) {
        composeRule.setContent {
            ChatTheme {
                ChatScreen(
                    onOpenSettings = onOpenSettings,
                    viewModelFactory = fixture.factory,
                )
            }
        }
    }

    private fun string(resId: Int): String = composeRule.activity.getString(resId)

    private fun typeAndSend(text: String) {
        composeRule.onNodeWithTag(ComposerInputTag).performTextInput(text)
        composeRule.onNodeWithContentDescription(string(R.string.cd_send)).performClick()
    }

    @Test
    fun editAndResendTruncatesHistoryAndStartsNewReply() {
        val fixture = ScriptedChatFixture(listOf(ScriptedEvent.Emit("First reply")))
        setContent(fixture)
        typeAndSend("First")
        waitUntilText("First reply")

        composeRule.onNodeWithText("First").performTouchInput { longClick() }
        composeRule.onNodeWithText(string(R.string.menu_edit_and_resend)).performClick()

        composeRule.onNodeWithText(string(R.string.edit_banner_label)).assertIsDisplayed()
        composeRule.onNodeWithTag(ComposerInputTag).performTextReplacement("Edited")

        fixture.provider.script = listOf(ScriptedEvent.Emit("Edited reply"))
        composeRule.onNodeWithContentDescription(string(R.string.cd_send)).performClick()

        waitUntilText("Edited reply")
        composeRule.onNodeWithText("Edited").assertIsDisplayed()
        composeRule.onNodeWithText("Edited reply").assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.edit_banner_label)).assertDoesNotExist()

        assertTrue(composeRule.onAllNodesWithText("First reply").fetchSemanticsNodes().isEmpty())
    }

    private fun waitUntilText(text: String, substring: Boolean = false) {
        composeRule.waitUntil(timeoutMillis = WAIT_MS) {
            composeRule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitUntilContentDescription(description: String) {
        composeRule.waitUntil(timeoutMillis = WAIT_MS) {
            composeRule.onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun sendShowsUserMessageAndStreamedAssistantReply() {
        val fixture = ScriptedChatFixture(
            listOf(ScriptedEvent.Emit("Hel"), ScriptedEvent.Emit("lo")),
        )
        setContent(fixture)

        composeRule.onNodeWithContentDescription(string(R.string.cd_send)).assertIsNotEnabled()

        typeAndSend("Hi")

        waitUntilText("Hello")
        composeRule.onNodeWithText("Hi").assertIsDisplayed()
        composeRule.onNodeWithText("Hello").assertIsDisplayed()
    }

    @Test
    fun stopDuringGenerationKeepsPartialTextAndReenablesSend() {
        val fixture = ScriptedChatFixture(
            listOf(ScriptedEvent.Emit("Hel"), ScriptedEvent.Delay(60_000)),
        )
        setContent(fixture)
        typeAndSend("Hi")

        waitUntilText("Hel", substring = true)
        composeRule.onNodeWithContentDescription(string(R.string.cd_stop)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.cd_send)).assertDoesNotExist()

        composeRule.onNodeWithContentDescription(string(R.string.cd_stop)).performClick()

        waitUntilContentDescription(string(R.string.cd_send))
        composeRule.onNodeWithText("Hel").assertIsDisplayed()

        composeRule.onNodeWithTag(ComposerInputTag).performTextInput(" and more")
        composeRule.onNodeWithContentDescription(string(R.string.cd_send)).assertIsEnabled()
    }

    @Test
    fun errorBannerShowsUserMessageAndRetryRestartsGeneration() {
        val fixture = ScriptedChatFixture(
            listOf(ScriptedEvent.Delay(50), ScriptedEvent.Fail(ProviderError.InvalidCredentials)),
        )
        setContent(fixture)
        typeAndSend("Hi")

        waitUntilText(ProviderError.InvalidCredentials.userMessage)
        composeRule.onNodeWithText(string(R.string.error_retry)).assertIsDisplayed()

        fixture.provider.script = listOf(ScriptedEvent.Emit("Recovered"))
        composeRule.onNodeWithText(string(R.string.error_retry)).performClick()

        waitUntilText("Recovered")
        composeRule.onNodeWithText("Recovered").assertIsDisplayed()
    }

    @Test
    fun searchWaitShowsProgressAndOffersStop() {
        val searchGate = CompletableDeferred<SearchOutcome?>()
        val fixture = ScriptedChatFixture(
            script = listOf(ScriptedEvent.Emit("Reply")),
            webSearch = { searchGate.await() },
        )
        setContent(fixture)

        composeRule.onNodeWithContentDescription(string(R.string.cd_toggle_search)).performClick()
        typeAndSend("Question")

        waitUntilContentDescription(string(R.string.cd_stop))
        composeRule.onNodeWithContentDescription(string(R.string.cd_send)).assertDoesNotExist()
        waitUntilText(string(R.string.status_searching_web))

        searchGate.complete(
            SearchOutcome.Success(listOf(SearchResult("Result", "https://example.com", "snippet"))),
        )
        waitUntilText("Reply")
        composeRule.onNodeWithContentDescription(string(R.string.cd_send)).assertIsDisplayed()
    }

    @Test
    fun streamingDoesNotResubscribeToTheLatestMessage() {
        val fixture = ScriptedChatFixture(emptyList())
        setContent(fixture)
        typeAndSend("Hi")
        waitUntilContentDescription(string(R.string.cd_send))

        fixture.provider.script = listOf(
            ScriptedEvent.Emit(FIRST_CHUNK),
            ScriptedEvent.Delay(50),
            ScriptedEvent.Emit(LATE_TOKEN),
        )
        typeAndSend("Second")

        waitUntilText(LATE_TOKEN, substring = true)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(FIRST_CHUNK, substring = true).assertIsDisplayed()
    }

    @Test
    fun needsSetupShowsCalmSetupStateAndOpensSettings() {
        var settingsOpened = false
        setContent(ScriptedChatFixture(emptyList(), ChatLlmState.NeedsSetup)) {
            settingsOpened = true
        }

        composeRule.onNodeWithText(string(R.string.chat_setup_required)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.chat_open_settings)).assertIsDisplayed()
        composeRule.onNodeWithTag(ComposerInputTag).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.chat_empty_statement)).assertDoesNotExist()

        composeRule.onNodeWithText(string(R.string.chat_open_settings)).performClick()
        assertTrue(settingsOpened)
    }

    private companion object {
        const val FIRST_CHUNK = "First chunk of the streamed reply."
        const val LATE_TOKEN = "late streamed token"
    }
}
