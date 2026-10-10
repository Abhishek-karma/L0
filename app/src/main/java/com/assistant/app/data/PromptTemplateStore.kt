package com.assistant.app.data

import com.assistant.app.data.local.ChatDatabase
import com.assistant.app.data.local.PromptTemplateEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PromptTemplateStore(
    private val db: ChatDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun templates(): Flow<List<PromptTemplate>> = db.promptTemplateDao().observeAll()
        .map { entities -> entities.map { it.toTemplate() } }

    /** Returns the stored id, or null when the title or body is unusable. */
    suspend fun save(id: String?, title: String, body: String): String? {
        val cleanTitle = title.trim()
        val cleanBody = body.trim()
        if (!PromptTemplates.isValid(cleanTitle, cleanBody)) return null
        val templateId = id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        db.promptTemplateDao().upsert(
            PromptTemplateEntity(
                id = templateId,
                title = cleanTitle,
                body = cleanBody,
                updatedAt = clock(),
            ),
        )
        return templateId
    }

    suspend fun delete(id: String) {
        db.promptTemplateDao().delete(id)
    }

    private fun PromptTemplateEntity.toTemplate() = PromptTemplate(
        id = id,
        title = title,
        body = body,
        updatedAt = updatedAt,
    )
}