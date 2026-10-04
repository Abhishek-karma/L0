package com.assistant.app.llm.model

import org.json.JSONArray
import org.json.JSONObject

data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val engine: String? = null,
)

data class ExtractedContent(
    val title: String,
    val text: String,
)

sealed interface SearchOutcome {
    data class Success(val results: List<SearchResult>) : SearchOutcome

    data class Failure(val error: SearchError, val detail: String? = null) : SearchOutcome
}

enum class SearchError(val userMessage: String) {
    RateLimited("Search rate limit reached. Try again shortly."),
    NetworkUnavailable("Unable to connect to search service."),
    Timeout("Search request timed out."),
    InvalidResponse("Search service returned an invalid response."),
    NoResults("No relevant web results found."),
    Unknown("Something went wrong with search."),
}

fun List<SearchResult>.toSearchJson(): String = JSONArray().apply {
    forEach { result ->
        put(
            JSONObject()
                .put("title", result.title)
                .put("url", result.url)
                .put("snippet", result.snippet),
        )
    }
}.toString()

fun searchResultsFromJson(raw: String?): List<SearchResult> =
    raw?.let { json ->
        runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val result = SearchResult(
                    title = obj.optString("title"),
                    url = obj.optString("url"),
                    snippet = obj.optString("snippet"),
                )
                if (result.url.isBlank()) null else result
            }
        }.getOrDefault(emptyList())
    }.orEmpty()
