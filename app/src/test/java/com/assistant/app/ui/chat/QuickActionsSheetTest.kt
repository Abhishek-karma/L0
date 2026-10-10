package com.assistant.app.ui.chat

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.assistant.app.R
import com.assistant.app.data.SharedContent
import com.assistant.app.llm.ScriptedEvent
import com.assistant.app.ui.ScriptedChatFixture
import com.assistant.app.ui.components.ComposerInputTag
import com.assistant.app.ui.theme.ChatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val WAIT_MS = 5_000L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickActionsSheetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(resId: Int): String = composeRule.activity.getString(resId)

    private fun setContent(share: SharedContent? = null, onShareConsumed: () -> Unit = {}) {
        val fixture = ScriptedChatFixture(listOf(ScriptedEvent.Emit("reply")))
        composeRule.setContent {
            ChatTheme {
                ChatScreen(
                    onOpenSettings = {},
                    viewModelFactory = fixture.factory,
                    pendingShare = share,
                    onShareConsumed = onShareConsumed,
                )
            }
        }
    }

    @Test
    fun quickActionsSheetListsEveryAction() {
        setContent()

        composeRule.onNodeWithContentDescription(string(R.string.cd_quick_actions)).performClick()

        QuickAction.entries.forEach { action ->
            composeRule.onAllNodesWithText(action.label).onFirst().assertExists()
        }
    }

    @Test
    fun choosingAnActionFillsTheComposerWithoutSending() {
        setContent()
        composeRule.onNodeWithTag(ComposerInputTag).performTextInput("shared article")
        composeRule.onNodeWithContentDescription(string(R.string.cd_quick_actions)).performClick()

        composeRule.onNodeWithText(QuickAction.Summarize.label).performClick()

        composeRule.onNodeWithText("Summarize the following clearly and briefly:", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("shared article", substring = true).assertIsDisplayed()
    }

    @Test
    fun sharedTextAppearsInTheComposerAndIsNotSent() {
        var consumed = false
        setContent(share = SharedContent(text = "https://example.com/article")) { consumed = true }

        composeRule.waitUntil(WAIT_MS) { consumed }

        composeRule.onNodeWithText("https://example.com/article").assertIsDisplayed()
        composeRule.onNodeWithText("reply").assertDoesNotExist()
    }

    @Test
    fun sharedTextIsStagedInTheComposer() {
        setContent(share = SharedContent(text = "the article body"))

        composeRule.waitUntil(WAIT_MS) {
            composeRule.onAllNodesWithText("the article body", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithText("the article body", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("reply").assertDoesNotExist()
    }
}