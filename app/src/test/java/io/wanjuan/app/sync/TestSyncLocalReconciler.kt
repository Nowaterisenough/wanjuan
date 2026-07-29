package io.wanjuan.app.sync

import io.wanjuan.app.sync.local.SyncMetadata
import io.wanjuan.app.sync.local.SyncOutbox
import io.wanjuan.app.sync.model.SyncSnapshot
import io.wanjuan.app.sync.model.SyncVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TestSyncLocalReconciler {

    @Test
    fun firstScanQueuesEveryObjectAndSecondScanIsIdempotent() {
        val store = MemoryReconcileStore()
        val snapshots = mutableListOf(
            snapshot("book", "a", "hash-a"),
            snapshot("book", "b", "hash-b")
        )
        val reconciler = reconciler(store) { snapshots }

        assertEquals(SyncCaptureResult(2, 0, 0), reconciler.capture())
        assertEquals(2, store.outbox.size)

        store.markAllSynced()
        assertEquals(SyncCaptureResult(0, 0, 2), reconciler.capture())
        assertEquals(0, store.outbox.size)
    }

    @Test
    fun changedContentReplacesOlderPendingUpsert() {
        val store = MemoryReconcileStore()
        var snapshot = snapshot("book", "a", "hash-a")
        val reconciler = reconciler(store) { listOf(snapshot) }

        reconciler.capture()
        snapshot = snapshot("book", "a", "hash-b")
        reconciler.capture()

        assertEquals(1, store.outbox.size)
        assertEquals("{\"value\":\"hash-b\"}", store.outbox.single().payloadJson)
    }

    @Test
    fun revertingWhileDirtyReplacesStalePendingPayload() {
        val store = MemoryReconcileStore()
        var snapshot = snapshot("book", "a", "hash-a")
        val reconciler = reconciler(store) { listOf(snapshot) }
        reconciler.capture()
        store.markAllSynced()

        snapshot = snapshot("book", "a", "hash-b")
        reconciler.capture()
        snapshot = snapshot("book", "a", "hash-a")
        reconciler.capture()

        assertEquals(1, store.outbox.size)
        assertEquals("{\"value\":\"hash-a\"}", store.outbox.single().payloadJson)
    }

    @Test
    fun missingSyncedObjectQueuesOneStableTombstone() {
        val store = MemoryReconcileStore()
        var snapshots = listOf(snapshot("book", "a", "hash-a"))
        val reconciler = reconciler(store) { snapshots }
        reconciler.capture()
        store.markAllSynced()

        snapshots = emptyList()
        assertEquals(SyncCaptureResult(0, 1, 0), reconciler.capture())
        val firstDelete = store.outbox.single()
        assertEquals("delete", firstDelete.operation)
        assertEquals(100L, firstDelete.versionTimestamp)
        assertEquals("device-a", firstDelete.versionDeviceId)
        assertNotNull(firstDelete.payloadJson)

        assertEquals(SyncCaptureResult(0, 0, 0), reconciler.capture())
        assertEquals(firstDelete, store.outbox.single())
    }

    private fun reconciler(
        store: MemoryReconcileStore,
        snapshots: () -> List<SyncSnapshot>
    ): SyncLocalReconciler {
        return SyncLocalReconciler(
            snapshotSource = SyncLocalSnapshotSource(snapshots),
            store = store,
            clock = object : SyncClock {
                override fun now(): Long = 100L
            },
            deviceIdProvider = { "device-a" },
            managedObjectTypes = setOf("book")
        )
    }

    private fun snapshot(type: String, id: String, hash: String): SyncSnapshot {
        return SyncSnapshot(
            objectType = type,
            objectId = id,
            contentHash = hash,
            payloadJson = "{\"value\":\"$hash\"}",
            version = SyncVersion(50L, "device-a")
        )
    }

    private class MemoryReconcileStore : SyncReconcileStore {
        val metadata = linkedMapOf<Pair<String, String>, SyncMetadata>()
        val outbox = mutableListOf<SyncOutbox>()

        override fun metadata(objectType: String, objectId: String): SyncMetadata? =
            metadata[objectType to objectId]

        override fun metadataForType(objectType: String): List<SyncMetadata> =
            metadata.values.filter { it.objectType == objectType }

        override fun saveMetadata(metadata: SyncMetadata) {
            this.metadata[metadata.objectType to metadata.objectId] = metadata
        }

        override fun replaceOutbox(item: SyncOutbox) {
            outbox.removeAll {
                it.objectType == item.objectType && it.objectId == item.objectId
            }
            outbox += item
        }

        fun markAllSynced() {
            metadata.replaceAll { _, value ->
                value.copy(lastSyncedHash = value.lastSyncedHash ?: value.objectId.let {
                    when (it) {
                        "a" -> "hash-a"
                        "b" -> "hash-b"
                        else -> error("Unexpected id: $it")
                    }
                }, dirty = false)
            }
            outbox.clear()
        }
    }
}
