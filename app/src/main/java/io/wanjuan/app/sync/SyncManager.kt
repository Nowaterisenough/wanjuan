package io.wanjuan.app.sync

import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.data.appDb
import io.wanjuan.app.help.AppWebDav
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.coroutine.Coroutine
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncResult
import io.wanjuan.app.sync.remote.newSyncClient
import io.wanjuan.app.sync.remote.SelectedSyncRemoteStore
import io.wanjuan.app.sync.remote.SyncSettings
import io.wanjuan.app.utils.getPrefString
import io.wanjuan.app.utils.putPrefString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx

object SyncManager {

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutationMutex = Mutex()
    private var changeObserver: Job? = null
    private var dirtyObserver: Job? = null
    private val managedTypes = SyncObjectSelection.all
    private val dirty = SyncDirtyTracker()
    private var forcedCapture = emptySet<String>()

    private val client by lazy { SelectedSyncRemoteStore(SyncSettings::target) }
    private val repository: SyncRepository by lazy {
        SyncRepository(
            db = appDb,
            client = client,
            clock = SystemSyncClock,
            onBookUploaded = { bookshelf.applyUploadedBook(it) },
            deviceIdProvider = SyncDeviceStore::deviceId
        )
    }
    private val groupCoordinator by lazy { BookGroupSyncCoordinator() }

    val bookshelf: BookshelfSyncCoordinator by lazy {
        BookshelfSyncCoordinator(
            client,
            repository,
            SystemSyncClock,
            SyncDeviceStore::deviceId,
            groupCoordinator
        )
    }
    // Large book/cache exports remain WebDAV files, separate from object synchronization.
    val assets by lazy { SyncAssetCoordinator(AppWebDav.newSyncClient()) }
    val bookSources by lazy {
        BookSourceSyncCoordinator(client, repository, SystemSyncClock, SyncDeviceStore::deviceId)
    }
    val rssSources by lazy {
        RssSourceSyncCoordinator(client, repository, SystemSyncClock, SyncDeviceStore::deviceId)
    }
    val ruleSubs by lazy {
        RuleSubSyncCoordinator(client, repository, SystemSyncClock, SyncDeviceStore::deviceId)
    }

    private val reconciler by lazy {
        SyncLocalReconciler(
            snapshotSource = RoomSyncSnapshotSource(
                appDb,
                SystemSyncClock,
                SyncDeviceStore::deviceId,
                groupCoordinator
            ),
            store = RoomSyncReconcileStore(appDb),
            clock = SystemSyncClock,
            deviceIdProvider = SyncDeviceStore::deviceId,
            managedObjectTypes = managedTypes
        )
    }

    private val pullEngine by lazy {
        SyncPullEngine(
            remoteStore = client,
            pullStore = RoomSyncPullStore(appDb),
            handlers = productionSyncPullHandlers(
                groupCoordinator,
                bookshelf,
                bookSources,
                rssSources,
                ruleSubs
            )
        )
    }

    private val orchestrator by lazy {
        SyncOrchestrator(
            remoteStore = client,
            captureAction = SyncCaptureAction {
                client.withTarget { target ->
                    if (appCtx.getPrefString("syncLastRemoteTarget") != target.identity) {
                        appDb.syncMetadataDao.resetRemoteState(managedTypes)
                        dirty.mark(managedTypes)
                        appCtx.putPrefString("syncLastRemoteTarget", target.identity)
                    }
                }
                dirty.capture(forcedCapture) { reconciler.capture(it) }
            },
            pullAction = SyncPullAction(pullEngine::pullAll),
            flushAction = SyncFlushAction { result -> repository.flushOutbox(client, result) }
        )
    }

    private val singleFlightSync by lazy {
        SingleFlightSync(syncScope, ::sync)
    }
    private val localFlushFlight by lazy { SingleFlightSync(syncScope) { runSync(flushOnly = true) } }

    suspend fun sync(): SyncResult = runSync(flushOnly = false)

    private suspend fun runSync(flushOnly: Boolean, captureCatalogChanges: Boolean = false): SyncResult = mutationMutex.withLock {
        forcedCapture = if (captureCatalogChanges) setOf(SyncObjectType.Book, SyncObjectType.BookshelfOrder) else emptySet()
        try {
            client.withTarget {
                if (flushOnly) orchestrator.flushPending() else orchestrator.sync()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncResult(failed = 1, errorMessage = e.localizedMessage ?: e.javaClass.simpleName)
        } finally {
            forcedCapture = emptySet()
        }
    }

    val isEnabled: Boolean
        get() = AppConfig.webDavObjectSync && SyncSettings.isConfigured

    suspend fun syncAwait(): SyncResult = if (isEnabled) {
        singleFlightSync.start().await()
    } else SyncResult()

    suspend fun flushPending(): SyncResult = if (isEnabled) {
        runSync(flushOnly = true, captureCatalogChanges = true)
    } else SyncResult()

    fun onAppStart() {
        observeLocalChanges()
        if (!AppConfig.webDavObjectSync) return
        syncNow()
    }

    @Synchronized
    private fun observeLocalChanges() {
        if (dirtyObserver?.isActive != true) {
            dirtyObserver = syncScope.launch {
                appDb.invalidationTracker.createFlow("books", "book_groups", "book_sources", "rssSources", "ruleSubs",
                    emitInitialState = false).collect { dirty.markTables(it) }
            }
        }
        if (changeObserver?.isActive == true) return
        changeObserver = syncScope.launch {
            SyncAutoTrigger().observe(
                fingerprints = RoomSyncChangeSource(appDb, dirty::markTables).fingerprints(),
                isEnabled = { isEnabled },
                hasPendingChanges = {
                    mutationMutex.withLock {
                        // Compare after remote apply; clean cloud changes must not echo back.
                        dirty.capture { reconciler.capture(it) }
                        appDb.syncOutboxDao.count() > 0
                    }
                },
                requestSync = { localFlushFlight.start(rerunIfActive = true) },
                onError = { AppLog.put("记录自动同步变更失败", it) }
            )
        }
    }

    fun onNetworkAvailable() {
        if (!AppConfig.webDavObjectSync) return
        syncNow()
    }

    fun syncNow(force: Boolean = false, onComplete: (SyncResult) -> Unit = {}) {
        if (!force && !isEnabled) {
            onComplete(SyncResult())
            return
        }
        val task = singleFlightSync.start()
        Coroutine.async(start = CoroutineStart.LAZY) { task.await() }
            .onSuccess { onComplete(it) }
            .onError {
                onComplete(
                    SyncResult(
                        failed = 1,
                        errorMessage = it.localizedMessage ?: it.javaClass.simpleName
                    )
                )
            }
            .start()
    }
}
