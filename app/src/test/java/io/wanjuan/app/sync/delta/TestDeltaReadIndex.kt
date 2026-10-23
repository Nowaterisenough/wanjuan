package io.wanjuan.app.sync.delta

import io.wanjuan.app.sync.remote.*
import io.wanjuan.app.utils.GSON
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class TestDeltaReadIndex {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun recentSmallHistoryComesWithOneHeadReadAndOldReadersStillWork() = runBlocking {
        val store = Store()
        val head = publish(store, 20)
        store.calls.clear()
        val cache = MemoryDeltaContentCache()
        val writes = AtomicInteger()
        val reader = DeltaRemote(store, object : DeltaContentCache by cache {
            override fun write(path: String, bytes: ByteArray) { writes.incrementAndGet(); cache.write(path, bytes) }
        })
        assertEquals(head, reader.head().second)
        assertEquals(0, writes.get())
        val results = arrayListOf<DeltaBlockRead>()
        assertEquals(20, reader.chain(head, DeltaHead(), results::add).size)
        assertTrue(results.all { it.reused })
        assertEquals(listOf(DeltaRemote.HEAD), store.calls)
        assertEquals(20, writes.get())
        reader.head()
        assertEquals(20, writes.get())
        var oldReader = GSON.fromJson(store.objects.getValue(DeltaRemote.HEAD), DeltaHead::class.java)
        var count = 0
        while (oldReader.timestamp > 0) {
            val json = store.objects.getValue(oldReader.block)
            assertFalse(DeltaJson.parse(json).getAsJsonObject("previous").has("readAhead"))
            oldReader = GSON.fromJson(json, DeltaBlock::class.java).previous
            count++
        }
        assertEquals(20, count)
    }

    @Test fun hundredBlocksUsePagedIndexAndAtMostThreeConcurrentReads() = runBlocking {
        val store = Store()
        val head = publish(store, 100, large = true)
        store.calls.clear()
        store.delayMillis = 8
        val reader = DeltaRemote(store)
        reader.head()
        val chain = reader.chain(head, DeltaHead()) { }
        assertEquals(100, chain.size)
        assertEquals(100, store.calls.count { "/blocks/" in it })
        assertEquals(1, store.calls.count { "/indexes/" in it })
        assertEquals(3, store.maxReads.get())
        assertTrue(store.objects.getValue(DeltaRemote.HEAD).toByteArray().size <= DeltaRemote.MAX_HEAD_BYTES)
        assertTrue(store.objects.values.all { it.toByteArray().size <= DeltaRemote.MAX_FILE_BYTES })
    }

    @Test fun recentWindowStaysFullAcrossIndexPageBoundaries() = runBlocking {
        val store = Store()
        val head = publish(store, 130)
        val root = DeltaJson.parse(store.objects.getValue(DeltaRemote.HEAD))
        val entries = root.getAsJsonObject("readAhead").getAsJsonArray("recent")
        assertEquals(64, entries.size())
        assertEquals(2, store.objects.keys.count { "/indexes/" in it })
        val cursorJson = store.objects.values.first { json ->
            val value = DeltaJson.parse(json)
            value.has("previous") && value["timestamp"].asLong == 110L
        }
        val cursor = DeltaHead(110, "delta-v2/blocks/${DeltaRemote.checksum(cursorJson.toByteArray())}.json")
        store.calls.clear()
        val reader = DeltaRemote(store)
        reader.head()
        assertEquals(20, reader.chain(head, cursor) { }.size)
        assertEquals(listOf(DeltaRemote.HEAD), store.calls)
        val fresh = DeltaRemote(store)
        fresh.head()
        assertEquals(130, fresh.chain(head, DeltaHead()) { }.size)
    }

    @Test fun oldHeadWithoutIndexFallsBackAndCanBeExtendedByNewWriter() = runBlocking {
        val store = Store()
        var head = publish(store, 10)
        store.objects[DeltaRemote.HEAD] = GSON.toJson(head)
        store.calls.clear()
        var reader = DeltaRemote(store)
        reader.head()
        assertEquals(10, reader.chain(head, DeltaHead()) { }.size)
        assertEquals(11, store.calls.size)
        val previous = reader.head().first
        val block = block(11, head, false)
        head = DeltaHead(11, reader.writeBlock(block))
        assertTrue(reader.publish(previous, head, listOf(head to block)))
        reader = DeltaRemote(store)
        reader.head()
        assertEquals(11, reader.chain(head, DeltaHead()) { }.size)
    }

    @Test fun verifiedDiskCacheSurvivesReaderRestartAndRepairsCorruption() = runBlocking {
        val store = Store()
        val head = publish(store, 20, large = true)
        val directory = temporary.newFolder()
        var reader = DeltaRemote(store, FileDeltaContentCache(directory))
        reader.head()
        val chain = reader.chain(head, DeltaHead()) { }
        reader.trimCache()
        store.calls.clear()
        reader = DeltaRemote(store, FileDeltaContentCache(directory))
        reader.head()
        val hits = arrayListOf<DeltaBlockRead>()
        assertEquals(chain, reader.chain(head, DeltaHead(), hits::add))
        assertTrue(hits.all { it.reused })
        assertEquals(listOf(DeltaRemote.HEAD), store.calls)
        val damaged = chain[7].block
        File(directory, damaged.removePrefix("delta-v2/")).writeText("broken")
        store.calls.clear()
        reader = DeltaRemote(store, FileDeltaContentCache(directory))
        reader.head()
        reader.chain(head, DeltaHead()) { }
        assertEquals(listOf(DeltaRemote.HEAD, damaged), store.calls)
    }

    @Test fun interruptedDownloadReusesOnlyVerifiedFilesOnRetry() = runBlocking {
        val store = Store()
        val head = publish(store, 20, large = true)
        val missing = DeltaJson.parse(store.objects.getValue(DeltaRemote.HEAD))
            .getAsJsonObject("readAhead").getAsJsonArray("recent")[10].asJsonObject["block"].asString
        val saved = store.objects.remove(missing)!!
        val directory = temporary.newFolder()
        val first = DeltaRemote(store, FileDeltaContentCache(directory))
        first.head()
        assertTrue(runCatching { first.chain(head, DeltaHead()) { } }.isFailure)
        store.objects[missing] = saved
        store.calls.clear()
        val second = DeltaRemote(store, FileDeltaContentCache(directory))
        second.head()
        assertEquals(20, second.chain(head, DeltaHead()) { }.size)
        assertTrue(store.calls.count { "/blocks/" in it } in 1..19)
    }

    @Test fun corruptInlineOrDisconnectedIndexCannotBypassChainValidation() = runBlocking {
        val store = Store()
        val head = publish(store, 5)
        val saved = store.objects.getValue(DeltaRemote.HEAD)
        val root = DeltaJson.parse(saved)
        root.getAsJsonObject("readAhead").getAsJsonArray("recent")[0].asJsonObject.addProperty("inline", "{}")
        store.objects[DeltaRemote.HEAD] = root.toString()
        assertTrue(runCatching { DeltaRemote(store).head() }.isFailure)
        val disconnected = DeltaJson.parse(saved)
        disconnected.getAsJsonObject("readAhead").getAsJsonArray("recent").remove(1)
        store.objects[DeltaRemote.HEAD] = disconnected.toString()
        val reader = DeltaRemote(store)
        reader.head()
        assertTrue(runCatching { reader.chain(head, DeltaHead()) { } }.isFailure)
        store.objects[DeltaRemote.HEAD] = saved
        val wrongCursor = DeltaHead(2, "delta-v2/blocks/${"0".repeat(64)}.json")
        assertTrue(runCatching { reader.chain(head, wrongCursor) { } }.isFailure)
    }

    @Test fun repeatedChunksAreFetchedOnceAndReadsShareTheConcurrencyLimit() = runBlocking {
        val store = Store()
        val json = GSON.toJson(mapOf("value" to "x".repeat(2000000)))
        val path = "delta-v2/blocks/${DeltaRemote.checksum(json.toByteArray())}.json"
        DeltaRemote(store).write(path, json)
        store.calls.clear()
        store.delayMillis = 8
        val reader = DeltaRemote(store, FileDeltaContentCache(temporary.newFolder()))
        assertEquals(json, reader.read(path))
        val chunks = store.calls.filter { "/chunks/" in it }
        assertEquals(chunks.distinct().size, chunks.size)
        assertTrue(store.maxReads.get() in 2..3)
    }

    @Test fun cacheQuotaIsEnforcedOnlyAfterAnExplicitTrimAndRejectsTraversal() {
        val directory = temporary.newFolder()
        val cache = FileDeltaContentCache(directory, 10)
        val paths = (1..3).map { n ->
            val bytes = "payload-$n".toByteArray()
            "delta-v2/blocks/${DeltaRemote.checksum(bytes)}.json".also { cache.write(it, bytes) }
        }
        assertTrue(paths.all { cache.read(it) != null })
        cache.trim()
        assertTrue(paths.count { cache.read(it) != null } <= 1)
        assertTrue(runCatching { cache.write("../outside", byteArrayOf(1)) }.isFailure)
    }

    @Test fun failedHeadPublicationLeavesIndexPagesUnreachable() = runBlocking {
        val store = Store()
        val head = publish(store, 64)
        val writer = DeltaRemote(store)
        val previous = writer.head().first
        val root = DeltaJson.parse(previous.json!!).apply { addProperty("otherWriter", true) }
        store.objects[DeltaRemote.HEAD] = root.toString()
        val block = block(65, head, false)
        val next = DeltaHead(65, writer.writeBlock(block))
        assertFalse(writer.publish(previous, next, listOf(next to block)))
        assertTrue(store.objects.keys.any { "/indexes/" in it })
        val reader = DeltaRemote(store)
        assertEquals(head, reader.head().second)
        assertEquals(64, reader.chain(head, DeltaHead()) { }.size)
    }

    @Test fun oversizedManifestFailsBeforeReadingAnyChunks() = runBlocking {
        val store = Store()
        val path = "delta-v2/blocks/${"a".repeat(64)}.json"
        store.objects[path] = """{"format":"wanjuan-delta-chunks-1","bytes":2147483647,"sha256":"x","parts":[]}"""
        assertTrue(runCatching { DeltaRemote(store).read(path) }.isFailure)
        assertEquals(listOf(path), store.calls)
    }

    private fun block(timestamp: Long, previous: DeltaHead, large: Boolean) = DeltaBlock(timestamp, previous,
        listOf(DeltaChange("op-$timestamp", "book", "book", create = true,
            fields = listOf(DeltaField(listOf("b", "h"), value = GSON.toJsonTree(if (large) "x".repeat(12000) + timestamp else "value-$timestamp"))))))

    private suspend fun publish(store: Store, count: Int, large: Boolean = false): DeltaHead {
        val writer = DeltaRemote(store)
        var head = DeltaHead()
        repeat(count) { index ->
            val previous = writer.head().first
            val block = block(index.toLong() + 1, head, large)
            head = DeltaHead(block.timestamp, writer.writeBlock(block))
            check(writer.publish(previous, head, listOf(head to block)))
        }
        return head
    }

    private class Store : SyncRemoteStore {
        val objects = ConcurrentHashMap<String, String>()
        val calls = CopyOnWriteArrayList<String>()
        val maxReads = AtomicInteger()
        private val active = AtomicInteger()
        var delayMillis = 0L
        override suspend fun ensureDirs() = Unit
        override suspend fun list(relativeDir: String): List<SyncRemoteFile> = error("Must not list repository")
        override suspend fun uploadJson(relativePath: String, json: String) { objects[relativePath] = json }
        override suspend fun downloadJson(relativePath: String): String? {
            calls += relativePath
            val count = active.incrementAndGet()
            maxReads.updateAndGet { maxOf(it, count) }
            return try {
                if (delayMillis > 0) delay(delayMillis)
                objects[relativePath]
            } finally { active.decrementAndGet() }
        }
        override suspend fun readForUpdate(path: String) = SyncRemoteRead(downloadJson(path))
        override suspend fun compareAndSetJson(path: String, previous: SyncRemoteRead, json: String): Boolean = synchronized(objects) {
            if (objects[path] != previous.json) false else { objects[path] = json; true }
        }
    }
}
