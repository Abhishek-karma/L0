package com.assistant.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.assistant.app.data.PromptTemplateStore
import com.assistant.app.data.local.ChatDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PromptTemplateStoreTest {

    private val dbName = "test_prompt_templates.db"
    private var database: ChatDatabase? = null
    private lateinit var store: PromptTemplateStore

    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase(dbName)
        open()
    }

    private fun open() {
        val opened = Room
            .databaseBuilder(ApplicationProvider.getApplicationContext(), ChatDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(
                ChatDatabase.MIGRATION_1_2,
                ChatDatabase.MIGRATION_2_3,
                ChatDatabase.MIGRATION_3_4,
                ChatDatabase.MIGRATION_4_5,
                ChatDatabase.MIGRATION_5_6,
                ChatDatabase.MIGRATION_6_7,
                ChatDatabase.MIGRATION_7_8,
                ChatDatabase.MIGRATION_8_9,
                ChatDatabase.MIGRATION_9_10,
                ChatDatabase.MIGRATION_10_11,
            )
            .build()
        database = opened
        store = PromptTemplateStore(opened) { 1_000L }
    }

    @After
    fun tearDown() {
        database?.close()
        ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase(dbName)
    }

    @Test
    fun `saved template is read back`() = runTest {
        val id = store.save(null, "Weekly summary", "Summarize {{text}} in three bullets.")

        val stored = store.templates().first()

        assertNotNull(id)
        assertEquals(1, stored.size)
        assertEquals("Weekly summary", stored.single().title)
        assertEquals("Summarize {{text}} in three bullets.", stored.single().body)
    }

    @Test
    fun `templates survive a store restart`() = runTest {
        store.save(null, "Standup", "Draft a standup update.")

        database?.close()
        open()

        val stored = store.templates().first()

        assertEquals(1, stored.size)
        assertEquals("Standup", stored.single().title)
    }

    @Test
    fun `editing keeps the same id`() = runTest {
        val id = store.save(null, "Draft", "First body.")

        store.save(id, "Draft", "Second body.")

        val stored = store.templates().first()
        assertEquals(1, stored.size)
        assertEquals("Second body.", stored.single().body)
        assertEquals(id, stored.single().id)
    }

    @Test
    fun `deleting removes only that template`() = runTest {
        val keep = store.save(null, "Keep", "body")
        store.save(null, "Remove", "body")

        store.delete(keep!!)

        val stored = store.templates().first()
        assertEquals(1, stored.size)
        assertEquals("Remove", stored.single().title)
    }

    @Test
    fun `invalid templates are not stored`() = runTest {
        assertNull(store.save(null, "", "body"))
        assertNull(store.save(null, "Title", "   "))

        assertTrue(store.templates().first().isEmpty())
    }

    @Test
    fun `titles and bodies are trimmed on save`() = runTest {
        store.save(null, "  Title  ", "  body  ")

        val stored = store.templates().first().single()
        assertEquals("Title", stored.title)
        assertEquals("body", stored.body)
    }

    @Test
    fun `deleting an unknown template does not fail`() = runTest {
        store.save(null, "Title", "body")

        store.delete("missing-id")

        assertEquals(1, store.templates().first().size)
    }
}