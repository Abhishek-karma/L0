package com.assistant.app.ui.history

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.R
import com.assistant.app.data.ConversationSearchHit
import com.assistant.app.ui.components.AppIcons
import com.assistant.app.ui.components.AssistantTopBar
import com.assistant.app.ui.theme.AppShape
import com.assistant.app.ui.theme.AppSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

@androidx.compose.runtime.Immutable
internal data class SearchResultItem(
    val id: String,
    val title: String,
    val timeText: String,
    val snippet: String? = null,
)

private data class SearchOutcome(
    val query: String,
    val results: List<SearchResultItem>,
    val failed: Boolean,
)

private const val RECENT_COUNT = 8
private const val SEARCH_DEBOUNCE_MILLIS = 200L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    conversations: List<ConversationSummary>?,
    onSearch: suspend (String) -> List<ConversationSearchHit>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var outcome by remember { mutableStateOf<SearchOutcome?>(null) }
    val now = remember { System.currentTimeMillis() }
    val trimmedQuery = query.trim()

    val recent = remember(conversations) {
        conversations.orEmpty().take(RECENT_COUNT).map { it.toResultItem(now) }
    }

    LaunchedEffect(trimmedQuery) {
        if (trimmedQuery.isEmpty()) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE_MILLIS)
        val found = try {
            onSearch(trimmedQuery).map { it.toResultItem(now) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        currentCoroutineContext().ensureActive()
        outcome = SearchOutcome(trimmedQuery, found.orEmpty(), found == null)
    }

    val active = outcome?.takeIf { it.query == trimmedQuery }
    val results = when {
        trimmedQuery.isEmpty() -> recent
        active == null -> emptyList()
        else -> active.results
    }
    val searching = trimmedQuery.isNotEmpty() && active == null
    val failed = active?.failed == true

    Scaffold(
        modifier = modifier,
        topBar = {
            AssistantTopBar(title = stringResource(R.string.search_title), onBack = onBack)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                shape = AppShape.pill,
                leadingIcon = {
                    Icon(
                        painter = painterResource(AppIcons.Search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_dismiss),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
            )

            Text(
                text = stringResource(
                    if (query.isBlank()) R.string.search_recent else R.string.search_results,
                ).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
            )

            if (searching) {
                Text(
                    text = stringResource(R.string.search_searching),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.lg),
                )
            } else if (results.isEmpty()) {
                Text(
                    text = stringResource(
                        when {
                            failed -> R.string.search_failed
                            trimmedQuery.isEmpty() -> R.string.history_empty
                            else -> R.string.search_empty
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.lg),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.lg),
                ) {
                    items(results, key = { it.id }) { item ->
                        Surface(
                            shape = AppShape.small,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClickLabel = item.title) {
                                        onOpen(item.id)
                                    }
                                    .padding(horizontal = AppSpacing.md, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(AppIcons.Chat),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = AppSpacing.md),
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    item.snippet?.let { snippet ->
                                        Text(
                                            text = snippet,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                    Text(
                                        text = item.timeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ConversationSummary.toResultItem(now: Long) = SearchResultItem(
    id = id,
    title = title,
    timeText = relativeTime(updatedAt, now),
)

private fun ConversationSearchHit.toResultItem(now: Long) = SearchResultItem(
    id = id,
    title = title,
    timeText = relativeTime(updatedAt, now),
    snippet = snippet,
)

private fun relativeTime(updatedAt: Long, now: Long): String =
    DateUtils.getRelativeTimeSpanString(updatedAt, now, DateUtils.DAY_IN_MILLIS).toString()
