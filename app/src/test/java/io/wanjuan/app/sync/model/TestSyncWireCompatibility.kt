package io.wanjuan.app.sync.model

import com.google.gson.JsonParser
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestSyncWireCompatibility {

    @Test
    fun readsBookObjectWrittenBeforeProgressWasEmbedded() {
        val payload = GSON.fromJsonObject<SyncBookPayload>(
            """{
                "a":"book_legacy",
                "b":{"a":"https://example.com/book","e":"Legacy","f":"Author","z":123,"G":456},
                "c":1000,
                "d":2000,
                "e":"device-legacy"
            }""".trimIndent()
        ).getOrThrow()

        assertEquals("book_legacy", payload.bookSyncId)
        assertEquals("device-legacy", payload.updatedByDeviceId)
        assertEquals("device-legacy", payload.progressUpdatedByDeviceId)
        assertEquals(0L, payload.progressUpdatedAt)
        assertEquals(1, payload.schemaVersion)
        assertEquals(456L, payload.book.syncTime)
    }

    @Test
    fun readsBookObjectWithEmbeddedProgress() {
        val payload = GSON.fromJsonObject<SyncBookPayload>(
            """{
                "a":"book_current",
                "b":{"a":"https://example.com/book","e":"Current","f":"Author","z":123,"G":456},
                "c":1000,
                "d":2000,
                "e":3000,
                "f":"device-current",
                "g":"device-progress",
                "h":2
            }""".trimIndent()
        ).getOrThrow()

        assertEquals(3000L, payload.progressUpdatedAt)
        assertEquals("device-current", payload.updatedByDeviceId)
        assertEquals("device-progress", payload.progressUpdatedByDeviceId)
        assertEquals(2, payload.schemaVersion)
    }

    @Test
    fun wireNamesStayStableWithoutReleaseObfuscation() {
        val json = GSON.toJson(
            SyncBookPayload(
                bookSyncId = "book_stable",
                book = SyncBook(bookUrl = "https://example.com/book", name = "Stable"),
                shelfUpdatedAt = 1000,
                catalogUpdatedAt = 2000,
                progressUpdatedAt = 3000,
                updatedByDeviceId = "device-current",
                progressUpdatedByDeviceId = "device-progress"
            )
        )
        val root = JsonParser.parseString(json).asJsonObject
        val book = root["b"].asJsonObject

        assertEquals("book_stable", root["a"].asString)
        assertEquals(3000L, root["e"].asLong)
        assertEquals("device-current", root["f"].asString)
        assertEquals("https://example.com/book", book["a"].asString)
        assertEquals("Stable", book["e"].asString)
        assertFalse(root.has("bookSyncId"))
        assertFalse(book.has("bookUrl"))
    }

    @Test
    fun nonBookWirePayloadsAlsoUseFrozenNames() {
        val order = JsonParser.parseString(
            GSON.toJson(SyncOrderPayload(10L, "device", listOf("first")))
        ).asJsonObject
        val tombstone = JsonParser.parseString(
            GSON.toJson(SyncTombstonePayload("book", "id", 20L, "device"))
        ).asJsonObject

        assertTrue(order.has("a"))
        assertTrue(order.has("b"))
        assertTrue(order.has("c"))
        assertTrue(tombstone.has("a"))
        assertTrue(tombstone.has("d"))
        assertFalse(order.has("updatedAt"))
        assertFalse(tombstone.has("deletedByDeviceId"))
    }
}
