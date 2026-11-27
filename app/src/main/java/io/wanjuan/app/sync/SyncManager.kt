package io.wanjuan.app.sync

import io.wanjuan.app.constant.AppLog
import android.content.SharedPreferences
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.utils.defaultSharedPreferences
import io.wanjuan.app.BuildConfig
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookProgress
import io.wanjuan.app.help.AppWebDav
import io.wanjuan.app.help.book.isNotShelf
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.coroutine.Coroutine
import io.wanjuan.app.service.BookshelfRefreshService
import io.wanjuan.app.sync.delta.*
import io.wanjuan.app.sync.mapper.progressSyncTime
import io.wanjuan.app.sync.model.SyncResult
import io.wanjuan.app.sync.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx
import java.io.File

object SyncManager {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutationMutex = Mutex()
    private var changeObserver: Job? = null
    private var retryJob: Job? = null
    private var retryDelayMillis = 5_000L
    private val mutableProgress = MutableStateFlow(SyncProgress())
    val progress = mutableProgress.asStateFlow()
    private val bookshelfRefreshActive = MutableStateFlow(false)
    val bookProgress = combine(progress, bookshelfRefreshActive) { state, refreshing ->
        if (refreshing) emptyMap() else state.books
    }.stateIn(syncScope, SharingStarted.Eagerly, emptyMap())

    // Engines are used under mutationMutex; a new gesture/target never inherits a prior CAS base.
    private class RefreshSession {
        var target: SyncRemoteTarget? = null
        var engine: DeltaEngine? = null
    }
    @Volatile private var refreshSession: RefreshSession? = null

    fun setBookshelfRefreshActive(active: Boolean) {
        refreshSession = if (active) RefreshSession() else null
        bookshelfRefreshActive.value = active
    }
    private val progressTracker = SyncProgressTracker { mutableProgress.value = it }
    private val client by lazy { SelectedSyncRemoteStore { SyncSettings.target() } }
    private val repository by lazy {
        SyncRepository(appDb, client, SystemSyncClock, deviceIdProvider = SyncDeviceStore::deviceId)
    }
    private val groups by lazy { BookGroupSyncCoordinator() }
    val bookshelf by lazy {
        BookshelfSyncCoordinator(client, repository, SystemSyncClock, SyncDeviceStore::deviceId, groups,
            unifiedPullProgress = ::pullBookProgress, unifiedPushProgress = ::publishBookProgress)
    }
    val assets by lazy { SyncAssetCoordinator(AppWebDav.newSyncClient()) }
    val bookSources by lazy { BookSourceSyncCoordinator(repository, SystemSyncClock, SyncDeviceStore::deviceId) }
    val rssSources by lazy { RssSourceSyncCoordinator(repository, SystemSyncClock, SyncDeviceStore::deviceId) }
    val ruleSubs by lazy { RuleSubSyncCoordinator(repository, SystemSyncClock, SyncDeviceStore::deviceId) }
    private val snapshots by lazy { RoomSyncSnapshotSource(appDb, SystemSyncClock, SyncDeviceStore::deviceId, groups) }
    private val handlers by lazy { productionSyncPullHandlers(groups, bookshelf, bookSources, rssSources, ruleSubs) }
    private val local by lazy { RoomDeltaLocal(appDb,
        RoomSyncSnapshotSource(appDb, SystemSyncClock, SyncDeviceStore::deviceId, groups, trackBookVersions = false),
        bookshelf, handlers) }

    private fun engine(target: SyncRemoteTarget, reusePullForUpload: Boolean = false) = DeltaEngine(local, DeltaRemote(target.store,
        FileDeltaContentCache(File(appCtx.cacheDir, "sync-delta/${target.identity}"))), target.identity,
        SystemSyncClock, legacy = {
            val cached = CachingSyncRemoteStore(target.store,
                FileSyncRemoteContentCache(File(appCtx.cacheDir, "sync-objects/${target.identity}")))
            LegacySyncImporter(cached, RoomSyncPullStore(appDb), handlers, prepare = {
                SyncLocalReconciler(snapshots, RoomSyncReconcileStore(appDb), SystemSyncClock,
                    SyncDeviceStore::deviceId, SyncObjectSelection.all).capture()
            }, progress = progressTracker).read()
        }, progress = progressTracker, reusePullForUpload = reusePullForUpload)

    private fun syncEngine(target: SyncRemoteTarget): DeltaEngine {
        val session = refreshSession ?: return engine(target)
        if (session.target === target) session.engine?.let { return it }
        return engine(target, reusePullForUpload = true).also {
            session.target = target
            session.engine = it
        }
    }

