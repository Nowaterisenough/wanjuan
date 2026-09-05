package io.wanjuan.app.sync

import io.wanjuan.app.sync.model.SyncResult
import io.wanjuan.app.sync.remote.SyncRemoteFile
import io.wanjuan.app.sync.remote.SyncRemoteStore
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestSyncManagerBehavior {

    @Test
    fun catalogCompletionCapturesAndUploadsWithoutRepeatingRemoteScan() = runBlocking {
        val calls = mutableListOf<String>()
        val orchestrator = SyncOrchestrator(
            remoteStore = RecordingRemote(calls),
            captureAction = SyncCaptureAction { calls += "capture" },
            pullAction = SyncPullAction { calls += "pull" },
            flushAction = SyncFlushAction { calls += "flush" }
        )

        assertTrue(orchestrator.flushPending().isSuccess)
        assertEquals(listOf("ensure", "capture", "flush"), calls)
    }

    @Test
    fun syncRunsEnsureCapturePullThenFlush() = runBlocking {
        val calls = mutableListOf<String>()
        val orchestrator = SyncOrchestrator(
            remoteStore = RecordingRemote(calls),
            captureAction = SyncCaptureAction { calls += "capture" },
            pullAction = SyncPullAction { calls += "pull" },
            flushAction = SyncFlushAction { calls += "flush" }
        )

        val result = orchestrator.sync()

        assertTrue(result.isSuccess)
        assertEquals(listOf("ensure", "capture", "pull", "flush"), calls)
    }

    @Test
    fun failedEnsureStopsPipelineAndReturnsFailure() = runBlocking {
        val calls = mutableListOf<String>()
        val orchestrator = SyncOrchestrator(
            remoteStore = RecordingRemote(calls, failEnsure = true),
            captureAction = SyncCaptureAction { calls += "capture" },
            pullAction = SyncPullAction { calls += "pull" },
            flushAction = SyncFlushAction { calls += "flush" }
        )

        val result = orchestrator.sync()

        assertFalse(result.isSuccess)
        assertEquals(1, result.failed)
        assertTrue(result.errorMessage.orEmpty().contains("not configured"))
        assertEquals(listOf("ensure"), calls)
    }

    @Test
    fun concurrentSyncCallsDoNotInterleave() = runBlocking {
        val calls = mutableListOf<String>()
        var active = 0
        var maxActive = 0
        val orchestrator = SyncOrchestrator(
            remoteStore = RecordingRemote(calls),
            captureAction = SyncCaptureAction {
                active += 1
                maxActive = maxOf(maxActive, active)
                calls += "capture"
            },
            pullAction = SyncPullAction {
                delay(10)
                calls += "pull"
            },
            flushAction = SyncFlushAction {
                calls += "flush"
                active -= 1
            }
        )

        val first = async { orchestrator.sync() }
        val second = async { orchestrator.sync() }
        first.await()
        second.await()

        assertEquals(1, maxActive)
        assertEquals(
            listOf("ensure", "capture", "pull", "flush", "ensure", "capture", "pull", "flush"),
            calls
        )
    }

    private class RecordingRemote(
        private val calls: MutableList<String>,
        private val failEnsure: Boolean = false
    ) : SyncRemoteStore {
        override suspend fun ensureDirs() {
            calls += "ensure"
            if (failEnsure) error("WebDAV not configured")
        }

        override suspend fun list(relativeDir: String): List<SyncRemoteFile> = emptyList()

        override suspend fun downloadJson(relativePath: String): String? = null

        override suspend fun uploadJson(relativePath: String, json: String) = Unit
    }
}
