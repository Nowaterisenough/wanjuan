package io.wanjuan.app.sync.remote

import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.help.AppWebDav
import io.wanjuan.app.lib.webdav.Authorization
import io.wanjuan.app.sync.SyncCanonicalJson
import io.wanjuan.app.utils.getPrefString
import splitties.init.appCtx

object SyncSettings {
    const val WebDav = "webdav"
    const val GitHub = "github"

    val provider: String get() = appCtx.getPrefString(PreferKey.syncProvider, WebDav) ?: WebDav

    private fun githubConfig(): GitHubSyncConfig {
        val repository = appCtx.getPrefString(PreferKey.gitHubSyncRepository).orEmpty().trim().split('/')
        return GitHubSyncConfig(
            owner = repository.getOrElse(0) { "" },
            repository = if (repository.size == 2) repository[1] else "",
            token = appCtx.getPrefString(PreferKey.gitHubSyncToken).orEmpty().trim(),
            branch = appCtx.getPrefString(PreferKey.gitHubSyncBranch).orEmpty().trim(),
            directory = appCtx.getPrefString(PreferKey.gitHubSyncDir, "wanjuan").orEmpty().trim()
        )
    }

    val isConfigured: Boolean get() = when (provider) {
        WebDav -> AppWebDav.isConfigured
        GitHub -> runCatching { githubConfig().validate() }.isSuccess
        else -> false
    }

    private var cachedKey: String? = null
    private var cachedTarget: SyncRemoteTarget? = null

    @Synchronized
    fun target(): SyncRemoteTarget {
        val github = provider == GitHub
        require(provider == WebDav || github) { "不支持的同步服务器" }
        val config = if (github) githubConfig().also { it.validate() } else null
        val root = if (github) "" else AppWebDav.syncRootWebDavUrl()
        val account = if (github) "" else appCtx.getPrefString(PreferKey.webDavAccount).orEmpty()
        val password = if (github) "" else appCtx.getPrefString(PreferKey.webDavPassword).orEmpty()
        require(github || (account.isNotBlank() && password.isNotBlank())) { "WebDAV 未配置" }
        val identity = SyncCanonicalJson.hash(listOf(provider, root, account,
            config?.owner?.lowercase(), config?.repository?.lowercase(), config?.branch, config?.directory))
        val key = SyncCanonicalJson.hash(listOf(identity, password, config?.token))
        if (key == cachedKey) return requireNotNull(cachedTarget)
        val store = if (config != null) GitHubSyncClient(config) else {
            val authorization = Authorization(account, password)
            WebDavSyncClient({ root }, { authorization })
        }
        return SyncRemoteTarget(identity, store).also {
            cachedTarget = it
            cachedKey = key
        }
    }
}
