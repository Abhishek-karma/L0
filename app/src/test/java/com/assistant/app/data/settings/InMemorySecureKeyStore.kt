package com.assistant.app.data.settings

class InMemorySecureKeyStore : SecureKeyStore {

    private val keys = HashMap<Long, String?>()

    override fun apiKey(id: Long): String? = keys[id]

    override fun setApiKey(id: Long, value: String?): Boolean {
        if (value == null) keys.remove(id) else keys[id] = value
        return true
    }

    private var legacyKey: String? = null

    override fun legacyApiKey(): String? = legacyKey

    override fun deleteLegacyApiKey() {
        legacyKey = null
    }

    fun setLegacyApiKey(value: String?) {
        legacyKey = value
    }
}
