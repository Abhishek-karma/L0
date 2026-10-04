package com.assistant.app.data

import androidx.room.withTransaction
import com.assistant.app.data.local.ChatDatabase
import com.assistant.app.data.local.ProviderEntity
import com.assistant.app.data.local.ProviderModelEntity
import com.assistant.app.data.local.ProviderWithActiveModel
import com.assistant.app.data.settings.AppPreferences
import com.assistant.app.data.settings.SecureKeyStore
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class ProviderDraft(
    val name: String = "",
    val baseUrl: String = "",
)

data class ModelDraft(
    val id: Long = 0,
    val model: String = "",
    val isActive: Boolean = false,
)

data class ActiveSelection(val provider: ProviderEntity, val model: ProviderModelEntity?)

class ProviderStore(
    private val db: ChatDatabase,
    private val appPreferences: AppPreferences,
    private val keyStore: SecureKeyStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    fun providers(): Flow<List<ProviderEntity>> = db.providerDao().observeAll()

    fun providersWithModel(): Flow<List<ProviderWithActiveModel>> = db.providerDao().observeAllWithModel()

    fun activeProvider(): Flow<ProviderEntity?> = db.providerDao().observeActive()

    fun activeSelection(): Flow<ActiveSelection?> = db.providerDao().observeActive().flatMapLatest { active ->
        if (active == null) {
            flowOf(null)
        } else {
            db.providerModelDao().observeForProvider(active.id)
                .map { models -> ActiveSelection(active, models.firstOrNull { it.isActive }) }
        }
    }

    suspend fun provider(id: Long): ProviderEntity? = db.providerDao().byId(id)

    suspend fun apiKey(id: Long): String? = withContext(ioDispatcher) { keyStore.apiKey(id) }

    fun models(providerId: Long): Flow<List<ProviderModelEntity>> =
        db.providerModelDao().observeForProvider(providerId)

    suspend fun modelsOf(providerId: Long): List<ProviderModelEntity> =
        db.providerModelDao().forProvider(providerId)

    suspend fun model(modelId: Long): ProviderModelEntity? = db.providerModelDao().byId(modelId)

    suspend fun activeModel(providerId: Long): ProviderModelEntity? =
        db.providerModelDao().activeForProvider(providerId)

    suspend fun ensureSeeded() = withContext(ioDispatcher) {
        val legacy = appPreferences.legacyProviderConfig()
        if (legacy == null && keyStore.legacyApiKey() == null) return@withContext
        val dao = db.providerDao()
        if (legacy != null && dao.count() == 0) {
            val id = dao.insert(
                ProviderEntity(
                    name = legacy.name.ifBlank { DEFAULT_NAME },
                    baseUrl = legacy.baseUrl,
                    isActive = true,
                ),
            )
            if (legacy.model.isNotBlank()) {
                db.providerModelDao().insert(
                    ProviderModelEntity(providerId = id, model = legacy.model, isActive = true),
                )
            }
            keyStore.legacyApiKey()?.let { keyStore.setApiKey(id, it) }
        }
        appPreferences.clearLegacyProviderConfig()
        keyStore.deleteLegacyApiKey()
    }

    suspend fun validate(id: Long, draft: ProviderDraft, models: List<ModelDraft>, enteredKey: String?): String? {
        if (draft.name.isBlank()) return ERROR_NAME_REQUIRED
        val normalizedUrl = if (draft.baseUrl.trim().equals("gemini", ignoreCase = true)) {
            "https://generativelanguage.googleapis.com"
        } else {
            draft.baseUrl.trim()
        }
        val uri = try {
            URI(normalizedUrl)
        } catch (_: URISyntaxException) {
            return ERROR_BASE_URL_INVALID
        }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host
        if ((scheme != "http" && scheme != "https") || host.isNullOrBlank()) {
            return ERROR_BASE_URL_INVALID
        }
        if (scheme == "http" && !isPermittedCleartextHost(host)) {
            return ERROR_CLEARTEXT_NOT_PERMITTED
        }
        if (models.none { it.model.isNotBlank() }) return ERROR_MODEL_REQUIRED
        if (enteredKey.isNullOrBlank() && apiKey(id).isNullOrBlank()) return ERROR_API_KEY_REQUIRED
        return null
    }

    fun isPermittedCleartextHost(host: String): Boolean {
        val cleanHost = host.lowercase().trim().removePrefix("[").removeSuffix("]")
        if (cleanHost == "localhost" || cleanHost == "127.0.0.1" || cleanHost == "10.0.2.2" || cleanHost == "::1") {
            return true
        }
        if (cleanHost.startsWith("127.")) {
            return true
        }
        if (cleanHost.endsWith(".local") || cleanHost.endsWith(".lan") ||
            cleanHost.endsWith(".home") || cleanHost.endsWith(".internal")) {
            return true
        }
        return false
    }

    suspend fun addProvider(draft: ProviderDraft, apiKey: String?, models: List<ModelDraft>): Long {
        val normalizedUrl = normalizeBaseUrl(draft.baseUrl)
        val id = db.providerDao().insert(
            ProviderEntity(
                name = draft.name.trim(),
                baseUrl = normalizedUrl,
            ),
        )
        saveModels(id, models)
        if (!apiKey.isNullOrBlank()) {
            val stored = withContext(ioDispatcher) { keyStore.setApiKey(id, apiKey) }
            check(stored) { ERROR_KEYSTORE_UNAVAILABLE }
        }
        db.withTransaction {
            if (db.providerDao().active() == null) {
                db.providerDao().setActive(id)
            }
        }
        return id
    }

    suspend fun updateProvider(id: Long, draft: ProviderDraft, apiKey: String?, models: List<ModelDraft>) {
        val normalizedUrl = normalizeBaseUrl(draft.baseUrl)
        if (!apiKey.isNullOrBlank()) {
            val stored = withContext(ioDispatcher) { keyStore.setApiKey(id, apiKey) }
            check(stored) { ERROR_KEYSTORE_UNAVAILABLE }
        }
        db.providerDao().update(id, draft.name.trim(), normalizedUrl)
        saveModels(id, models)
    }

    private suspend fun saveModels(providerId: Long, models: List<ModelDraft>) {
        val dao = db.providerModelDao()
        val keepIds = models.filter { it.id != 0L }.map { it.id }.toSet()
        dao.forProvider(providerId).filter { it.id !in keepIds }.forEach { dao.delete(it.id) }
        models.forEach { draft ->
            val entity = ProviderModelEntity(
                providerId = providerId,
                model = draft.model.trim(),
            )
            if (draft.id == 0L) {
                dao.insert(entity.copy(isActive = draft.isActive))
            } else {
                dao.update(id = draft.id, model = entity.model)
                if (draft.isActive) dao.setActiveForProvider(providerId, draft.id)
            }
        }
        ensureActiveModel(providerId)
    }

    private suspend fun ensureActiveModel(providerId: Long) {
        val dao = db.providerModelDao()
        if (dao.activeForProvider(providerId) != null) return
        val first = dao.forProvider(providerId).firstOrNull() ?: return
        dao.setActiveForProvider(providerId, first.id)
    }

    suspend fun deleteModel(providerId: Long, modelId: Long) {
        val dao = db.providerModelDao()
        val wasActive = dao.activeForProvider(providerId)?.id == modelId
        dao.delete(modelId)
        if (wasActive) {
            val next = dao.forProvider(providerId).firstOrNull()
            if (next != null) dao.setActiveForProvider(providerId, next.id) else dao.clearActive(providerId)
        }
    }

    suspend fun setActiveModel(providerId: Long, modelId: Long) {
        db.providerModelDao().setActiveForProvider(providerId, modelId)
    }

    suspend fun deleteProvider(id: Long) {
        db.providerModelDao().deleteForProvider(id)
        db.providerDao().delete(id)
        withContext(ioDispatcher) { keyStore.setApiKey(id, null) }
        appPreferences.clearThinkSelections(id)
        if (db.providerDao().active() == null) {
            db.providerDao().firstOtherThan(id)?.let { db.providerDao().setActive(it.id) }
        }
    }

    suspend fun setActive(id: Long) {
        db.providerDao().setActive(id)
    }

    private fun normalizeBaseUrl(raw: String): String =
        if (raw.trim().equals("gemini", ignoreCase = true)) {
            "https://generativelanguage.googleapis.com"
        } else {
            raw.trim()
        }

    companion object {

        const val ERROR_NAME_REQUIRED = "Give this provider a name, so you can tell it apart in the list."
        const val ERROR_BASE_URL_INVALID = "The base URL must start with http:// or https://, for example https://api.openai.com/v1."
        const val ERROR_CLEARTEXT_NOT_PERMITTED = "Cleartext HTTP is only permitted for local or private network endpoints (such as localhost, 10.0.2.2, or .local/.lan/.home/.internal domains). Use HTTPS for remote providers."
        const val ERROR_MODEL_REQUIRED = "Add at least one model, and give every model a name. It is sent to the provider exactly as typed."
        const val ERROR_API_KEY_REQUIRED = "Enter the API key for this provider. Leave the field empty only when you are keeping a key you already saved."
const val ERROR_KEYSTORE_UNAVAILABLE = "The secure keystore is unavailable, so the API key could not be saved. Nothing was stored."

        private const val DEFAULT_NAME = "Provider"
    }
}