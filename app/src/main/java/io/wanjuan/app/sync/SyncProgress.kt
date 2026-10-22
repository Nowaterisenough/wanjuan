package io.wanjuan.app.sync

import io.wanjuan.app.sync.model.SyncResult

enum class SyncPhase {
    Idle, Connecting, Capturing, CheckingIndex, CheckingCursor,
    MigratingPreparing, MigratingIndex, MigratingReading, MigratingApplying,
    ReadingChanges, Applying, PreparingUpload, Uploading, Publishing, Confirming, Complete, Failed
}

data class BookSyncProgress(val fraction: Float = 0f, val failed: Boolean = false)

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
    val errorMessage: String? = null,
    val phaseElapsedMillis: Long = 0,
    val books: Map<String, BookSyncProgress> = emptyMap()
) {
    val running: Boolean get() = phase != SyncPhase.Idle && phase != SyncPhase.Complete && phase != SyncPhase.Failed
}

class SyncProgressTracker(private val publish: (SyncProgress) -> Unit = {}) {
    private var state = SyncProgress()
    private var readCompleted = 0
    private var applyCompleted = 0
    private var readTotal = 0
    private var startedAt = 0L
    private var phaseStartedAt = 0L
    private val phaseMillis = linkedMapOf<SyncPhase, Long>()

    private fun elapsed() = if (startedAt == 0L) 0L else (System.nanoTime() - startedAt) / 1_000_000L

    private fun emit(next: SyncProgress) {
        val now = elapsed()
        if (next.phase != state.phase) {
            if (state.running) phaseMillis[state.phase] = (phaseMillis[state.phase] ?: 0) + now - phaseStartedAt
            phaseStartedAt = now
        }
        state = next.copy(elapsedMillis = now, phaseElapsedMillis = now - phaseStartedAt)
        publish(state)
    }

    @Synchronized
    fun phaseTimings(): Map<SyncPhase, Long> = phaseMillis.toMutableMap().apply {
        if (state.running) put(state.phase, (get(state.phase) ?: 0) + elapsed() - phaseStartedAt)
    }

    @Synchronized
    fun pendingBooks(ids: Set<String>) {
        val books = ids.associateWith { state.books[it] ?: BookSyncProgress() }
        if (books != state.books) emit(state.copy(books = books))
    }

    @Synchronized
    fun booksStage(ids: Collection<String>, fraction: Float) {
        val books = state.books.toMutableMap()
        ids.forEach { if (it in books) books[it] = BookSyncProgress(fraction.coerceIn(0f, 0.95f)) }
        emit(state.copy(books = books))
    }

    @Synchronized
    fun begin() {
        readCompleted = 0
        applyCompleted = 0
        readTotal = 0
        startedAt = System.nanoTime()
        phaseStartedAt = 0
        phaseMillis.clear()
        state = SyncProgress(SyncPhase.Connecting, books = state.books)
        publish(state)
    }

    @Synchronized
    fun phase(phase: SyncPhase, completed: Int = 0, total: Int = 0) {
        emit(state.copy(phase = phase, completed = completed, total = total))
    }

    @Synchronized
    fun snapshot(): SyncProgress = state

    @Synchronized
    fun tick() {
        if (!state.running) return
        emit(state)
    }

    @Synchronized
    fun migration(next: SyncProgress) {
        val phase = when (next.phase) {
            SyncPhase.CheckingIndex -> SyncPhase.MigratingIndex
            SyncPhase.ReadingChanges -> SyncPhase.MigratingReading
            SyncPhase.Applying -> SyncPhase.MigratingApplying
            else -> SyncPhase.MigratingPreparing
        }
        emit(next.copy(phase = phase, books = state.books))
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
        emit(state.copy(phase = SyncPhase.ReadingChanges, completed = readCompleted, total = readTotal,
            downloaded = state.downloaded + if (downloaded) 1 else 0,
            reused = state.reused + if (fromCache) 1 else 0))
    }

    @Synchronized
    fun applied() {
        applyCompleted++
        phase(SyncPhase.Applying, applyCompleted)
    }

    @Synchronized
    fun upload(completed: Int, total: Int, uploaded: Int, failed: Int, phase: SyncPhase = SyncPhase.Uploading) {
        emit(state.copy(phase = phase, completed = completed, total = total,
            uploaded = uploaded, failed = failed))
    }

    @Synchronized
    fun finish(result: SyncResult) {
        emit(state.copy(phase = if (result.isSuccess) SyncPhase.Complete else SyncPhase.Failed,
            uploaded = result.uploaded, failed = result.failed, pending = result.pending,
            books = if (result.failed > 0) state.books.mapValues { it.value.copy(failed = true) } else state.books,
            errorMessage = result.errorMessage))
    }
}
