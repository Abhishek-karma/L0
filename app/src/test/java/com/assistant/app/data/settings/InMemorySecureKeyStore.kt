package com.assistant.app.data.settings

class InMemorySecureKeyStore : SecureKeyStore {

    private val keys = HashMap<Long, String?>()

    var failNextSetApiKey = false

    override fun apiKey(id: Long): String? = keys[id]

    override fun setApiKey(id: Long, value: String?): Boolean {
        if (failNextSetApiKey) {
            failNextSetApiKey = false
            return false
        }
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

    override val isAvailable: Boolean = true

    override val needsCredentialReentry: Boolean = false
}
