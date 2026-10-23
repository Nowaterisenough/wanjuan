package io.wanjuan.app.sync.delta

import io.wanjuan.app.sync.*
import io.wanjuan.app.sync.local.SyncDeltaCursor
import io.wanjuan.app.sync.local.SyncDeltaState
import io.wanjuan.app.sync.model.*
import io.wanjuan.app.sync.remote.*
import io.wanjuan.app.utils.GSON
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class TestDeltaEngine {
    @Test fun hundredBlocksAreAppliedInBoundedBatchesWithoutPerBlockFullScans() = runBlocking {
        val remote = Remote(); val a = Local(); val ae = engine(a, remote)
        repeat(100) { a.values[id] = content(tag = "tag-$it"); ae.flush() }
        val b = Local().apply { saveCursor(SyncDeltaCursor("target", legacyImported = true)) }
        engine(b, remote).pull()
        assertEquals(a.values, b.values)
        assertTrue("Full scans: ${b.snapshotReads}", b.snapshotReads <= 15)
        assertEquals(0, engine(b, remote).flush().uploaded)
    }

    @Test fun missingIndexedBlockDoesNotAdvanceCursorOrApplyAnyChanges() = runBlocking {
        val remote = Remote(); val a = Local(); val ae = engine(a, remote)
        repeat(20) { a.values[id] = content(tag = "x".repeat(12000) + it); ae.flush() }
        remote.objects.remove(remote.objects.keys.first { "/blocks/" in it })
        val b = Local().apply { saveCursor(SyncDeltaCursor("target", legacyImported = true)) }
        assertTrue(runCatching { engine(b, remote).pull() }.isFailure)
        assertEquals(0L, b.cursor("target").timestamp)
        assertTrue(b.values.isEmpty())
    }

    private val id = SyncIdentity(SyncObjectType.Book, "book")
    private fun content(tag: String = "old", chapter: Int = 1, check: Long = 100, read: Long = 100): String =
        DeltaCodec.normalize(id.objectType, GSON.toJson(SyncBookPayload("book",
            SyncBook(bookUrl = "url", origin = "source", name = "Book", customTag = tag,
                durChapterIndex = chapter, syncTime = chapter.toLong(), lastCheckTime = check, durChapterTime = read),
            100, 100, 100, "device")))

    @Test fun unchangedPullAndFlushNeverUploadOrRereadOldBlocks() = runBlocking {
        val remote = Remote(); val a = Local(); val engine = engine(a, remote)
        engine.pull(); assertTrue(remote.objects.isEmpty())
        a.values[id] = content(); assertEquals(1, engine.flush().uploaded)
        val writes = remote.writes.size
        remote.reads.clear()
        repeat(3) { assertEquals(0, engine.pull().downloaded); assertEquals(0, engine.flush().uploaded) }
        assertEquals(writes, remote.writes.size)
        assertEquals(List(3) { DeltaRemote.HEAD }, remote.reads)
    }

    @Test fun oneChangedBookPublishesOnlyChangedFields() = runBlocking {
        val remote = Remote(); val a = Local(); val engine = engine(a, remote)
        repeat(20) { a.values[SyncIdentity(id.objectType, "$it")] = content() }
        engine.flush()
        val changed = SyncIdentity(id.objectType, "5")
        a.values[changed] = content(tag = "edited")
        remote.reads.clear()
        assertEquals(1, engine.flush().uploaded)
        assertEquals(listOf(DeltaRemote.HEAD), remote.reads)
        val head = DeltaRemote(remote).head().second
        val changes = DeltaRemote(remote).block(head).changes
        assertEquals(1, changes.size)
        assertEquals("5", changes.single().objectId)
        assertEquals(listOf(listOf("b", "h")), changes.single().fields.map { it.path })
        assertFalse(changes.single().create)
    }

    @Test fun batchLimitUsesPatchBytesRatherThanLargeUnchangedFields() = runBlocking {
        val remote = Remote(); val a = Local(); val engine = engine(a, remote)
        val large = "x".repeat(600000)
        repeat(3) { a.values[SyncIdentity(id.objectType, "$it")] = content(tag = large) }
        repeat(3) { engine.flush() }
        repeat(3) { a.values[SyncIdentity(id.objectType, "$it")] = content(tag = large, chapter = 2) }
        val result = engine.flush()
        assertEquals(3, result.uploaded)
        assertEquals(0, result.pending)
        val block = DeltaRemote(remote).block(DeltaRemote(remote).head().second)
        assertEquals(3, block.changes.size)
        assertTrue(GSON.toJson(block).toByteArray().size < 10000)
    }

    @Test fun bookFeedbackWaitsForCommitAndKeepsEditsMadeDuringUploadPending() = runBlocking {
        val remote = Remote(); val local = Local()
        val tracker = SyncProgressTracker().apply { begin() }
        val engine = DeltaEngine(local, DeltaRemote(remote), "target", clock(100), progress = tracker)
        local.values[id] = content()
        remote.beforeCas = {
            assertTrue(tracker.snapshot().books.getValue(id.objectId).fraction < 1f)
            local.values[id] = content(tag = "second")
        }
        engine.flush()
        assertTrue(id.objectId in tracker.snapshot().books)
        engine.flush()
        assertTrue(tracker.snapshot().books.isEmpty())
    }

    @Test fun readingAndCheckTimestampsAreNotEdits() {
        assertEquals(content(check = 100, read = 100), content(check = 900, read = 900))
        assertNotEquals(content(chapter = 1), content(chapter = 2))
    }

    @Test fun progressTimestampAndDefaultConfigAllocationAreNotEdits() {
        val payload = GSON.fromJson(content(), SyncBookPayload::class.java)
        val touched = payload.copy(book = payload.book.copy(syncTime = 900,
            readConfig = io.wanjuan.app.data.entities.Book.ReadConfig()))
        val unallocated = payload.copy(book = payload.book.copy(readConfig = null))
        assertEquals(DeltaCodec.normalize(id.objectType, GSON.toJson(unallocated)),
            DeltaCodec.normalize(id.objectType, GSON.toJson(touched)))
    }

    @Test fun pullOnlyNeverPublishesLocalEditsAndRemoteChangesDoNotEcho() = runBlocking {
        val remote = Remote(); val a = Local(); val b = Local()
        val ae = engine(a, remote); val be = engine(b, remote)
        a.values[id] = content(); ae.flush(); be.pull()
        assertEquals(a.values, b.values)
        val writes = remote.writes.size
        b.values[id] = content(tag = "local")
        be.pull()
        assertEquals(writes, remote.writes.size)
        assertEquals("local", book(b).customTag)
        assertEquals(0, ae.flush().uploaded)
    }

    @Test fun concurrentDifferentFieldsMergeAndClockSkewCannotSkipChanges() = runBlocking {
        val remote = Remote(); val a = Local(); val b = Local()
        val ae = engine(a, remote, 9000); val be = engine(b, remote, 1)
        a.values[id] = content(); ae.flush(); be.pull()
        b.values[id] = content(tag = "tag-b")
        a.values[id] = content(chapter = 9); ae.flush()
        assertEquals(1, be.flush().uploaded)
        ae.pull()
        assertEquals("tag-b", book(a).customTag)
        assertEquals(9, book(a).durChapterIndex)
        assertEquals(a.values, b.values)
        assertTrue(b.cursor("target").timestamp > 9000)
    }

    @Test fun deletionWinsOverStaleEditButExplicitReaddWorks() = runBlocking {
        val remote = Remote(); val a = Local(); val b = Local()
        val ae = engine(a, remote); val be = engine(b, remote)
        a.values[id] = content(); ae.flush(); be.pull()
        b.values[id] = content(tag = "offline edit")
        a.values.remove(id); assertEquals(1, ae.flush().uploaded)
        assertEquals(0, be.flush().uploaded)
        assertNull(b.values[id])
        b.values[id] = content(tag = "readded"); be.flush(); ae.pull()
        assertEquals("readded", book(a).customTag)
    }

    @Test fun lostPublishAcknowledgementDoesNotPublishSameEditTwice() = runBlocking {
        val remote = Remote(); val local = Local(); val engine = engine(local, remote)
        local.values[id] = content()
        remote.loseAcknowledgement = true
        assertTrue(runCatching { engine.flush() }.isFailure)
        assertTrue(engine.hasPending())
        assertEquals(0, engine.flush().uploaded)
        assertFalse(engine.hasPending())
        assertEquals(1, remote.writes.count { it == DeltaRemote.HEAD })
    }

    @Test fun failedApplyDoesNotAdvanceTimestampAndRetryIsSafe() = runBlocking {
        val remote = Remote(); val a = Local(); val b = Local()
        a.values[id] = content(); engine(a, remote).flush()
        b.failApply = true
        assertTrue(runCatching { engine(b, remote).pull() }.isFailure)
        assertEquals(0, b.cursor("target").timestamp)
        assertTrue(b.values.isEmpty())
        b.failApply = false
        engine(b, remote).pull()
        assertEquals(a.values, b.values)
    }

    @Test fun editDuringUploadRemainsPendingInsteadOfBeingAcknowledged() = runBlocking {
        val remote = Remote(); val a = Local(); val ae = engine(a, remote)
        a.values[id] = content(); ae.flush()
        a.values[id] = content(tag = "first")
        remote.beforeCas = { a.values[id] = content(tag = "second") }
        ae.flush()
        assertTrue(ae.hasPending())
        assertEquals("second", book(a).customTag)
        ae.flush()
        val b = Local(); engine(b, remote).pull()
        assertEquals("second", book(b).customTag)
    }

    @Test fun undoDuringUploadIsNotLostWhenItMatchesTheOldCloudBaseline() = runBlocking {
        val remote = Remote(); val a = Local(); val ae = engine(a, remote)
        a.values[id] = content(); ae.flush()
        a.values[id] = content(tag = "temporary")
        remote.beforeCas = { a.values[id] = content(); ae.capture() }
        ae.flush()
        assertEquals("old", book(a).customTag)
        assertTrue(ae.hasPending())
        ae.flush()
        val b = Local(); engine(b, remote).pull()
        assertEquals("old", book(b).customTag)
    }

    @Test fun undoAfterLostAcknowledgementSurvivesEngineRestart() = runBlocking {
        val remote = Remote(); val a = Local(); val ae = engine(a, remote)
        a.values[id] = content(); ae.flush()
        a.values[id] = content(tag = "temporary")
        remote.loseAcknowledgement = true
        assertTrue(runCatching { ae.flush() }.isFailure)
        a.values[id] = content(); ae.capture()
        val restarted = engine(a, remote)
        restarted.flush()
        val b = Local(); engine(b, remote).pull()
        assertEquals("old", book(b).customTag)
        assertFalse(restarted.hasPending())
    }

    @Test fun fieldRemovalDoesNotRecreateAParentDeletedOnAnotherDevice() {
        val fields = DeltaJson.diff(DeltaJson.parse("{\"config\":{\"value\":1}}"), DeltaJson.parse("{\"config\":{}}"))
        assertEquals("{}", DeltaJson.apply(DeltaJson.parse("{}"), fields).toString())
        val explicitNull = DeltaJson.diff(DeltaJson.parse("{}"), DeltaJson.parse("{\"value\":null}"))
        assertEquals("{\"value\":null}", DeltaJson.apply(null, explicitNull).toString())
    }

    @Test fun concurrentHeadWriterIsRetriedWithoutLosingEitherEdit() = runBlocking {
        val remote = Remote(); val a = Local(); val b = Local()
        val ae = engine(a, remote); val be = engine(b, remote)
        a.values[id] = content(); ae.flush(); be.pull()
        a.values[id] = content(tag = "a"); b.values[id] = content(chapter = 5)
        remote.beforeCas = { be.flush() }
        ae.flush(); be.pull()
        assertEquals("a", book(b).customTag)
        assertEquals(5, book(b).durChapterIndex)
    }

    @Test fun largeUnicodeChangesAreChunkedAndCorruptionNeverAdvancesCursor() = runBlocking {
        val remote = Remote(); val a = Local(); val b = Local()
        a.values[id] = content(tag = "\u4e66\ud83d\udcd6".repeat(240000))
        engine(a, remote).flush()
        assertTrue(remote.objects.keys.any { it.contains("/chunks/") })
        assertTrue(remote.objects.values.all { it.toByteArray(Charsets.UTF_8).size <= DeltaRemote.MAX_FILE_BYTES })
        val chunk = remote.objects.keys.first { it.contains("/chunks/") }
        val saved = remote.objects.remove(chunk)!!
        assertTrue(runCatching { engine(b, remote).pull() }.isFailure)
        assertEquals(0, b.cursor("target").timestamp)
        remote.objects[chunk] = saved
        engine(b, remote).pull()
        assertEquals(a.values, b.values)
    }

    @Test fun failedChunkUploadNeverPublishesHead() = runBlocking {
        val remote = Remote(); val local = Local(); val engine = engine(local, remote)
        local.values[id] = content(tag = "x".repeat(1500000))
        remote.failChunks = true
        assertTrue(runCatching { engine.flush() }.isFailure)
        assertNull(remote.objects[DeltaRemote.HEAD])
        assertTrue(engine.hasPending())
        remote.failChunks = false
        engine.flush()
        assertFalse(engine.hasPending())
    }

    @Test fun cursorIsScopedToTargetAndLegacyBaselineIsImportedWithoutUpload() = runBlocking {
        val remote = Remote(); val local = Local()
        local.values[id] = content()
        val ae = DeltaEngine(local, DeltaRemote(remote), "a", clock(10), legacy = { mapOf(id to content()) })
        ae.pull()
        assertEquals(0, ae.flush().uploaded)
        assertTrue(remote.objects.isEmpty())
        local.values[id] = content(tag = "edited"); ae.flush()
        val fresh = Local(); engine(fresh, remote).pull()
        assertEquals("edited", book(fresh).customTag)
        assertEquals(0, local.cursor("b").timestamp)
    }

    private fun book(local: Local) = GSON.fromJson(local.values.getValue(id), SyncBookPayload::class.java).book
    private fun clock(time: Long) = object : SyncClock { override fun now() = time }
    private fun engine(local: Local, remote: Remote, time: Long = 100) = DeltaEngine(local, DeltaRemote(remote), "target", clock(time))

    private class Local : DeltaLocal {
        val values = linkedMapOf<SyncIdentity, String>()
        private val states = linkedMapOf<Triple<String, String, String>, SyncDeltaState>()
        private val cursors = hashMapOf<String, SyncDeltaCursor>()
        var failApply = false
        var snapshotReads = 0
        override fun transaction(action: () -> Unit) {
            val oldValues = values.toMap(); val oldStates = states.toMap(); val oldCursors = cursors.toMap()
            try { action() } catch (e: Exception) {
                values.clear(); values.putAll(oldValues); states.clear(); states.putAll(oldStates)
                cursors.clear(); cursors.putAll(oldCursors); throw e
            }
        }
        override fun states(target: String) = states.values.filter { it.target == target }
        override fun save(state: SyncDeltaState) { states[Triple(state.target, state.objectType, state.objectId)] = state }
        override fun cursor(target: String) = cursors[target] ?: SyncDeltaCursor(target)
        override fun saveCursor(cursor: SyncDeltaCursor) { cursors[cursor.target] = cursor }
        override fun snapshots() = values.toMap().also { snapshotReads++ }
        override fun apply(identity: SyncIdentity, json: String?, timestamp: Long): SyncApplyOutcome {
            check(!failApply) { "Injected apply failure" }
            if (json == null) { values.remove(identity); return SyncApplyOutcome.Deleted }
            val old = values.put(identity, json)
            return if (old == null) SyncApplyOutcome.Inserted else SyncApplyOutcome.Updated
        }
    }

    private class Remote : SyncRemoteStore {
        val objects = linkedMapOf<String, String>()
        val reads = arrayListOf<String>(); val writes = arrayListOf<String>()
        var loseAcknowledgement = false; var failChunks = false
        var beforeCas: (suspend () -> Unit)? = null
        override suspend fun ensureDirs() = Unit
        override suspend fun list(relativeDir: String): List<SyncRemoteFile> = error("Journal must not list old objects")
        override suspend fun downloadJson(relativePath: String): String? { reads += relativePath; return objects[relativePath] }
        override suspend fun uploadJson(relativePath: String, json: String) {
            if (failChunks && relativePath.contains("/chunks/")) throw IOException("Injected chunk failure")
            writes += relativePath; objects[relativePath] = json
        }
        override suspend fun readForUpdate(path: String) = SyncRemoteRead(downloadJson(path))
        override suspend fun compareAndSetJson(path: String, previous: SyncRemoteRead, json: String): Boolean {
            val callback = beforeCas; beforeCas = null; callback?.invoke()
            if (objects[path] != previous.json) return false
            writes += path; objects[path] = json
            if (loseAcknowledgement) { loseAcknowledgement = false; throw IOException("Lost acknowledgement") }
            return true
        }
    }
}
