package io.wanjuan.app.lib.cronet

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CancellationException

class TestCronetSafety {
    @Test fun missingProviderDuringBuilderConstructionReturnsNull() {
        var failure: Throwable? = null
        val result = CronetSafety.initialize({ failure = it }) {
            throw RuntimeException("Unable to find any Cronet provider")
        }
        assertNull(result)
        assertTrue(failure is RuntimeException)
    }

    @Test fun missingProviderDependencyFallsBack() {
        var calls = 0
        val result = CronetSafety.request<String>(
            { throw NoClassDefFoundError("HttpEngineNativeProvider") },
            { calls++; "okhttp" }, {}
        )
        assertEquals("okhttp", result)
        assertEquals(1, calls)
    }

    @Test fun initializationFailureBeforeRequestAlsoFallsBack() {
        assertEquals("okhttp", CronetSafety.request<String>(
            { throw IllegalStateException("builder failed") }, { "okhttp" }, {}
        ))
    }

    @Test fun successfulCronetDoesNotSendDuplicateRequest() {
        assertEquals("cronet", CronetSafety.request({ "cronet" }, { error("Duplicate request") }, {}))
    }

    @Test fun unavailableEngineUsesFallbackExactlyOnce() {
        var calls = 0
        val failure = IOException("offline")
        val thrown = assertThrows(IOException::class.java) {
            CronetSafety.request<String>({ null }, { calls++; throw failure }, {})
        }
        assertSame(failure, thrown)
        assertEquals(1, calls)
    }

    @Test fun fallbackFailureRetainsOriginalCauseWithoutRetrying() {
        val cronet = NoClassDefFoundError("provider dependency")
        val fallback = IOException("offline")
        var calls = 0
        val error = assertThrows(IOException::class.java) {
            CronetSafety.request<String>({ throw cronet }, { calls++; throw fallback }, {})
        }
        assertSame(cronet, error.suppressed.single())
        assertEquals(1, calls)
    }

    @Test fun cancellationAndVmErrorsAreNotSwallowed() {
        assertThrows(CancellationException::class.java) {
            CronetSafety.request<String>({ throw CancellationException() }, { error("Must not retry") }, {})
        }
        assertThrows(OutOfMemoryError::class.java) {
            CronetSafety.initialize({ error("Must not swallow VM errors") }) { throw OutOfMemoryError() }
        }
    }
}
