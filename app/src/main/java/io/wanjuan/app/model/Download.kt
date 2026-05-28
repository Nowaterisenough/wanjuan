package io.wanjuan.app.model

import android.content.Context
import io.wanjuan.app.constant.IntentAction
import io.wanjuan.app.service.DownloadService
import io.wanjuan.app.utils.startService

object Download {


    fun start(context: Context, url: String, fileName: String) {
        context.startService<DownloadService> {
            action = IntentAction.start
            putExtra("url", url)
            putExtra("fileName", fileName)
        }
    }

}