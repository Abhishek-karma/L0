package com.assistant.app.data

class AnswerVersions {

    private val cache = HashMap<String, MutableList<String>>()

    fun versionsOf(messageId: String): List<String>? = cache[messageId]?.toList()

    fun append(messageId: String, content: String): Boolean {
        val versions = cache.getOrPut(messageId) { mutableListOf() }
        if (versions.lastOrNull() == content) return false
        versions.add(content)
        return true
    }

    fun remove(messageId: String) {
        cache.remove(messageId)
    }

    fun clear() = cache.clear()

    fun loadAll(versionsByMessage: Map<String, List<String>>) {
        versionsByMessage.forEach { (messageId, contents) ->
            cache[messageId] = contents.toMutableList()
        }
    }
}