    private val pullFlight by lazy { SingleFlightSync(syncScope) { runSync(pushChanges = false) } }
    private val changeFlight by lazy { SingleFlightSync(syncScope) { runSync(pushChanges = true) } }

    val isEnabled: Boolean get() = AppConfig.webDavObjectSync && SyncSettings.isConfigured

    /** Lifecycle, network and manual requests only pull the journal after the saved cursor. */
    suspend fun sync(): SyncResult = runSync(pushChanges = false)
    suspend fun syncAwait(): SyncResult = if (isEnabled) pullFlight.start().await() else SyncResult()
    suspend fun flushPending(): SyncResult = if (isEnabled) changeFlight.start(rerunIfActive = true).await() else SyncResult()

    /** Only a shelf gesture transfers original files. Capture newly imported shelf entries in the final flush. */
    suspend fun pullBookshelfWithFiles(): SyncResult {
        if (SyncSettings.provider != SyncSettings.GitHub) return syncAwait()
        if (!isEnabled) return SyncResult()
        return runSync(pushChanges = false, includeBookFiles = true)
    }

    private val aiSettings by lazy { GitHubAiConfigSync(appCtx.defaultSharedPreferences) }
    private var aiSyncDebounce: Job? = null
    private val aiSettingsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if ((key in AiSyncSettings.keys || key == PreferKey.gitHubAiSyncPassphrase) &&
            !prefs.getString(PreferKey.gitHubAiSyncPassphrase, null).isNullOrBlank() &&
            SyncSettings.provider == SyncSettings.GitHub && isEnabled) {
            aiSyncDebounce?.cancel()
            aiSyncDebounce = syncScope.launch {
                delay(1500)
                if (SyncSettings.provider == SyncSettings.GitHub && isEnabled) pullFlight.start(rerunIfActive = true)
            }
        }
    }

    private suspend fun SyncResult.withFiles(action: suspend () -> SyncResult): SyncResult {
        val other = try { action() } catch (e: CancellationException) { throw e } catch (e: Exception) {
            SyncResult(failed = 1, errorMessage = e.localizedMessage ?: e.javaClass.simpleName)
        }
        return copy(uploaded = uploaded + other.uploaded, downloaded = downloaded + other.downloaded,
            failed = failed + other.failed, errorMessage = errorMessage ?: other.errorMessage)
    }

    private suspend fun runSync(pushChanges: Boolean, includeBookFiles: Boolean = false): SyncResult = mutationMutex.withLock {
        val heartbeat = syncScope.launch {
            var lastReport = 0L
            while (isActive) {
                delay(1000)
                progressTracker.tick()
                val state = progressTracker.snapshot()
                if (state.running && state.elapsedMillis - lastReport >= 30_000) {
                    lastReport = state.elapsedMillis
                    AppLog.put("Sync v2 ${state.phase}: ${state.completed}/${state.total}, " +
                        "downloaded=${state.downloaded}, cached=${state.reused}, phaseMs=${state.phaseElapsedMillis}, elapsed=${state.elapsedMillis / 1000}s")
                }
            }
        }
        try {
            var repeatChanges = false
            var targetId: String? = null
            val result = progressTracker.track(onFailure = { e ->
                AppLog.put("Sync v2 failed at ${progressTracker.snapshot().phase}: ${e.localizedMessage}", e)
                SyncResult(failed = 1, errorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                    pending = runCatching { targetId?.let { appDb.syncDeltaDao.pendingCount(it) } ?: 0 }.getOrDefault(0))
            }) {
                client.withTarget { target ->
                    targetId = target.identity
                    val cursor = local.cursor(target.identity)
                    AppLog.put("Sync v2 ${BuildConfig.VERSION_NAME}: ${if (pushChanges) "push" else "pull"}, " +
                        "target=${target.identity.takeLast(8)}, cursor=${cursor.timestamp}, " +
                        "legacyImported=${cursor.legacyImported}, pending=${appDb.syncDeltaDao.pendingCount(target.identity)}")
                    val delta = syncEngine(target)
                    val completed = if (pushChanges) delta.flush() else delta.pull().copy(pending = 0)
                    // Flush has already captured changes and counted the remaining queue.
                    // Writes after that transaction wake the Room observer, even during a flush.
                    repeatChanges = isEnabled && !bookshelfRefreshActive.value &&
                        (if (pushChanges) completed.pending > 0 else delta.capture() > 0)
                    val github = target.store as? GitHubSyncClient
                    if (pushChanges || github == null || completed.failed != 0) return@withTarget completed
                    val withSettings = completed.withFiles { aiSettings.sync(target.identity, github) }
                    if (includeBookFiles) withSettings.withFiles { GitHubBookLibrary.sync(target.identity, github) }
                    else withSettings
                }
            }
            AppLog.put("Sync v2 finished: uploaded=${result.uploaded}, downloaded=${result.downloaded}, " +
                "pending=${result.pending}, failed=${result.failed}, phaseMs=${progressTracker.phaseTimings()}")
            // Retry real, persisted edits after connectivity returns; a pull itself creates no upload.
            if (result.failed == 0) {
                retryJob?.cancel()
                retryDelayMillis = 5_000L
                if (repeatChanges && !bookshelfRefreshActive.value) changeFlight.start(rerunIfActive = true)
            } else if (result.pending > 0 && isEnabled) {
                retryJob?.cancel()
                val wait = retryDelayMillis
                retryDelayMillis = minOf(retryDelayMillis * 2, 300_000L)
                val failedTarget = targetId
                retryJob = syncScope.launch {
                    delay(wait)
                    if (isEnabled && runCatching { SyncSettings.target().identity == failedTarget }.getOrDefault(false)) {
                        changeFlight.start(rerunIfActive = true)
                    }
                }
            }
            result
        } finally {
            heartbeat.cancel()
        }
    }

    fun onAppStart() {
        appCtx.defaultSharedPreferences.registerOnSharedPreferenceChangeListener(aiSettingsListener)
        observeLocalChanges()
        syncNow()
    }

    @Synchronized
    private fun observeLocalChanges() {
        if (changeObserver?.isActive == true) return
        changeObserver = syncScope.launch {
            appDb.invalidationTracker.createFlow("books", "book_groups", "book_sources", "rssSources", "ruleSubs")
                .afterBookshelfRefresh(bookshelfRefreshActive).collect {
                    if (!isEnabled || bookshelfRefreshActive.value) return@collect
                    try {
                        val changed = mutationMutex.withLock {
                            if (bookshelfRefreshActive.value) return@withLock false
                            client.withTarget { target ->
                                if (!local.cursor(target.identity).legacyImported) false
                                else engine(target).capture() > 0
                            }
                        }
                        if (changed) changeFlight.start(rerunIfActive = true)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLog.put("Capture incremental sync changes failed", e)
                    }
                }
        }
    }

    fun onNetworkAvailable() = syncNow()

    /** Called on the main thread from the reader's onPause, before it leaves the foreground. */
    fun finishReading(saved: Deferred<Unit>) {
        if (!isEnabled) return
        startReadingProgressUpload(syncScope, saved,
            protect = { BookshelfRefreshService.track(appCtx, it) },
            upload = { flushPending() },
            onFailure = { AppLog.put("保存后同步阅读进度失败", it) })
    }

    fun syncNow(force: Boolean = false, onComplete: (SyncResult) -> Unit = {}) {
        if ((!force && !isEnabled) || !SyncSettings.isConfigured) {
            onComplete(SyncResult())
            return
        }
        val task = pullFlight.start()
        Coroutine.async(start = CoroutineStart.LAZY) { task.await() }
            .onSuccess { onComplete(it) }
            .onError { onComplete(SyncResult(failed = 1, errorMessage = it.localizedMessage)) }
            .start()
    }

    private suspend fun pullBookProgress(book: Book): BookProgress? {
        if (!isEnabled || book.isNotShelf) return null
        val result = syncAwait()
        check(result.failed == 0) { result.errorMessage ?: "Pull failed" }
        val current = appDb.bookDao.getBook(book.bookUrl) ?: return null
        if (current.durChapterIndex == book.durChapterIndex && current.durChapterPos == book.durChapterPos) return null
        return BookProgress(current.name, current.author, current.durChapterIndex, current.durChapterPos,
            current.progressSyncTime(), current.durChapterTitle)
    }

    private suspend fun publishBookProgress(book: Book, force: Boolean): Boolean {
        if (!isEnabled || book.isNotShelf) return false
        appDb.runInTransaction {
            val current = appDb.bookDao.getBook(book.bookUrl) ?: return@runInTransaction
            if (current.isNotShelf || SyncIds.bookId(current) != SyncIds.bookId(book)) return@runInTransaction
            if (!force && current.progressSyncTime() > book.progressSyncTime()) return@runInTransaction
            if (current.durChapterIndex != book.durChapterIndex || current.durChapterPos != book.durChapterPos) {
                current.durChapterIndex = book.durChapterIndex
                current.durChapterPos = book.durChapterPos
                current.durChapterTitle = book.durChapterTitle
                current.durVolumeIndex = book.durVolumeIndex
                current.chapterInVolumeIndex = book.chapterInVolumeIndex
                current.syncTime = maxOf(book.progressSyncTime(), SystemSyncClock.now())
                appDb.bookDao.update(current)
            }
        }
        return flushPending().isSuccess
    }
}
