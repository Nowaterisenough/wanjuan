package io.wanjuan.app.sync.mapper

import io.wanjuan.app.data.entities.RuleSub
import io.wanjuan.app.sync.SyncIds
import io.wanjuan.app.sync.model.SyncRuleSub
import io.wanjuan.app.sync.model.SyncRuleSubPayload

object RuleSubSyncMapper {

    fun toPayload(ruleSub: RuleSub, deviceId: String, updatedAt: Long): SyncRuleSubPayload {
        return SyncRuleSubPayload(
            ruleSubHash = SyncIds.ruleSubId(ruleSub),
            type = ruleSub.type,
            url = ruleSub.url,
            ruleSub = SyncRuleSub.from(ruleSub),
            subscriptionUpdatedAt = updatedAt,
            updatedByDeviceId = deviceId
        )
    }
}
