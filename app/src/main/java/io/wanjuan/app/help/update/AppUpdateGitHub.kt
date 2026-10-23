package io.wanjuan.app.help.update

import androidx.annotation.Keep
import io.wanjuan.app.constant.AppConst
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.help.coroutine.Coroutine
import kotlinx.coroutines.CoroutineScope

@Keep
object AppUpdateGitHub : AppUpdate.AppUpdateInterface {
    private val releaseClient by lazy { GitHubReleaseClient() }

    override fun check(scope: CoroutineScope): Coroutine<AppUpdate.UpdateInfo?> = Coroutine.async(scope) {
        selectUpdate(releaseClient.fetchLatest(), AppConst.appInfo.versionName)
    }

    fun getChangeLog(scope: CoroutineScope): Coroutine<String> = Coroutine.async(scope) {
        releaseClient.fetchLatest().body.orEmpty()
    }

    internal fun selectUpdate(release: GithubRelease, currentVersion: String): AppUpdate.UpdateInfo? {
        if (release.isPreRelease) throw NoStackTraceException("尚未发布可用的正式版本")
        val latest = release.gitReleaseToAppReleaseInfo()
            .filter { it.appVariant == AppVariant.OFFICIAL && it.versionName.isNotBlank() }
            .maxWithOrNull { left, right -> ReleaseVersion.compare(left.versionName, right.versionName) }
            ?: throw NoStackTraceException("最新发布中没有可识别的 APK 附件，请到发布页面检查")
        if (ReleaseVersion.compare(latest.versionName, currentVersion) <= 0) return null
        return AppUpdate.UpdateInfo(latest.versionName, latest.note, latest.downloadUrl, latest.name)
    }
}
