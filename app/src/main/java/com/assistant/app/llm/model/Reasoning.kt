package com.assistant.app.llm.model

sealed interface ReasoningConfig {
    data object Auto : ReasoningConfig
    data object Off : ReasoningConfig
    data class Effort(val level: ReasoningEffort) : ReasoningConfig
    data class Budget(val tokens: Int) : ReasoningConfig
}

enum class ReasoningEffort { LOW, MEDIUM, HIGH }

sealed interface ThinkCapability {
    data object Unsupported : ThinkCapability

    data object Unknown : ThinkCapability

    data class Effort(val levels: List<ReasoningEffort>) : ThinkCapability

    data class Budget(
        val minTokens: Int,
        val maxTokens: Int,
        val allowOff: Boolean,
        val allowAuto: Boolean,
    ) : ThinkCapability
}

fun ThinkCapability.isReasoningSupported(): Boolean =
    this is ThinkCapability.Effort || this is ThinkCapability.Budget

fun ThinkCapability.accepts(config: ReasoningConfig): Boolean = when (this) {
    ThinkCapability.Unsupported, ThinkCapability.Unknown -> false
    is ThinkCapability.Effort -> config is ReasoningConfig.Effort && config.level in levels
    is ThinkCapability.Budget -> when (config) {
        ReasoningConfig.Auto -> allowAuto
        ReasoningConfig.Off -> allowOff
        is ReasoningConfig.Budget -> config.tokens in minTokens..maxTokens
        is ReasoningConfig.Effort -> false
    }
}

fun ThinkCapability.normalize(config: ReasoningConfig): ReasoningConfig {
    if (accepts(config)) return config
    return when (this) {
        ThinkCapability.Unsupported, ThinkCapability.Unknown -> ReasoningConfig.Auto
        is ThinkCapability.Effort -> ReasoningConfig.Auto
        is ThinkCapability.Budget -> when (config) {
            is ReasoningConfig.Budget -> ReasoningConfig.Budget(config.tokens.coerceIn(minTokens, maxTokens))
            ReasoningConfig.Off -> if (allowOff) ReasoningConfig.Off else ReasoningConfig.Auto
            else -> ReasoningConfig.Auto
        }
    }
}

fun ThinkCapability.budgetPresets(): List<Int> = when (this) {
    is ThinkCapability.Budget ->
        (listOf(minTokens, maxTokens) + listOf(1024, 2048, 4096, 8192, 16384, 32768))
            .filter { it in minTokens..maxTokens }
            .distinct()
            .sorted()
    else -> emptyList()
}

private const val GEMINI_BUDGET_MIN = 1
private const val GEMINI_BUDGET_MAX = 32768

private val EFFORT_MARKERS = listOf("thinking", "reasoner", "reasoning")
private val EFFORT_ID_PATTERN = Regex("""(?:^|[^a-z0-9])(?:o[1-9]|r1)(?:$|[^0-9])""")

fun inferThinkCapability(model: String): ThinkCapability {
    val id = model.lowercase()
    return when {
        "gemini" in id -> ThinkCapability.Budget(
            minTokens = GEMINI_BUDGET_MIN,
            maxTokens = GEMINI_BUDGET_MAX,
            allowOff = true,
            allowAuto = true,
        )
        EFFORT_MARKERS.any { it in id } || EFFORT_ID_PATTERN.containsMatchIn(id) ->
            ThinkCapability.Effort(ReasoningEffort.entries)
        else -> ThinkCapability.Unknown
    }
}

fun ReasoningConfig.encode(): String = when (this) {
    ReasoningConfig.Auto -> "auto"
    ReasoningConfig.Off -> "off"
    is ReasoningConfig.Effort -> "effort:${level.name.lowercase()}"
    is ReasoningConfig.Budget -> "budget:$tokens"
}

fun decodeReasoningConfig(raw: String): ReasoningConfig? = when {
    raw == "auto" -> ReasoningConfig.Auto
    raw == "off" -> ReasoningConfig.Off
    raw.startsWith("effort:") -> ReasoningEffort.entries
        .firstOrNull { it.name.lowercase() == raw.removePrefix("effort:") }
        ?.let { ReasoningConfig.Effort(it) }
    raw.startsWith("budget:") -> raw.removePrefix("budget:").toIntOrNull()
        ?.takeIf { it > 0 }
        ?.let { ReasoningConfig.Budget(it) }
    else -> null
}
