package io.wanjuan.app.sync

import io.wanjuan.app.data.appDb
import io.wanjuan.app.help.AppWebDav
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.coroutine.Coroutine
import io.wanjuan.app.sync.remote.newSyncClient

object SyncManager {

    private val client by lazy { AppWebDav.newSyncClient() }
    private val repository by lazy {
        SyncRepository(
            db = appDb,
            client = client,
            clock = SystemSyncClock,
            deviceIdProvider = SyncDeviceStore::deviceId
        )
    }

    val progress by lazy { ProgressSyncCoordinator(client, SyncDeviceStore::deviceId) }
    val bookshelf by lazy {
        BookshelfSyncCoordinator(client, repository, SystemSyncClock, SyncDeviceStore::deviceId)
    }
    val bookSources by lazy {
        BookSourceSyncCoordinator(client, repository, SystemSyncClock, SyncDeviceStore::deviceId)
    }
    val ruleSubs by lazy {
        RuleSubSyncCoordinator(client, repository, SystemSyncClock, SyncDeviceStore::deviceId)
    }

    fun onAppStart() {
        if (!AppConfig.syncBookProgress) return
        Coroutine.async {
            repository.ensureRemoteReady()
        }
    }

    fun onNetworkAvailable() {
        Coroutine.async {
            repository.ensureRemoteReady()
        }
    }

    fun syncNow() {
        Coroutine.async {
            repository.ensureRemoteReady()
        }
    }
}
