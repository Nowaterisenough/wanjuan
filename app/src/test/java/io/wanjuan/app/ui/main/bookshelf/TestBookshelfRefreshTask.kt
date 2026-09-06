package io.wanjuan.app.ui.main.bookshelf

import io.wanjuan.app.sync.model.SyncResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class TestBookshelfRefreshTask {

    @Test
    fun slowCloudDoesNotBlockLocalCatalogsAndNewBooksJoinBeforeTheyFinish() = runBlocking {
        withTimeout(5_000) {
            val cloudStarted = CompletableDeferred<Unit>()
            val cloudReply = CompletableDeferred<SyncResult>()
            val localQueued = CompletableDeferred<Unit>()
            val addedQueued = CompletableDeferred<Unit>()
            val localDone = CompletableDeferred<Boolean>()
            val addedDone = CompletableDeferred<Boolean>()
            var books = listOf("local")
            val queued = arrayListOf<String>()
            val progress = arrayListOf<BookshelfRefreshTask.Progress>()
            var flushed = false
            val task = BookshelfRefreshTask(
                loadBooks = { books },
                bookKey = { it },
                queueCatalogs = { batch ->
                    queued.addAll(batch)
                    batch.map {
                        when (it) {
                            "local" -> localDone.also { localQueued.complete(Unit) }
                            else -> addedDone.also { addedQueued.complete(Unit) }
                        }
                    }
                },
                syncCloud = {
                    cloudStarted.complete(Unit)
                    cloudReply.await()
                },
                flushChanges = {
                    assertTrue(localDone.isCompleted)
                    assertTrue(addedDone.isCompleted)
                    flushed = true
                    SyncResult()
                }
            )
            val refresh = async { task.run { progress += it } }
            cloudStarted.await()
            localQueued.await()
            assertFalse(cloudReply.isCompleted)
            assertFalse(flushed)

            books = listOf("local", "new", "new")
            cloudReply.complete(SyncResult(downloaded = 1))
            addedQueued.await()
            assertFalse(localDone.isCompleted)
            addedDone.complete(true)
            assertFalse(flushed)
            localDone.complete(true)

            assertTrue(refresh.await().isSuccess)
            assertEquals(listOf("local", "new"), queued)
            assertTrue(progress.any { it.syncing && it.catalogTotal == 1 })
            assertEquals(BookshelfRefreshTask.Progress(false, 2, 2, true), progress.last())
            assertTrue(flushed)
        }
    }

    @Test
    fun cloudFailureDoesNotCancelCatalogsOrGetReportedAsSuccess() = runBlocking {
        withTimeout(5_000) {
            val localQueued = CompletableDeferred<Unit>()
            val localDone = CompletableDeferred<Boolean>()
            val cloudFailed = CompletableDeferred<Unit>()
            var flushed = false
            val task = BookshelfRefreshTask(
                loadBooks = { listOf("local") },
                bookKey = { it },
                queueCatalogs = {
                    localQueued.complete(Unit)
                    listOf(localDone)
                },
                syncCloud = {
                    localQueued.await()
                    throw IllegalStateException("Cloud unavailable")
                },
                flushChanges = { flushed = true; SyncResult() }
            )
            val refresh = async {
                task.run { if (!it.syncing) cloudFailed.complete(Unit) }
            }
            cloudFailed.await()
            assertFalse(refresh.isCompleted)
            assertFalse(localDone.isCancelled)
            localDone.complete(false)

            val result = refresh.await()
            assertFalse(result.isSuccess)
            assertEquals("Cloud unavailable", result.cloud.errorMessage)
            assertEquals(1, result.catalogFailures)
            assertTrue(flushed)
        }
    }

    @Test
    fun finalUploadIncludesPendingChangesWithoutAnotherCloudScan() = runBlocking {
        var cloudRuns = 0
        var uploads = 0
        val task = BookshelfRefreshTask<String>(
            loadBooks = { emptyList() },
            bookKey = { it },
            queueCatalogs = { error("An empty shelf should not start a catalog worker") },
            syncCloud = { cloudRuns++; SyncResult(pending = 1) },
            flushChanges = { SyncResult(pending = if (++uploads == 1) 1 else 0) }
        )

        assertTrue(task.run {}.isSuccess)
        assertEquals(1, cloudRuns)
        assertEquals(2, uploads)
    }

    @Test
    fun remainingUploadsKeepRefreshUnsuccessfulAfterBoundedRetry() = runBlocking {
        var uploads = 0
        val task = BookshelfRefreshTask<String>(
            loadBooks = { emptyList() },
            bookKey = { it },
            queueCatalogs = { emptyList() },
            syncCloud = { SyncResult() },
            flushChanges = { uploads++; SyncResult(pending = 1) }
        )

        assertFalse(task.run {}.isSuccess)
        assertEquals(2, uploads)
    }

    @Test
    fun cancellationStopsProgressReportingAndSkipsFinalUpload() = runBlocking {
        withTimeout(5_000) {
            val localQueued = CompletableDeferred<Unit>()
            val localDone = CompletableDeferred<Boolean>()
            val cloudReply = CompletableDeferred<SyncResult>()
            val progress = arrayListOf<BookshelfRefreshTask.Progress>()
            var flushed = false
            val task = BookshelfRefreshTask(
                loadBooks = { listOf("local") },
                bookKey = { it },
                queueCatalogs = {
                    localQueued.complete(Unit)
                    listOf(localDone)
                },
                syncCloud = { cloudReply.await() },
                flushChanges = { flushed = true; SyncResult() }
            )
            val refresh = async { task.run { progress += it } }
            localQueued.await()
            refresh.cancelAndJoin()
            val reportsAfterCancellation = progress.size
            localDone.complete(true)
            cloudReply.complete(SyncResult())

            assertEquals(reportsAfterCancellation, progress.size)
            assertFalse(flushed)
        }
    }
}
