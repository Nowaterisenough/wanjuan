package io.wanjuan.app.sync

import io.wanjuan.app.data.appDb
import io.wanjuan.app.help.AppWebDav
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.coroutine.Coroutine
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.sync.model.SyncBookSourcePayload
import io.wanjuan.app.sync.model.SyncDeleteKeyPayload
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncOrderPayload
import io.wanjuan.app.sync.model.SyncRuleSubPayload
import io.wanjuan.app.sync.remote.newSyncClient
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject

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
            flushOutbox()
        }
    }

    fun onNetworkAvailable() {
        Coroutine.async {
            repository.ensureRemoteReady()
            flushOutbox()
        }
    }

    fun syncNow() {
        Coroutine.async {
            repository.ensureRemoteReady()
            flushOutbox()
        }
    }

    private suspend fun flushOutbox() {
        repository.flushOutbox { item ->
            when (item.objectType) {
                SyncObjectType.Book -> {
                    if (item.operation == "delete") {
                        bookshelf.pushBookDeleteById(item.objectId)
                    } else {
                        bookshelf.pushBookPayload(item.requirePayload())
                    }
                }
                SyncObjectType.BookSource -> {
                    if (item.operation == "delete") {
                        bookSources.pushDeleteById(
                            id = item.objectId,
                            sourceUrl = item.payload<SyncDeleteKeyPayload>()?.key
                        )
                    } else {
                        val payload = item.requirePayload<SyncBookSourcePayload>()
                        client.upload("bookSources/${payload.sourceHash}.json", payload)
                    }
                }
                SyncObjectType.RuleSub -> {
                    if (item.operation == "delete") {
                        ruleSubs.pushDeleteById(item.objectId)
                    } else {
                        val payload = item.requirePayload<SyncRuleSubPayload>()
                        client.upload("ruleSubs/${payload.ruleSubHash}.json", payload)
                    }
                }
                SyncObjectType.BookshelfOrder -> {
                    client.upload("order/bookshelf.json", item.requirePayload<SyncOrderPayload>())
                }
                SyncObjectType.BookSourceOrder -> {
                    client.upload("order/bookSources.json", item.requirePayload<SyncOrderPayload>())
                }
                SyncObjectType.RuleSubOrder -> {
                    client.upload("order/ruleSubs.json", item.requirePayload<SyncOrderPayload>())
                }
            }
        }
    }

    private inline fun <reified T> io.wanjuan.app.sync.local.SyncOutbox.payload(): T? {
        return payloadJson?.let { GSON.fromJsonObject<T>(it).getOrNull() }
    }

    private inline fun <reified T> io.wanjuan.app.sync.local.SyncOutbox.requirePayload(): T {
        return payload() ?: error("Invalid sync outbox payload: $objectType/$objectId")
    }
}
