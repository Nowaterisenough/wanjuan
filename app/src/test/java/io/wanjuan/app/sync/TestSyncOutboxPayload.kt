package io.wanjuan.app.sync

import io.wanjuan.app.sync.local.SyncOutbox
import io.wanjuan.app.sync.model.SyncDeleteKeyPayload
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncTombstonePayload
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TestSyncOutboxPayload {

    @Test
    fun validTombstoneIsUploadedUnchanged() {
        val json = GSON.toJson(
            SyncTombstonePayload(
                objectType = SyncObjectType.Book,
                objectId = "book-a",
                deletedAt = 100L,
                deletedByDeviceId = "device-a"
            )
        )
        val item = outbox(
            objectType = SyncObjectType.Book,
            objectId = "book-a",
            payloadJson = json
        )

        assertEquals(json, item.payloadJsonForUpload { "fallback-device" })
    }

    @Test
    fun missingDeletePayloadIsRebuiltAsTombstone() {
        val item = outbox(
            objectType = SyncObjectType.RuleSub,
            objectId = "rule-a",
            payloadJson = null,
            createdAt = 90L,
            versionTimestamp = 100L,
            versionDeviceId = "device-a"
        )

        val payload = item.payloadJsonForUpload { "fallback-device" }.toTombstone()

        assertEquals(SyncObjectType.RuleSub, payload.objectType)
        assertEquals("rule-a", payload.objectId)
        assertEquals(100L, payload.deletedAt)
        assertEquals("device-a", payload.deletedByDeviceId)
        assertNull(payload.objectKey)
    }

    @Test
    fun legacyDeleteKeyIsPreservedInRebuiltTombstone() {
        val item = outbox(
            objectType = SyncObjectType.BookSource,
            objectId = "source-a",
            payloadJson = GSON.toJson(SyncDeleteKeyPayload("https://example.com/source")),
            createdAt = 90L,
            versionTimestamp = 0L,
            versionDeviceId = ""
        )

        val payload = item.payloadJsonForUpload { "fallback-device" }.toTombstone()

        assertEquals(90L, payload.deletedAt)
        assertEquals("fallback-device", payload.deletedByDeviceId)
        assertEquals("https://example.com/source", payload.objectKey)
    }

    @Test
    fun missingNonDeletePayloadStillFails() {
        val item = outbox(
            objectType = SyncObjectType.Book,
            objectId = "book-a",
            payloadJson = null,
            operation = "upsert"
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            item.payloadJsonForUpload { "device-a" }
        }

        assertEquals("Missing sync payload: book/book-a", error.message)
    }

    private fun outbox(
        objectType: String,
        objectId: String,
        payloadJson: String?,
        operation: String = "delete",
        createdAt: Long = 100L,
        versionTimestamp: Long = createdAt,
        versionDeviceId: String = "device-a"
    ) = SyncOutbox(
        objectType = objectType,
        objectId = objectId,
        operation = operation,
        payloadJson = payloadJson,
        createdAt = createdAt,
        versionTimestamp = versionTimestamp,
        versionDeviceId = versionDeviceId
    )

    private fun String.toTombstone(): SyncTombstonePayload =
        GSON.fromJsonObject<SyncTombstonePayload>(this).getOrThrow()
}
