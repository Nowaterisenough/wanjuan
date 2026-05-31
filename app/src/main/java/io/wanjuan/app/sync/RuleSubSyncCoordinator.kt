package io.wanjuan.app.sync

import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.RuleSub
import io.wanjuan.app.sync.local.SyncMetadata
import io.wanjuan.app.sync.mapper.RuleSubSyncMapper
import io.wanjuan.app.sync.merge.SyncMerge
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncOrderPayload
import io.wanjuan.app.sync.model.SyncRuleSub
import io.wanjuan.app.sync.model.SyncRuleSubPayload
import io.wanjuan.app.sync.model.SyncTombstonePayload
import io.wanjuan.app.sync.remote.WebDavSyncClient

class RuleSubSyncCoordinator(
    private val client: WebDavSyncClient,
    private val repository: SyncRepository,
    private val clock: SyncClock,
    private val deviceIdProvider: () -> String
) {

    fun enqueueRuleSub(ruleSub: RuleSub) {
        val payload = RuleSubSyncMapper.toPayload(ruleSub, deviceIdProvider(), clock.now())
        repository.markDirty(SyncObjectType.RuleSub, payload.ruleSubHash, payload, "upsert")
    }

    fun enqueueDelete(ruleSub: RuleSub) {
        repository.markDirty(SyncObjectType.RuleSub, SyncIds.ruleSubId(ruleSub), null, "delete")
    }

    fun enqueueOrder(items: List<RuleSub>) {
        val payload = SyncOrderPayload(
            updatedAt = clock.now(),
            updatedByDeviceId = deviceIdProvider(),
            items = items.sortedBy { it.customOrder }.map { SyncIds.ruleSubId(it) }
        )
        repository.markDirty(SyncObjectType.RuleSubOrder, "ruleSubs", payload, "order")
    }

    suspend fun pushSub(ruleSub: RuleSub) {
        val payload = RuleSubSyncMapper.toPayload(ruleSub, deviceIdProvider(), clock.now())
        recordLocalSubClock(payload)
        client.upload("ruleSubs/${payload.ruleSubHash}.json", payload)
    }

    suspend fun pushRuleSub(ruleSub: RuleSub) {
        pushSub(ruleSub)
    }

    fun applyRemoteSub(payload: SyncRuleSubPayload) {
        if (payload.ruleSubHash != SyncIds.hashKey("rule-sub", "${payload.type}\n${payload.url}")) return
        repository.applyRemote {
            appDb.runInTransaction {
                val dao = appDb.ruleSubDao
                val metadataDao = appDb.syncMetadataDao
                val metadata = metadataDao.get(SyncObjectType.RuleSub, payload.ruleSubHash)
                val local = dao.findByTypeAndUrl(payload.type, payload.url)
                val localUpdatedAt = maxOf(
                    metadata?.localUpdatedAt ?: 0L,
                    metadata?.remoteUpdatedAt ?: 0L
                )
                if (SyncMerge.remoteWins(payload.subscriptionUpdatedAt, metadata?.deletedAt ?: 0L)) {
                    return@runInTransaction
                }

                if (local == null || SyncMerge.remoteWins(localUpdatedAt, payload.subscriptionUpdatedAt)) {
                    val sub = payload.ruleSub.toRuleSub(
                        id = local?.id ?: 0L
                    ).apply {
                        type = payload.type
                        url = payload.url
                    }
                    if (local == null) {
                        dao.insert(sub)
                    } else {
                        dao.update(sub)
                    }
                    metadataDao.insert(
                        metadata.withRemoteClock(
                            objectId = payload.ruleSubHash,
                            remoteUpdatedAt = payload.subscriptionUpdatedAt,
                            updatedByDeviceId = payload.updatedByDeviceId
                        )
                    )
                }
            }
        }
    }

    fun applyRemoteRuleSub(payload: SyncRuleSubPayload) {
        applyRemoteSub(payload)
    }

    suspend fun pushOrder(items: List<RuleSub>) {
        val payload = SyncOrderPayload(
            updatedAt = clock.now(),
            updatedByDeviceId = deviceIdProvider(),
            items = items.sortedBy { it.customOrder }.map { SyncIds.ruleSubId(it) }
        )
        recordLocalOrderClock(payload)
        client.upload("order/ruleSubs.json", payload)
    }

    fun applyRemoteOrder(payload: SyncOrderPayload) {
        repository.applyRemote {
            appDb.runInTransaction {
                val dao = appDb.ruleSubDao
                val metadataDao = appDb.syncMetadataDao
                val metadata = metadataDao.get(SyncObjectType.RuleSubOrder, "ruleSubs")
                val localUpdatedAt = maxOf(
                    metadata?.localUpdatedAt ?: 0L,
                    metadata?.remoteUpdatedAt ?: 0L
                )
                if (!SyncMerge.remoteWins(localUpdatedAt, payload.updatedAt)) {
                    return@runInTransaction
                }

                val byId = dao.all.associateBy { SyncIds.ruleSubId(it) }
                val missingIds = payload.items.filterNot { byId.containsKey(it) }
                payload.items.forEachIndexed { index, id ->
                    byId[id]?.let { it.customOrder = index + 1 }
                }
                dao.update(*byId.values.toTypedArray())
                if (missingIds.isNotEmpty()) {
                    return@runInTransaction
                }
                metadataDao.insert(
                    metadata?.let {
                        it.copy(
                            remoteUpdatedAt = maxOf(it.remoteUpdatedAt, payload.updatedAt),
                            updatedByDeviceId = payload.updatedByDeviceId
                        )
                    } ?: SyncMetadata(
                        objectType = SyncObjectType.RuleSubOrder,
                        objectId = "ruleSubs",
                        remoteUpdatedAt = payload.updatedAt,
                        updatedByDeviceId = payload.updatedByDeviceId
                    )
                )
            }
        }
    }

    suspend fun pushDelete(ruleSub: RuleSub) {
        pushDeleteById(
            id = SyncIds.ruleSubId(ruleSub),
            objectKey = "${ruleSub.type}\n${ruleSub.url}"
        )
    }

    suspend fun pushDeleteById(id: String, objectKey: String? = null) {
        val deletedAt = clock.now()
        val deviceId = deviceIdProvider()
        recordLocalDeleteClock(
            id = id,
            deletedAt = deletedAt,
            deletedByDeviceId = deviceId
        )
        client.upload(
            "tombstones/ruleSubs/$id.json",
            SyncTombstonePayload(
                objectType = SyncObjectType.RuleSub,
                objectId = id,
                deletedAt = deletedAt,
                deletedByDeviceId = deviceId,
                objectKey = objectKey
            )
        )
    }

    fun applyRemoteDelete(payload: SyncTombstonePayload, objectKey: String? = null) {
        if (payload.objectType != SyncObjectType.RuleSub) return
        repository.applyRemote {
            appDb.runInTransaction {
                val metadataDao = appDb.syncMetadataDao
                val metadata = metadataDao.get(SyncObjectType.RuleSub, payload.objectId)
                val ruleSub = findRuleSubByKey(objectKey, payload.objectId)
                    ?: findRuleSubByKey(payload.objectKey, payload.objectId)
                    ?: findRuleSubByHash(payload.objectId)
                val objectUpdatedAt = maxOf(
                    metadata?.localUpdatedAt ?: 0L,
                    metadata?.remoteUpdatedAt ?: 0L
                )
                if (!SyncMerge.tombstoneWins(objectUpdatedAt, payload.deletedAt)) {
                    return@runInTransaction
                }
                if (ruleSub != null) {
                    appDb.ruleSubDao.delete(ruleSub)
                }
                metadataDao.insert(
                    metadata.withRemoteDelete(
                        objectId = payload.objectId,
                        deletedAt = payload.deletedAt,
                        deletedByDeviceId = payload.deletedByDeviceId
                    )
                )
            }
        }
    }

    private fun findRuleSubByHash(ruleSubHash: String): RuleSub? {
        return appDb.ruleSubDao.all
            .firstOrNull { it.url.isNotBlank() && SyncIds.ruleSubId(it) == ruleSubHash }
    }

    private fun findRuleSubByKey(objectKey: String?, ruleSubHash: String): RuleSub? {
        val key = objectKey ?: return null
        val separator = key.indexOf('\n').takeIf { it > 0 } ?: return null
        val type = key.substring(0, separator).toIntOrNull() ?: return null
        val url = key.substring(separator + 1).takeIf { it.isNotBlank() } ?: return null
        if (SyncIds.hashKey("rule-sub", "$type\n$url") != ruleSubHash) return null
        return appDb.ruleSubDao.findByTypeAndUrl(type, url)
    }

    private fun recordLocalSubClock(payload: SyncRuleSubPayload) {
        appDb.runInTransaction {
            val metadataDao = appDb.syncMetadataDao
            val metadata = metadataDao.get(SyncObjectType.RuleSub, payload.ruleSubHash)
            metadataDao.insert(
                metadata.withLocalClock(
                    objectId = payload.ruleSubHash,
                    localUpdatedAt = payload.subscriptionUpdatedAt,
                    updatedByDeviceId = payload.updatedByDeviceId
                )
            )
        }
    }

    private fun recordLocalOrderClock(payload: SyncOrderPayload) {
        appDb.runInTransaction {
            val metadataDao = appDb.syncMetadataDao
            val metadata = metadataDao.get(SyncObjectType.RuleSubOrder, "ruleSubs")
            metadataDao.insert(
                metadata?.let {
                    it.copy(
                        localUpdatedAt = maxOf(it.localUpdatedAt, payload.updatedAt),
                        updatedByDeviceId = payload.updatedByDeviceId
                    )
                } ?: SyncMetadata(
                    objectType = SyncObjectType.RuleSubOrder,
                    objectId = "ruleSubs",
                    localUpdatedAt = payload.updatedAt,
                    updatedByDeviceId = payload.updatedByDeviceId
                )
            )
        }
    }

    private fun recordLocalDeleteClock(
        id: String,
        deletedAt: Long,
        deletedByDeviceId: String
    ) {
        appDb.runInTransaction {
            val metadataDao = appDb.syncMetadataDao
            val metadata = metadataDao.get(SyncObjectType.RuleSub, id)
            metadataDao.insert(
                metadata.withLocalDelete(
                    objectId = id,
                    deletedAt = deletedAt,
                    deletedByDeviceId = deletedByDeviceId
                )
            )
        }
    }

    private fun SyncMetadata?.withRemoteClock(
        objectId: String,
        remoteUpdatedAt: Long,
        updatedByDeviceId: String
    ): SyncMetadata {
        return this?.copy(
            remoteUpdatedAt = maxOf(this.remoteUpdatedAt, remoteUpdatedAt),
            deletedAt = null,
            updatedByDeviceId = updatedByDeviceId
        ) ?: SyncMetadata(
            objectType = SyncObjectType.RuleSub,
            objectId = objectId,
            remoteUpdatedAt = remoteUpdatedAt,
            updatedByDeviceId = updatedByDeviceId
        )
    }

    private fun SyncMetadata?.withLocalClock(
        objectId: String,
        localUpdatedAt: Long,
        updatedByDeviceId: String
    ): SyncMetadata {
        return this?.copy(
            localUpdatedAt = maxOf(this.localUpdatedAt, localUpdatedAt),
            deletedAt = null,
            updatedByDeviceId = updatedByDeviceId
        ) ?: SyncMetadata(
            objectType = SyncObjectType.RuleSub,
            objectId = objectId,
            localUpdatedAt = localUpdatedAt,
            updatedByDeviceId = updatedByDeviceId
        )
    }

    private fun SyncMetadata?.withRemoteDelete(
        objectId: String,
        deletedAt: Long,
        deletedByDeviceId: String
    ): SyncMetadata {
        return this?.copy(
            remoteUpdatedAt = maxOf(this.remoteUpdatedAt, deletedAt),
            deletedAt = maxOf(this.deletedAt ?: 0L, deletedAt),
            updatedByDeviceId = deletedByDeviceId
        ) ?: SyncMetadata(
            objectType = SyncObjectType.RuleSub,
            objectId = objectId,
            remoteUpdatedAt = deletedAt,
            deletedAt = deletedAt,
            updatedByDeviceId = deletedByDeviceId
        )
    }

    private fun SyncMetadata?.withLocalDelete(
        objectId: String,
        deletedAt: Long,
        deletedByDeviceId: String
    ): SyncMetadata {
        return this?.copy(
            localUpdatedAt = maxOf(this.localUpdatedAt, deletedAt),
            deletedAt = maxOf(this.deletedAt ?: 0L, deletedAt),
            updatedByDeviceId = deletedByDeviceId
        ) ?: SyncMetadata(
            objectType = SyncObjectType.RuleSub,
            objectId = objectId,
            localUpdatedAt = deletedAt,
            deletedAt = deletedAt,
            updatedByDeviceId = deletedByDeviceId
        )
    }

    private fun SyncRuleSub.toRuleSub(id: Long): RuleSub {
        return RuleSub(
            id = id,
            name = name,
            url = url,
            type = type,
            customOrder = customOrder,
            autoUpdate = autoUpdate,
            update = update,
            updateInterval = updateInterval,
            silentUpdate = silentUpdate,
            js = js,
            showRule = showRule,
            sourceUrl = sourceUrl
        )
    }
}
