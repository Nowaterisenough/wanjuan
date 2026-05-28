package io.wanjuan.app.help.update

import io.wanjuan.app.help.coroutine.Coroutine
import kotlinx.coroutines.CoroutineScope

object AppUpdate {

    val gitHubUpdate: AppUpdateInterface by lazy {
        AppUpdateGitHub
    }

    data class UpdateInfo(
        val tagName: String,
        val updateLog: String,
        val downloadUrl: String,
        val fileName: String
    )

    interface AppUpdateInterface {

        fun check(scope: CoroutineScope): Coroutine<UpdateInfo>

    }

}
