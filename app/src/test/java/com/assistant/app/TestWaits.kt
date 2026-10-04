package com.assistant.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val STORE_WAIT_MS = 10_000L
private const val STORE_POLL_MS = 50L

private class Emitted<T>(val value: T)

suspend fun <T> Flow<T>.firstBounded(
    what: String = "matching value",
    predicate: (T) -> Boolean = { true },
): T = withContext(Dispatchers.IO) {

    val deadline = System.currentTimeMillis() + STORE_WAIT_MS
    var last: T? = null
    while (true) {

        val outcome = withTimeoutOrNull(STORE_WAIT_MS) { Emitted(first()) }
        if (outcome != null) {
            last = outcome.value
            if (predicate(outcome.value)) return@withContext outcome.value
        }
        if (System.currentTimeMillis() >= deadline) {
            throw AssertionError(
                "Timed out after ${STORE_WAIT_MS}ms waiting for $what; last value: $last",
            )
        }
        Thread.sleep(STORE_POLL_MS)
    }
    error("unreachable")
}
