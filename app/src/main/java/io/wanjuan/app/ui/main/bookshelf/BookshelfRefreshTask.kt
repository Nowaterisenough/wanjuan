package io.wanjuan.app.ui.main.bookshelf

import io.wanjuan.app.sync.model.SyncResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class BookshelfRefreshTask<T>(
    private val loadBooks: suspend () -> List<T>,
    private val bookKey: (T) -> String,
    private val queueCatalogs: (List<T>) -> List<Deferred<Boolean>>,
    private val syncCloud: suspend () -> SyncResult,
    private val flushChanges: suspend () -> SyncResult
) {
    data class Progress(
        val syncing: Boolean = true,
        val catalogCompleted: Int = 0,
        val catalogTotal: Int = 0,
        val uploading: Boolean = false
    )

    data class Result(val cloud: SyncResult, val upload: SyncResult, val catalogFailures: Int) {
        val isSuccess: Boolean
            get() = cloud.failed == 0 && upload.isSuccess && catalogFailures == 0
    }

    suspend fun run(onProgress: suspend (Progress) -> Unit): Result = coroutineScope {
        val progressMutex = Mutex()
        var progress = Progress()
        suspend fun report(update: (Progress) -> Progress) = progressMutex.withLock {
            progress = update(progress)
            onProgress(progress)
        }

        suspend fun updateCatalogs(books: List<T>): Int = coroutineScope {
            val completions = if (books.isEmpty()) emptyList() else queueCatalogs(books)
            report { it.copy(catalogTotal = it.catalogTotal + completions.size) }
            completions.map { completion ->
                async {
                    val success = completion.await()
                    report { it.copy(catalogCompleted = it.catalogCompleted + 1) }
                    success
                }
            }.awaitAll().count { !it }
        }

        val requestedBooks = hashSetOf<String>()
        val initialBooks = loadBooks().filter { requestedBooks.add(bookKey(it)) }
        report { it }
        val cloud = async {
            syncSafely(syncCloud).also { report { it.copy(syncing = false) } }
        }
        val catalogs = async { updateCatalogs(initialBooks) }
        val cloudResult = cloud.await()
        // Include newly synced books without refreshing the initial batch twice.
        val addedBooks = loadBooks().filter { requestedBooks.add(bookKey(it)) }
        val addedFailures = updateCatalogs(addedBooks)
        val catalogFailures = catalogs.await() + addedFailures
        report { it.copy(uploading = true) }
        var upload = syncSafely(flushChanges)
        if (upload.failed == 0 && upload.pending > 0) upload = syncSafely(flushChanges)
        Result(cloudResult, upload, catalogFailures)
    }

    private suspend fun syncSafely(action: suspend () -> SyncResult): SyncResult = try {
        action()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        SyncResult(failed = 1, errorMessage = e.localizedMessage ?: e.javaClass.simpleName)
    }
}
