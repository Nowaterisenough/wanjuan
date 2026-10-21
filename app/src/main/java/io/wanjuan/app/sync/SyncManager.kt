package io.wanjuan.app.sync

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx

object SyncManager {

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutationMutex = Mutex()
    private val managedTypes = setOf(
        SyncObjectType.BookGroup, SyncObjectType.Book, SyncObjectType.BookSource,
        SyncObjectType.RssSource, SyncObjectType.RuleSub, SyncObjectType.BookGroupOrder,
        SyncObjectType.BookshelfOrder, SyncObjectType.BookSourceOrder,
        SyncObjectType.RssSourceOrder, SyncObjectType.RuleSubOrder
    )

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
                        appCtx.putPrefString("syncLastRemoteTarget", target.identity)
                    }
                }
                reconciler.capture()
            },
            pullAction = SyncPullAction(pullEngine::pullAll),
            flushAction = SyncFlushAction { result -> repository.flushOutbox(client, result) }
        )
    }

    private val singleFlightSync by lazy {
        SingleFlightSync(syncScope, ::sync)
    }

    suspend fun sync(): SyncResult = runSync(flushOnly = false)

    private suspend fun runSync(flushOnly: Boolean): SyncResult = mutationMutex.withLock {
        try {
            client.withTarget {
                if (flushOnly) orchestrator.flushPending() else orchestrator.sync()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncResult(failed = 1, errorMessage = e.localizedMessage ?: e.javaClass.simpleName)
        }
    }

    val isEnabled: Boolean
        get() = AppConfig.webDavObjectSync && SyncSettings.isConfigured

    suspend fun syncAwait(): SyncResult = if (isEnabled) {
        singleFlightSync.start(rerunIfActive = true).await()
    } else SyncResult()

    suspend fun flushPending(): SyncResult = if (isEnabled) {
        runSync(flushOnly = true)
    } else SyncResult()

    fun onAppStart() {
        if (!AppConfig.webDavObjectSync) return
        syncNow()
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
        val task = singleFlightSync.start(rerunIfActive = true)
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
