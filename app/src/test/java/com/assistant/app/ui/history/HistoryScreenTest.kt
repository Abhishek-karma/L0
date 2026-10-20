package com.assistant.app.ui.history

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.assistant.app.R
import com.assistant.app.data.ConversationSearchHit
import com.assistant.app.ui.theme.ChatTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun historyScreenDisplaysEmptyStateWhenListIsEmpty() {
        var backPressed = false
        composeRule.setContent {
            ChatTheme {
                HistoryScreen(
                    conversations = emptyList(),
                    onOpen = {},
                    onDelete = {},
                    onTogglePin = { _, _ -> },
                    onRename = { _, _ -> },
                    shareText = { null },
                    onBack = { backPressed = true },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.history_empty)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_empty_hint)).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_back)).performClick()
        assertTrue("Back callback should be triggered", backPressed)
    }

    @Test
    fun historyScreenRendersConversationsAndHandlesOpen() {
        val now = System.currentTimeMillis()
        val list = listOf(
            ConversationSummary(id = "c1", title = "Pinned Chat", updatedAt = now, pinned = true),
            ConversationSummary(id = "c2", title = "Recent Chat", updatedAt = now, pinned = false),
        )
        var openedId: String? = null

        composeRule.setContent {
            ChatTheme {
                HistoryScreen(
                    conversations = list,
                    onOpen = { openedId = it },
                    onDelete = {},
                    onTogglePin = { _, _ -> },
                    onRename = { _, _ -> },
                    shareText = { null },
                )
            }
        }

        composeRule.onNodeWithText("Pinned Chat").assertIsDisplayed()
        composeRule.onNodeWithText("Recent Chat").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_pinned), substring = true).assertIsDisplayed()

        composeRule.onNodeWithText("Recent Chat").performClick()
        assertEquals("c2", openedId)
    }

    @Test
    fun searchScreenFiltersConversationsByQuery() {
        val list = listOf(
            ConversationSummary(id = "c1", title = "Kotlin Coroutines", updatedAt = 1000L),
            ConversationSummary(id = "c2", title = "Compose Animation", updatedAt = 2000L),
        )
        var openedId: String? = null

        composeRule.setContent {
            ChatTheme {
                SearchScreen(
                    conversations = list,
                    onSearch = { query ->
                        list.filter { it.title.contains(query, ignoreCase = true) }
                            .map { ConversationSearchHit(it.id, it.title, it.updatedAt, null) }
                    },
                    onOpen = { openedId = it },
                )
            }
        }

        composeRule.onNodeWithText("Kotlin Coroutines").assertIsDisplayed()
        composeRule.onNodeWithText("Compose Animation").assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.search_hint))
            .performTextInput("Coroutines")

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Kotlin Coroutines"))
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodes(hasText("Compose Animation"))
                    .fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("Kotlin Coroutines").assertIsDisplayed()

        composeRule.onNodeWithText("Kotlin Coroutines").performClick()
        assertEquals("c1", openedId)
    }

    @Test
    fun searchScreenShowsTheMatchingMessageSnippet() {
        val list = listOf(ConversationSummary(id = "c1", title = "Release notes", updatedAt = 1000L))
        var searchedQuery: String? = null

        composeRule.setContent {
            ChatTheme {
                SearchScreen(
                    conversations = list,
                    onSearch = { query ->
                        searchedQuery = query
                        listOf(
                            ConversationSearchHit(
                                id = "c1",
                                title = "Release notes",
                                updatedAt = 1000L,
                                snippet = "The rollback plan is ready",
                            ),
                        )
                    },
                    onOpen = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.search_hint))
            .performTextInput("rollback")

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("The rollback plan is ready"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("rollback", searchedQuery)
    }

    @Test
    fun searchScreenReportsAFailedSearch() {
        composeRule.setContent {
            ChatTheme {
                SearchScreen(
                    conversations = listOf(
                        ConversationSummary(id = "c1", title = "Release notes", updatedAt = 1000L),
                    ),
                    onSearch = { throw IllegalStateException("search unavailable") },
                    onOpen = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.search_hint))
            .performTextInput("rollback")

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(
                hasText(context.getString(R.string.search_failed)),
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun aSuccessfulSearchReplacesAPreviousFailure() {
        val list = listOf(ConversationSummary(id = "c1", title = "Release notes", updatedAt = 1000L))
        var attempt = 0

        composeRule.setContent {
            ChatTheme {
                SearchScreen(
                    conversations = list,
                    onSearch = { query ->
                        attempt++
                        if (attempt == 1) throw IllegalStateException("search unavailable")
                        listOf(ConversationSearchHit("c1", "Release notes", 1000L, null))
                    },
                    onOpen = {},
                )
            }
        }

        val field = composeRule.onNode(hasSetTextAction())
        field.performTextInput("roll")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(
                hasText(context.getString(R.string.search_failed)),
            ).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("back")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Release notes"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            composeRule.onAllNodes(
                hasText(context.getString(R.string.search_failed)),
            ).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun searchScreenShowsASearchingStateWhileTheQueryRuns() {
        composeRule.setContent {
            ChatTheme {
                SearchScreen(
                    conversations = emptyList(),
                    onSearch = {
                        awaitCancellation()
                        emptyList()
                    },
                    onOpen = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.search_hint))
            .performTextInput("rollback")

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(context.getString(R.string.search_searching)))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun searchScreenReportsWhenNothingMatches() {
        composeRule.setContent {
            ChatTheme {
                SearchScreen(
                    conversations = emptyList(),
                    onSearch = { emptyList() },
                    onOpen = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.search_hint))
            .performTextInput("rollback")

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(context.getString(R.string.search_empty)))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun clearingTheQueryBringsBackTheRecentConversations() {
        val list = listOf(ConversationSummary(id = "c1", title = "Release notes", updatedAt = 1000L))

        composeRule.setContent {
            ChatTheme {
                SearchScreen(
                    conversations = list,
                    onSearch = { emptyList() },
                    onOpen = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.search_hint))
            .performTextInput("zzz")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(context.getString(R.string.search_empty)))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNode(hasSetTextAction()).performTextReplacement("")

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Release notes")).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            composeRule.onAllNodes(hasText(context.getString(R.string.search_empty)))
                .fetchSemanticsNodes().isEmpty(),
        )
    }
}
