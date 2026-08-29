package io.wanjuan.app.lib.webdav

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TestWebDavDeleteStatus {

    @Test
    fun missingDeleteTargetIsAlreadySuccessful() {
        assertTrue(isSuccessfulWebDavDeleteStatus(204))
        assertTrue(isSuccessfulWebDavDeleteStatus(404))
        assertFalse(isSuccessfulWebDavDeleteStatus(401))
        assertFalse(isSuccessfulWebDavDeleteStatus(500))
    }

    @Test
    fun onlyNotFoundMeansResourceIsMissing() {
        assertTrue(webDavResourceStatus(200) == WebDavResourceStatus.EXISTS)
        assertTrue(webDavResourceStatus(207) == WebDavResourceStatus.EXISTS)
        assertTrue(webDavResourceStatus(404) == WebDavResourceStatus.MISSING)
        assertTrue(webDavResourceStatus(401) == WebDavResourceStatus.ERROR)
        assertTrue(webDavResourceStatus(500) == WebDavResourceStatus.ERROR)
    }

    @Test
    fun transientIoFailureIsRetriedOnce() = runBlocking {
        var attempts = 0
        var retries = 0

        val result = retryWebDavIoRequest(onRetry = { retries += 1 }) {
            attempts += 1
            if (attempts == 1) throw IOException("stale connection")
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, attempts)
        assertEquals(1, retries)
    }

    @Test
    fun persistentIoFailureStopsAfterRetryBudget() {
        var attempts = 0

        assertThrows(IOException::class.java) {
            runBlocking {
                retryWebDavIoRequest(maxRetries = 1) {
                    attempts += 1
                    throw IOException("server closed connection")
                }
            }
        }

        assertEquals(2, attempts)
    }
}
