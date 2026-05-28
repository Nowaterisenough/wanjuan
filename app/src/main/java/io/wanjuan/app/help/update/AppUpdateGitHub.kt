package io.wanjuan.app.help.update

import androidx.annotation.Keep
import io.wanjuan.app.BuildConfig
import io.wanjuan.app.constant.AppConst
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.help.coroutine.Coroutine
import io.wanjuan.app.help.http.newCallResponse
import io.wanjuan.app.help.http.okHttpClient
import io.wanjuan.app.help.http.text
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject
import kotlinx.coroutines.CoroutineScope

@Keep
@Suppress("unused")
object AppUpdateGitHub : AppUpdate.AppUpdateInterface {

    private suspend fun getLatestRelease(): List<AppReleaseInfo> {
        val releaseUrl = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest"
        val res = okHttpClient.newCallResponse {
            url(releaseUrl)
        }
        if (!res.isSuccessful) {
            throw NoStackTraceException("获取新版本出错(${res.code})")
        }
        val body = res.body.text()
        if (body.isBlank()) {
            throw NoStackTraceException("获取新版本出错")
        }
        return GSON.fromJsonObject<GithubRelease>(body)
            .getOrElse {
                throw NoStackTraceException("获取新版本出错 " + it.localizedMessage)
            }
            .gitReleaseToAppReleaseInfo()
            .filter { it.appVariant == AppVariant.OFFICIAL }
            .sortedByDescending { it.createdAt }
    }

    override fun check(scope: CoroutineScope): Coroutine<AppUpdate.UpdateInfo> {
        return Coroutine.async(scope) {
            getLatestRelease()
                .firstOrNull { it.versionName > AppConst.appInfo.versionName }
                ?.let {
                    return@async AppUpdate.UpdateInfo(
                        it.versionName,
                        it.note,
                        it.downloadUrl,
                        it.name
                    )
                }
                ?: throw NoStackTraceException("已是最新版本")
        }.timeout(10000)
    }

    fun getChangeLog(scope: CoroutineScope): Coroutine<String> {
        return Coroutine.async(scope) {
            val releaseUrl = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest"
            val res = okHttpClient.newCallResponse {
                url(releaseUrl)
            }
            if (!res.isSuccessful) {
                throw NoStackTraceException("获取更新日志失败(${res.code})")
            }
            val body = res.body.text()
            if (body.isBlank()) {
                throw NoStackTraceException("获取更新日志失败")
            }
            GSON.fromJsonObject<GithubRelease>(body)
                .getOrElse {
                    throw NoStackTraceException("解析更新日志失败: " + it.localizedMessage)
                }
                .body
                .orEmpty()
        }.timeout(10000)
    }
}
