package com.assistant.app.llm

data class SseEvent(
    val event: String?,
    val data: String,
)

class SseParser {

    private val buffer = StringBuilder()
    private var eventName: String? = null
    private val dataLines = mutableListOf<String>()

    fun parseSse(chunk: String): List<SseEvent> {
        if (buffer.isEmpty()) {

            buffer.append(chunk.removePrefix(BOM))
        } else {
            buffer.append(chunk)
        }
        val events = mutableListOf<SseEvent>()
        while (true) {
            val line = nextLine() ?: break
            handleLine(line, events)
        }
        return events
    }

    fun flush(): List<SseEvent> {
        val events = mutableListOf<SseEvent>()
        val tail = buffer.toString().removeSuffix("\r")
        buffer.setLength(0)
        if (tail.isNotEmpty()) handleLine(tail, events)
        dispatch(events)
        return events
    }

    private fun handleLine(line: String, events: MutableList<SseEvent>) {
        when {
            line.isEmpty() -> dispatch(events)
            line.startsWith(":") -> Unit
            else -> {
                val colon = line.indexOf(':')
                val field = if (colon < 0) line else line.substring(0, colon)
                val value = if (colon < 0) "" else line.substring(colon + 1).removePrefix(" ")
                when (field) {
                    "event" -> eventName = value
                    "data" -> dataLines += value
                    else -> Unit
                }
            }
        }
    }

    private fun dispatch(events: MutableList<SseEvent>) {
        val data = dataLines.joinToString(separator = "\n")
        val name = eventName
        dataLines.clear()
        eventName = null
        if (data.isNotEmpty()) events += SseEvent(name, data)
    }

    private fun nextLine(): String? {
        for (index in 0 until buffer.length) {
            when (buffer[index]) {
                '\n' -> return cutLine(index, 1)
                '\r' -> {

                    if (index == buffer.length - 1) return null
                    return cutLine(index, if (buffer[index + 1] == '\n') 2 else 1)
                }
            }
        }
        return null
    }

    private fun cutLine(lineEnd: Int, terminatorWidth: Int): String {
        val line = buffer.substring(0, lineEnd)
        buffer.delete(0, lineEnd + terminatorWidth)
        return line
    }

    private companion object {
        const val BOM = "\uFEFF"
    }
}
