package io.wanjuan.app.ui.association

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import io.wanjuan.app.R
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.utils.toastOnUi

object OpenUrlLauncher {

    fun open(context: Context, uri: String, mimeType: String?): Boolean {
        return try {
            val targetUri = uri.toUri()
            val targetIntent = Intent(Intent.ACTION_VIEW).apply {
                if (!mimeType.isNullOrBlank()) {
                    setDataAndType(targetUri, mimeType)
                } else {
                    data = targetUri
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (targetIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(targetIntent)
                true
            } else {
                context.toastOnUi(R.string.can_not_open)
                false
            }
        } catch (e: Exception) {
            AppLog.put("打开链接失败", e, true)
            false
        }
    }
}
