package io.wanjuan.app.sync

import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.utils.getPrefString
import io.wanjuan.app.utils.putPrefString
import splitties.init.appCtx
import java.util.UUID

object SyncDeviceStore {
    private const val KEY = "webDavSyncDeviceId"

    fun deviceId(): String {
        val existing = appCtx.getPrefString(KEY)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        appCtx.putPrefString(KEY, created)
        return created
    }

    fun deviceName(): String {
        return appCtx.getPrefString(PreferKey.webDavDeviceName).orEmpty()
            .ifBlank { android.os.Build.MODEL ?: "Android" }
    }
}
