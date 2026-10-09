package com.assistant.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import com.assistant.app.R
import com.assistant.app.ui.chat.ChatScreen
import com.assistant.app.llm.ScriptedEvent
import com.assistant.app.ui.components.ChatModelOption
import com.assistant.app.ui.components.ComposerInputTag
import com.assistant.app.ui.theme.ChatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatScreenShellTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun chatShellShowsTopBarActionsAndEmptyState() {
        composeRule.setContent {
            ChatTheme {
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                com.assistant.app.ui.components.AppDrawer(
                    drawerState = drawerState,
                    activeModel = null,
                    isNewChat = true,
                    onNewChat = {},
                    onHistory = {},
                    onSettings = {},
                ) {
                    ChatScreen(
                        onOpenSettings = {},
                        viewModelFactory = ScriptedChatFixture(emptyList()).factory,
                        onOpenDrawer = {  },
                    )
                }
            }
        }

        val appName = composeRule.activity.getString(R.string.app_name)
        assertTrue(
            "The app name should be visible in the chat shell",
            composeRule.onAllNodesWithText(appName).fetchSemanticsNodes().isNotEmpty(),
        )
        composeRule.onNodeWithText("test-model").assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.chat_empty_statement)).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.cd_open_drawer),
        ).assertIsDisplayed()
    }

    @Test
    fun chatShellHasNoNewChatActionWhileEmpty() {
        composeRule.setContent {
            ChatTheme {
                ChatScreen(
                    onOpenSettings = {},
                    viewModelFactory = ScriptedChatFixture(emptyList()).factory,
                )
            }
        }

        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.cd_new_chat)).assertDoesNotExist()
    }

    @Test
    fun emptyHomeShowsGreetingAndExamplePrompts() {
        composeRule.setContent {
            ChatTheme {
                ChatScreen(
                    onOpenSettings = {},
                    viewModelFactory = ScriptedChatFixture(emptyList()).factory,
                )
            }
        }

        val prompt = composeRule.activity.getString(R.string.chat_prompt_1)
        composeRule.onNodeWithText(prompt).assertIsDisplayed().performClick()
    }

    @Test
    fun speakerToggleStaysVisibleAfterMuting() {
        composeRule.setContent {
            ChatTheme {
                ChatScreen(
                    onOpenSettings = {},
                    viewModelFactory = ScriptedChatFixture(
                        emptyList(),
                        ttsAvailable = true,
                        voiceOutputEnabled = true,
                    ).factory,
                )
            }
        }

        val speaker = composeRule.activity.getString(R.string.cd_toggle_speaker)
        composeRule.onNodeWithContentDescription(speaker).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(speaker).performClick()
        composeRule.onNodeWithContentDescription(speaker).assertIsDisplayed()
    }

    @Test
    fun speakerToggleIsAbsentWithoutTextToSpeech() {
        composeRule.setContent {
            ChatTheme {
                ChatScreen(
                    onOpenSettings = {},
                    viewModelFactory = ScriptedChatFixture(emptyList()).factory,
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.cd_toggle_speaker),
        ).assertDoesNotExist()
    }

    @Test
    fun drawerSwitcherListsEverySavedModelAndSelectsOne() {
        var selectedId: Long? = null
        val models = listOf(
            ChatModelOption(id = 1L, model = "gpt-4o", providerName = "OpenAI"),
            ChatModelOption(id = 2L, model = "llama3", providerName = "Local"),
        )
        composeRule.setContent {
            ChatTheme {
                val drawerState = rememberDrawerState(DrawerValue.Open)
                com.assistant.app.ui.components.AppDrawer(
                    drawerState = drawerState,
                    activeModel = "gpt-4o",
                    isNewChat = true,
                    onNewChat = {},
                    onHistory = {},
                    onSettings = {},
                    savedModels = models,
                    activeModelId = 1L,
                    onModelSelected = { selectedId = it },
                ) {
                    Box(modifier = Modifier.size(100.dp))
                }
            }
        }

        composeRule.onNodeWithText("gpt-4o").performClick()
        composeRule.onNodeWithText("llama3").assertIsDisplayed()
        composeRule.onNodeWithText("Local").assertIsDisplayed()

        composeRule.onNodeWithText("llama3").performClick()
        assertEquals(2L, selectedId)
    }

    @Test
    fun drawerSwitcherHandlesALongModelIdWithoutOverlapping() {
        val models = listOf(
            ChatModelOption(id = 1L, model = "gpt-4o", providerName = "OpenAI"),
            ChatModelOption(id = 2L, model = "a-fairly-long-self-hosted-model-identifier", providerName = "Local"),
        )
        composeRule.setContent {
            ChatTheme {
                val drawerState = rememberDrawerState(DrawerValue.Open)
                com.assistant.app.ui.components.AppDrawer(
                    drawerState = drawerState,
                    activeModel = "gpt-4o",
                    isNewChat = true,
                    onNewChat = {},
                    onHistory = {},
                    onSettings = {},
                    savedModels = models,
                    activeModelId = 1L,
                    onModelSelected = {},
                ) {
                    Box(modifier = Modifier.size(100.dp))
                }
            }
        }

        composeRule.onNodeWithText("gpt-4o").performClick()

        composeRule.onNodeWithText("Local").assertIsDisplayed()
        composeRule.onNodeWithText("a-fairly-long-self-hosted-model-identifier", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun followUpChipsDoNotCoverTheConversationOrComposer() {
        composeRule.setContent {
            ChatTheme {
                ChatScreen(
                    onOpenSettings = {},
                    viewModelFactory = ScriptedChatFixture(
                        listOf(ScriptedEvent.Emit(LONG_ANSWER)),
                        followUps = listOf("Ask about the setup?"),
                    ).factory,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ComposerInputTag).performTextInput("Hi")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil {
            composeRule.onAllNodesWithText(LONG_ANSWER)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(LONG_ANSWER).assertIsDisplayed()
        composeRule.onNodeWithTag(ComposerInputTag).assertIsDisplayed()

        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Ask about the setup?").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Ask about the setup?").assertIsDisplayed().performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ComposerInputTag).assertTextEquals("Ask about the setup?")
    }

    @Test
    fun sendingAMessagePositionsItBelowTheTopBarWithTheAnswerStreamingBelow() {
        composeRule.setContent {
            ChatTheme {
                ChatScreen(
                    onOpenSettings = {},
                    viewModelFactory = ScriptedChatFixture(
                        listOf(ScriptedEvent.Emit(PARAGRAPH_ANSWER)),
                    ).factory,
                )
            }
        }

        composeRule.onNodeWithTag(ComposerInputTag).performTextInput("Hi")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Paragraph 60 of", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil {
            composeRule.onAllNodesWithContentDescription(
                composeRule.activity.getString(R.string.menu_copy),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        val userBubble = composeRule.onNodeWithText("Hi").getUnclippedBoundsInRoot()
        assertTrue(
            "the sent message should sit just below the top bar, not at the screen center",
            userBubble.top.value in 64f..100f,
        )
        val answerHead = composeRule
            .onNodeWithText("Paragraph 1 of", substring = true)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "the answer should stream below the message",
            answerHead.top.value > userBubble.bottom.value,
        )
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.cd_scroll_to_latest),
        ).assertIsDisplayed()
    }

    @Test
    fun streamingTokensDoNotMoveTheViewport() {
        val streamedAnswer = (1..60).joinToString("\n\n") {
            "Streamed paragraph $it of a reply long enough to overflow the chat area."
        }
        composeRule.setContent {
            ChatTheme {
                ChatScreen(
                    onOpenSettings = {},
                    viewModelFactory = ScriptedChatFixture(
                        streamedAnswer.chunked(180).map { ScriptedEvent.Emit(it) },
                    ).factory,
                )
            }
        }

        composeRule.onNodeWithTag(ComposerInputTag).performTextInput("Hi")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Hi").fetchSemanticsNodes().isNotEmpty()
        }
        val bubbleBefore = composeRule.onNodeWithText("Hi").getUnclippedBoundsInRoot()
        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Streamed paragraph 60 of", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        val bubbleAfter = composeRule.onNodeWithText("Hi").getUnclippedBoundsInRoot()
        assertEquals(
            "streaming must not move the just-sent message",
            bubbleBefore.top.value,
            bubbleAfter.top.value,
            1f,
        )
        assertTrue(
            "the just-sent message should sit just below the top bar",
            bubbleAfter.top.value in 64f..100f,
        )
    }

    @Test
    fun userScrollDuringStreamingIsRespected() {
        val part1 = (1..30).joinToString("\n\n") { "Streamed paragraph $it of a paced reply." }
        val part2 = (31..60).joinToString("\n\n") { "Streamed paragraph $it of a paced reply." }
        composeRule.setContent {
            ChatTheme {
                ChatScreen(
                    onOpenSettings = {},
                    viewModelFactory = ScriptedChatFixture(
                        listOf(
                            ScriptedEvent.Emit(part1),
                            ScriptedEvent.Delay(4_000),
                            ScriptedEvent.Emit(part2),
                        ),
                    ).factory,
                )
            }
        }

        composeRule.onNodeWithTag(ComposerInputTag).performTextInput("Hi")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Streamed paragraph 30 of", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onRoot().performTouchInput {
            swipe(start = Offset(center.x, center.y + 200f), end = Offset(center.x, center.y - 200f))
        }
        composeRule.waitForIdle()
        val anchor = composeRule
            .onNodeWithText("Streamed paragraph 20 of", substring = true)
            .getUnclippedBoundsInRoot()

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Streamed paragraph 60 of", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        val afterStreaming = composeRule
            .onNodeWithText("Streamed paragraph 20 of", substring = true)
            .getUnclippedBoundsInRoot()
        assertEquals(
            "tokens arriving after the reader scrolled must not move the viewport",
            anchor.top.value,
            afterStreaming.top.value,
            1f,
        )
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.cd_scroll_to_latest),
        ).assertIsDisplayed()
    }

    @Test
    fun downArrowJumpsToTheLatestOnce() {
        composeRule.setContent {
            ChatTheme {
                ChatScreen(
                    onOpenSettings = {},
                    viewModelFactory = ScriptedChatFixture(
                        listOf(ScriptedEvent.Emit(PARAGRAPH_ANSWER)),
                    ).factory,
                )
            }
        }

        composeRule.onNodeWithTag(ComposerInputTag).performTextInput("Hi")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Paragraph 60 of", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil {
            composeRule.onAllNodesWithContentDescription(
                composeRule.activity.getString(R.string.menu_copy),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        val scrollToLatest = composeRule.activity.getString(R.string.cd_scroll_to_latest)
        composeRule.onNodeWithContentDescription(scrollToLatest).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(scrollToLatest).performClick()
        composeRule.waitUntil {
            composeRule.onAllNodesWithContentDescription(scrollToLatest)
                .fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText("Paragraph 60 of", substring = true).assertIsDisplayed()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(scrollToLatest).assertDoesNotExist()
    }

    @Test
    fun streamingAfterTheDownArrowDoesNotMoveTheViewport() {
        val part1 = (1..30).joinToString("\n\n") { "Streamed paragraph $it of a paced reply." }
        val part2 = (31..60).joinToString("\n\n") { "Streamed paragraph $it of a paced reply." }
        composeRule.setContent {
            ChatTheme {
                ChatScreen(
                    onOpenSettings = {},
                    viewModelFactory = ScriptedChatFixture(
                        listOf(
                            ScriptedEvent.Emit(part1),
                            ScriptedEvent.Delay(4_000),
                            ScriptedEvent.Emit(part2),
                        ),
                    ).factory,
                )
            }
        }

        composeRule.onNodeWithTag(ComposerInputTag).performTextInput("Hi")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Streamed paragraph 30 of", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val scrollToLatest = composeRule.activity.getString(R.string.cd_scroll_to_latest)
        composeRule.onNodeWithContentDescription(scrollToLatest).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(scrollToLatest).performClick()
        composeRule.waitUntil {
            composeRule.onAllNodesWithContentDescription(scrollToLatest)
                .fetchSemanticsNodes().isEmpty()
        }
        val tail = composeRule
            .onNodeWithText("Streamed paragraph 30 of", substring = true)
            .getUnclippedBoundsInRoot()

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Streamed paragraph 60 of", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        val tailAfterStreaming = composeRule
            .onNodeWithText("Streamed paragraph 30 of", substring = true)
            .getUnclippedBoundsInRoot()
        assertEquals(
            "tokens arriving after the one-shot jump must not move the viewport",
            tail.top.value,
            tailAfterStreaming.top.value,
            1f,
        )
        composeRule.onNodeWithContentDescription(scrollToLatest).assertIsDisplayed()
    }

    @Test
    fun nextMessageInALongConversationIsPositionedBelowTheTopBar() {
        val secondAnswer = (1..60).joinToString("\n\n") {
            "Second answer line $it, streamed below the new message."
        }
        val fixture = ScriptedChatFixture(listOf(ScriptedEvent.Emit(PARAGRAPH_ANSWER)))
        composeRule.setContent {
            ChatTheme {
                ChatScreen(
                    onOpenSettings = {},
                    viewModelFactory = fixture.factory,
                )
            }
        }

        composeRule.onNodeWithTag(ComposerInputTag).performTextInput("Hi")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Paragraph 60 of", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil {
            composeRule.onAllNodesWithContentDescription(
                composeRule.activity.getString(R.string.menu_copy),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        fixture.provider.script = listOf(ScriptedEvent.Emit(secondAnswer))
        composeRule.onNodeWithTag(ComposerInputTag).performTextInput("Again")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Again").fetchSemanticsNodes().isNotEmpty()
        }
        val bubble = composeRule.onNodeWithText("Again").getUnclippedBoundsInRoot()
        assertTrue(
            "the new message should be positioned just below the top bar",
            bubble.top.value in 64f..100f,
        )

        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Second answer line 60,", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        val bubbleAfterStreaming = composeRule.onNodeWithText("Again").getUnclippedBoundsInRoot()
        assertTrue(
            "the new message must stay pinned near the top while the answer streams below it",
            bubbleAfterStreaming.top.value in 64f..100f,
        )
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.cd_scroll_to_latest),
        ).assertIsDisplayed()
    }

    @Test
    fun anAnswerThatFitsKeepsItsTailAndHidesTheScrollAffordance() {
        composeRule.setContent {
            ChatTheme {
                ChatScreen(
                    onOpenSettings = {},
                    viewModelFactory = ScriptedChatFixture(
                        listOf(ScriptedEvent.Emit(LONG_ANSWER)),
                    ).factory,
                )
            }
        }

        composeRule.onNodeWithTag(ComposerInputTag).performTextInput("Hi")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil {
            composeRule.onAllNodesWithText(LONG_ANSWER).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.menu_copy),
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.cd_scroll_to_latest),
        ).assertDoesNotExist()
    }

    private companion object {

        val PARAGRAPH_ANSWER: String =
            (1..60).joinToString("\n\n") {
                "Paragraph $it of a reply long enough to overflow the chat area."
            }

        const val LONG_ANSWER =
            "A considerably longer answer so the follow-up generator is willing to " +
                "spend a second request on it, padded out well past the minimum " +
                "length that the generator uses before it decides to bother."
    }
}
