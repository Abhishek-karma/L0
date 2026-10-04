package com.assistant.app.data.versions

import com.assistant.app.data.AnswerVersions
import com.assistant.app.data.ConversationStore
import com.assistant.app.llm.model.Role
import com.assistant.app.llm.model.UiMessage

class AnswerVersionStore(
    private val store: ConversationStore? = null,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val versionCache = AnswerVersions()

    fun versionsOf(messageId: String): List<String>? = versionCache.versionsOf(messageId)

    fun clear() = versionCache.clear()

    fun remove(messageId: String) = versionCache.remove(messageId)

    fun loadAll(versionsByMessage: Map<String, List<String>>) = versionCache.loadAll(versionsByMessage)

    suspend fun snapshotVersion(
        messageId: String,
        messages: List<UiMessage>,
        updateMessages: ((List<UiMessage>) -> List<UiMessage>) -> Unit
    ) {
        val content = messages
            .firstOrNull { it.id == messageId }
            ?.takeIf { it.role == Role.ASSISTANT }
            ?.content
            ?: return
        if (content.isBlank()) return

        val appended = versionCache.append(messageId, content)
        if (appended) {
            store?.saveVersion(messageId, content)
        }
        val versions = versionCache.versionsOf(messageId).orEmpty()
        val selected = versions.lastIndex
        updateMessages { currentMessages ->
            currentMessages.map { m ->
                if (m.id == messageId) {
                    m.copy(content = content, versions = versions.toList(), selectedVersion = selected)
                } else {
                    m
                }
            }
        }
        store?.updateSelectedVersion(messageId, selected)
    }

    suspend fun switchVersion(
        messageId: String,
        index: Int,
        updateMessages: ((List<UiMessage>) -> List<UiMessage>) -> Unit
    ): Boolean {
        val versions = versionCache.versionsOf(messageId) ?: return false
        if (index !in versions.indices) return false

        updateMessages { currentMessages ->
            currentMessages.map { m ->
                if (m.id == messageId && m.role == Role.ASSISTANT) {
                    m.copy(content = versions[index], selectedVersion = index)
                } else {
                    m
                }
            }
        }
        store?.updateMessageContent(messageId, versions[index], null, clock())
        store?.updateSelectedVersion(messageId, index)
        return true
    }
}
