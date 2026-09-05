package io.wanjuan.app.sync

import io.wanjuan.app.data.appDb
import io.wanjuan.app.help.AppWebDav
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.coroutine.Coroutine
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncResult
import io.wanjuan.app.sync.remote.newSyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object SyncManager {

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client by lazy { AppWebDav.newSyncClient() }
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
    val assets by lazy { SyncAssetCoordinator(client) }
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
            managedObjectTypes = setOf(
                SyncObjectType.BookGroup,
                SyncObjectType.Book,
                SyncObjectType.BookSource,
                SyncObjectType.RssSource,
                SyncObjectType.RuleSub,
                SyncObjectType.BookGroupOrder,
                SyncObjectType.BookshelfOrder,
                SyncObjectType.BookSourceOrder,
                SyncObjectType.RssSourceOrder,
                SyncObjectType.RuleSubOrder
            )
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
            captureAction = SyncCaptureAction { reconciler.capture() },
            pullAction = SyncPullAction(pullEngine::pullAll),
            flushAction = SyncFlushAction { result -> repository.flushOutbox(client, result) }
        )
    }

    private val singleFlightSync by lazy {
        SingleFlightSync(syncScope, ::sync)
    }

    suspend fun sync(): SyncResult = orchestrator.sync()

    fun onAppStart() {
        if (!AppConfig.webDavObjectSync) return
        syncNow()
    }

    fun onNetworkAvailable() {
        if (!AppConfig.webDavObjectSync) return
        syncNow()
    }

    fun syncNow(force: Boolean = false, onComplete: (SyncResult) -> Unit = {}) {
        if (!force && (!AppConfig.webDavObjectSync || !AppWebDav.isConfigured)) {
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
