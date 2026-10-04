package com.assistant.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.assistant.app.llm.model.ReasoningConfig
import com.assistant.app.llm.model.decodeReasoningConfig
import com.assistant.app.llm.model.encode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

enum class AppTheme { SYSTEM, LIGHT, DARK }

enum class TextSize(val scale: Float) { SMALL(0.9f), NORMAL(1.0f), LARGE(1.15f) }

class AppPreferences(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    dataStoreFile: File? = null,
) {

    private val appContext = context.applicationContext

    private val dataStore = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(ioDispatcher + SupervisorJob()),
        produceFile = { dataStoreFile ?: File(appContext.filesDir, DATA_STORE_FILE) },
    )

    val voiceOutputEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[KEY_VOICE_OUTPUT] ?: false }

    suspend fun setVoiceOutputEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_VOICE_OUTPUT] = enabled }
    }

    val appearance: Flow<AppTheme> = dataStore.data.map { prefs ->
        prefs[KEY_APPEARANCE]?.let { stored ->
            AppTheme.entries.firstOrNull { it.name == stored }
        } ?: AppTheme.SYSTEM
    }

    suspend fun setAppearance(theme: AppTheme) {
        dataStore.edit { prefs -> prefs[KEY_APPEARANCE] = theme.name }
    }

    val reasoningVisible: Flow<Boolean> = dataStore.data.map { prefs -> prefs[KEY_REASONING_VISIBLE] ?: true }

    suspend fun setReasoningVisible(visible: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_REASONING_VISIBLE] = visible }
    }

    val textSize: Flow<TextSize> = dataStore.data.map { prefs ->
        prefs[KEY_TEXT_SIZE]?.let { stored -> TextSize.entries.firstOrNull { it.name == stored } }
            ?: TextSize.NORMAL
    }

    suspend fun setTextSize(size: TextSize) {
        dataStore.edit { prefs -> prefs[KEY_TEXT_SIZE] = size.name }
    }

    val voiceAutoPlay: Flow<Boolean> = dataStore.data.map { prefs -> prefs[KEY_VOICE_AUTO_PLAY] ?: true }

    suspend fun setVoiceAutoPlay(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_VOICE_AUTO_PLAY] = enabled }
    }

    val onboardingDone: Flow<Boolean> = dataStore.data.map { prefs -> prefs[KEY_ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ONBOARDING_DONE] = done }
    }

    val voiceSpeed: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[KEY_VOICE_SPEED] ?: DEFAULT_VOICE_SPEED).coerceIn(MIN_VOICE_SPEED, MAX_VOICE_SPEED)
    }

    suspend fun setVoiceSpeed(speed: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_VOICE_SPEED] = speed.coerceIn(MIN_VOICE_SPEED, MAX_VOICE_SPEED)
        }
    }

    val voiceId: Flow<String?> = dataStore.data.map { prefs -> prefs[KEY_VOICE_ID] }

    suspend fun setVoiceId(voiceId: String?) {
        dataStore.edit { prefs ->
            if (voiceId.isNullOrBlank()) prefs.remove(KEY_VOICE_ID) else prefs[KEY_VOICE_ID] = voiceId
        }
    }

    val autoCheckUpdates: Flow<Boolean> = dataStore.data.map { prefs -> prefs[KEY_AUTO_CHECK_UPDATES] ?: true }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AUTO_CHECK_UPDATES] = enabled }
    }

    suspend fun thinkSelection(providerId: Long, modelId: Long): ReasoningConfig? {
        val prefs = dataStore.data.first()
        return prefs[stringPreferencesKey(thinkKey(providerId, modelId))]?.let { decodeReasoningConfig(it) }
    }

    suspend fun setThinkSelection(providerId: Long, modelId: Long, config: ReasoningConfig) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey(thinkKey(providerId, modelId))] = config.encode()
        }
    }

    private fun thinkKey(providerId: Long, modelId: Long): String =
        THINK_KEY_PREFIX + providerId + "_" + modelId

    val lastUpdateCheckTime: Flow<Long> = dataStore.data.map { prefs -> prefs[KEY_LAST_UPDATE_CHECK_TIME] ?: 0L }

    suspend fun setLastUpdateCheckTime(timestamp: Long) {
        dataStore.edit { prefs -> prefs[KEY_LAST_UPDATE_CHECK_TIME] = timestamp }
    }

    val lastNotifiedVersion: Flow<String?> = dataStore.data.map { prefs -> prefs[KEY_LAST_NOTIFIED_VERSION] }

    suspend fun setLastNotifiedVersion(version: String?) {
        dataStore.edit { prefs ->
            if (version.isNullOrBlank()) prefs.remove(KEY_LAST_NOTIFIED_VERSION) else prefs[KEY_LAST_NOTIFIED_VERSION] = version
        }
    }

    suspend fun legacyProviderConfig(): LegacyProviderConfig? {
        val prefs = dataStore.data.first()
        val name = prefs[KEY_NAME].orEmpty()
        val baseUrl = prefs[KEY_BASE_URL].orEmpty()
        val model = prefs[KEY_MODEL].orEmpty()
        if (name.isBlank() && baseUrl.isBlank() && model.isBlank()) return null
        return LegacyProviderConfig(
            name = name,
            baseUrl = baseUrl,
            model = model,
            enabled = prefs[KEY_ENABLED] ?: true,
        )
    }

    suspend fun clearLegacyProviderConfig() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_NAME)
            prefs.remove(KEY_BASE_URL)
            prefs.remove(KEY_MODEL)
            prefs.remove(KEY_ENABLED)
            prefs.remove(KEY_ID)
        }
    }

    internal suspend fun installLegacyProviderConfig(name: String, baseUrl: String, model: String) {
        dataStore.edit { prefs ->
            prefs[KEY_NAME] = name
            prefs[KEY_BASE_URL] = baseUrl
            prefs[KEY_MODEL] = model
            prefs[KEY_ENABLED] = true
        }
    }

    data class LegacyProviderConfig(
        val name: String,
        val baseUrl: String,
        val model: String,
        val enabled: Boolean,
    )

    companion object {
        private const val DATA_STORE_FILE = "provider_settings.preferences_pb"
        private const val THINK_KEY_PREFIX = "think_model_"

        private val KEY_ID = longPreferencesKey("id")
        private val KEY_NAME = stringPreferencesKey("name")
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_MODEL = stringPreferencesKey("model")
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_VOICE_OUTPUT = booleanPreferencesKey("voice_output_enabled")
        private val KEY_APPEARANCE = stringPreferencesKey("appearance")
        private val KEY_REASONING_VISIBLE = booleanPreferencesKey("reasoning_visible")
        private val KEY_TEXT_SIZE = stringPreferencesKey("text_size")
        private val KEY_VOICE_AUTO_PLAY = booleanPreferencesKey("voice_auto_play")
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val KEY_VOICE_SPEED = floatPreferencesKey("voice_speed")
        private val KEY_VOICE_ID = stringPreferencesKey("voice_id")
        private val KEY_AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        private val KEY_LAST_UPDATE_CHECK_TIME = longPreferencesKey("last_update_check_time")
        private val KEY_LAST_NOTIFIED_VERSION = stringPreferencesKey("last_notified_version")

        const val DEFAULT_VOICE_SPEED = 1.0f
        const val MIN_VOICE_SPEED = 0.5f
        const val MAX_VOICE_SPEED = 2.0f
    }
}
