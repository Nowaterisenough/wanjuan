package io.wanjuan.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RuleSubSyncSourceTest {

    private val source = File("app/src/main/java/io/wanjuan/app/sync/RuleSubSyncCoordinator.kt").readText()
    private val dao = File("app/src/main/java/io/wanjuan/app/data/dao/RuleSubDao.kt").readText()

    @Test
    fun ruleSubDaoHasTypeAndUrlLookup() {
        assertTrue(dao.contains("where type = :type and url = :url limit 1"))
        assertTrue(dao.contains("fun findByTypeAndUrl(type: Int, url: String): RuleSub?"))
    }

    @Test
    fun ruleSubSyncUsesTypeAndUrlAndNoRssSourceConfig() {
        assertTrue(source.contains("ruleSubs/"))
        assertTrue(source.contains("order/ruleSubs.json"))
        assertTrue(source.contains("tombstones/ruleSubs/"))
        assertTrue(source.contains("findByTypeAndUrl(payload.type, payload.url)"))
        assertTrue(source.contains("items.sortedBy { it.customOrder }.map { SyncIds.ruleSubId(it) }"))
        assertTrue(source.contains("fun applyRemoteOrder(payload: SyncOrderPayload)"))
        assertTrue(source.contains("SyncObjectType.RuleSubOrder"))
        assertTrue(source.contains("payload.items.forEachIndexed"))
        assertTrue(source.contains("missingIds.isNotEmpty()"))
        assertTrue(source.contains("subscriptionUpdatedAt"))
        assertFalse(source.contains("rssSourceUpdatedAt"))
        assertFalse(source.contains("rssSources/"))
        assertFalse(source.contains("Rss"))
    }

    @Test
    fun ruleSubSyncMapsDtoBackToRoomEntity() {
        assertTrue(source.contains("SyncRuleSub"))
        assertTrue(source.contains("payload.ruleSub.toRuleSub("))
        assertTrue(source.contains("name = name"))
        assertTrue(source.contains("url = url"))
        assertTrue(source.contains("type = type"))
        assertTrue(source.contains("customOrder = customOrder"))
        assertTrue(source.contains("update = update"))
        assertTrue(source.contains("sourceUrl = sourceUrl"))
        assertTrue(source.contains("dao.insert(sub)"))
        assertTrue(source.contains("dao.update(sub)"))
    }

    @Test
    fun ruleSubSyncUsesSubscriptionTimestampNotRuleSubUpdateForConflicts() {
        assertTrue(source.contains("payload.ruleSubHash != SyncIds.hashKey(\"rule-sub\""))
        assertTrue(source.contains("payload.type"))
        assertTrue(source.contains("payload.url"))
        assertTrue(source.contains("metadata?.localUpdatedAt ?: 0L"))
        assertTrue(source.contains("metadata?.remoteUpdatedAt ?: 0L"))
        assertTrue(source.contains("SyncMerge.remoteWins(localUpdatedAt, payload.subscriptionUpdatedAt)"))
        assertTrue(source.contains("remoteUpdatedAt = payload.subscriptionUpdatedAt"))
        assertFalse(source.contains("local?.update"))
        assertFalse(source.contains("local.update"))
    }

    @Test
    fun ruleSubDeleteUsesTombstoneConflictSemantics() {
        assertTrue(source.contains("SyncTombstonePayload("))
        assertTrue(source.contains("objectType = SyncObjectType.RuleSub"))
        assertTrue(source.contains("if (payload.objectType != SyncObjectType.RuleSub) return"))
        assertTrue(source.contains("SyncMerge.tombstoneWins(objectUpdatedAt, payload.deletedAt)"))
        assertTrue(source.contains("recordLocalDeleteClock"))
        assertTrue(source.contains("objectKey = objectKey"))
        assertTrue(source.contains("payload.objectKey"))
        assertTrue(source.contains("findRuleSubByKey"))
        assertTrue(source.contains("withRemoteDelete"))
    }
}
