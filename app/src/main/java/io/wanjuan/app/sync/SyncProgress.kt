package io.wanjuan.app.sync

import io.wanjuan.app.sync.model.SyncResult

enum class SyncPhase { Idle, Connecting, Capturing, CheckingIndex, ReadingChanges, Applying, Uploading, Complete, Failed }

data class SyncProgress(
    val phase: SyncPhase = SyncPhase.Idle,
    val completed: Int = 0,
    val total: Int = 0,
    val downloaded: Int = 0,
    val reused: Int = 0,
    val uploaded: Int = 0,
    val failed: Int = 0,
    val pending: Int = 0,
    val elapsedMillis: Long = 0,
    val errorMessage: String? = null
) {
    val running: Boolean get() = phase != SyncPhase.Idle && phase != SyncPhase.Complete && phase != SyncPhase.Failed
}

class SyncProgressTracker(private val publish: (SyncProgress) -> Unit = {}) {
    private var state = SyncProgress()
    private var readCompleted = 0
    private var applyCompleted = 0
    private var readTotal = 0
    private var startedAt = 0L

    private fun elapsed() = if (startedAt == 0L) 0L else (System.nanoTime() - startedAt) / 1_000_000L

    @Synchronized
    fun begin() {
        readCompleted = 0
        applyCompleted = 0
        readTotal = 0
        startedAt = System.nanoTime()
        state = SyncProgress(SyncPhase.Connecting)
        publish(state)
    }

    @Synchronized
    fun phase(phase: SyncPhase, completed: Int = 0, total: Int = 0) {
        state = state.copy(phase = phase, completed = completed, total = total, elapsedMillis = elapsed())
        publish(state)
    }

    @Synchronized
    fun reading(total: Int) {
        readTotal = total
        phase(SyncPhase.ReadingChanges, readCompleted, total)
    }

    @Synchronized
    fun extraRead() { readTotal++ }

    @Synchronized
    fun read(fromCache: Boolean, downloaded: Boolean = !fromCache) {
        readCompleted++
        state = state.copy(phase = SyncPhase.ReadingChanges, completed = readCompleted, total = readTotal,
            elapsedMillis = elapsed(),
            downloaded = state.downloaded + if (downloaded) 1 else 0,
            reused = state.reused + if (fromCache) 1 else 0)
        publish(state)
    }

    @Synchronized
    fun applied() {
        applyCompleted++
        phase(SyncPhase.Applying, applyCompleted)
    }

    @Synchronized
    fun upload(completed: Int, total: Int, uploaded: Int, failed: Int) {
        state = state.copy(phase = SyncPhase.Uploading, completed = completed, total = total,
            elapsedMillis = elapsed(),
            uploaded = uploaded, failed = failed)
        publish(state)
    }

    @Synchronized
    fun finish(result: SyncResult) {
        state = state.copy(phase = if (result.isSuccess) SyncPhase.Complete else SyncPhase.Failed,
            uploaded = result.uploaded, failed = result.failed, pending = result.pending,
            elapsedMillis = elapsed(), errorMessage = result.errorMessage)
        publish(state)
    }
}
