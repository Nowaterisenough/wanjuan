package io.wanjuan.app.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class TestReadingProgressUpload {
    @Test fun protectionStartsBeforeSaveAndUploadWaitsForDatabase() = runBlocking {
        val saved = CompletableDeferred<Unit>()
        val events = arrayListOf<String>()
        val task = startReadingProgressUpload(this, saved,
            protect = { events += "protected" }, upload = { events += "uploaded" },
            onFailure = { throw AssertionError(it) })
        yield()
        assertEquals(listOf("protected"), events)
        events += "saved"
        saved.complete(Unit)
        task.join()
        assertEquals(listOf("protected", "saved", "uploaded"), events)
    }

    @Test fun failedSaveDoesNotUploadStaleProgress() = runBlocking {
        val saved = CompletableDeferred<Unit>()
        val errors = arrayListOf<Exception>()
        var uploaded = false
        val task = startReadingProgressUpload(this, saved, {}, { uploaded = true }, { errors += it })
        saved.completeExceptionally(IOException("save failed"))
        task.join()
        assertFalse(uploaded)
        assertEquals("save failed", errors.single().message)
    }

    @Test fun cancelledProtectionDoesNotDiscardTheDatabaseSave() = runBlocking {
        val saved = CompletableDeferred<Unit>()
        var uploaded = false
        val task = startReadingProgressUpload(this, saved, {}, { uploaded = true }, { throw AssertionError(it) })
        task.cancelAndJoin()
        assertFalse(saved.isCancelled)
        saved.complete(Unit)
        assertFalse(uploaded)
    }

    @Test fun deniedForegroundServiceStillUsesNormalUploadQueue() = runBlocking {
        var uploaded = false
        val errors = arrayListOf<Exception>()
        startReadingProgressUpload(this, CompletableDeferred(Unit),
            { throw IllegalStateException("foreground denied") }, { uploaded = true }, { errors += it }).join()
        assertTrue(uploaded)
        assertEquals(1, errors.size)
    }
}
