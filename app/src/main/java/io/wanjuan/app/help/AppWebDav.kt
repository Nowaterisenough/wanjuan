package io.wanjuan.app.help

import io.wanjuan.app.R
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.storage.Backup
import io.wanjuan.app.help.storage.Restore
import io.wanjuan.app.lib.webdav.Authorization
import io.wanjuan.app.lib.webdav.WebDav
import io.wanjuan.app.lib.webdav.WebDavException
import io.wanjuan.app.sync.remote.WebDavSyncClient
import io.wanjuan.app.utils.AlphanumComparator
import io.wanjuan.app.utils.FileUtils
import io.wanjuan.app.utils.NetworkUtils
import io.wanjuan.app.utils.compress.ZipUtils
import io.wanjuan.app.utils.getPrefString
import io.wanjuan.app.utils.removePref
import io.wanjuan.app.utils.toastOnUi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx
import java.io.File

/**
 * webDav初始化会访问网络,不要放到主线程
 */
object AppWebDav {
    private const val defaultWebDavUrl = "https://dav.jianguoyun.com/dav/"
    private val configMutex = Mutex()

    @Volatile
    var authorization: Authorization? = null
        private set

    @Volatile
    private var configError: String? = null

    val isOk get() = authorization != null

    val isConfigured: Boolean
        get() = !appCtx.getPrefString(PreferKey.webDavAccount).isNullOrBlank() &&
            !appCtx.getPrefString(PreferKey.webDavPassword).isNullOrBlank()

    val isJianGuoYun get() = rootWebDavUrl.startsWith(defaultWebDavUrl, true)

    fun syncRootWebDavUrl(): String {
        return rootWebDavUrl
    }

    init {
        runBlocking {
            upConfig()
        }
    }

    private val rootWebDavUrl: String
        get() {
            val configUrl = appCtx.getPrefString(PreferKey.webDavUrl)?.trim()
            var url = if (configUrl.isNullOrEmpty()) defaultWebDavUrl else configUrl
            if (!url.endsWith("/")) url = "${url}/"
            AppConfig.webDavDir?.trim()?.let {
                if (it.isNotEmpty()) {
                    url = "${url}${it}/"
                }
            }
            return url
        }

    suspend fun upConfig() {
        configMutex.withLock {
            configError = null
            kotlin.runCatching {
                authorization = null
                val account = appCtx.getPrefString(PreferKey.webDavAccount)
                val password = appCtx.getPrefString(PreferKey.webDavPassword)
                if (!account.isNullOrBlank() && !password.isNullOrBlank()) {
                    val mAuthorization = Authorization(account, password)
                    checkAuthorization(mAuthorization)
                    val root = rootWebDavUrl
                    try {
                        WebDav(root, mAuthorization).makeAsDir()
                        WebDavSyncClient(
                            rootUrlProvider = { root },
                            authorizationProvider = { mAuthorization }
                        ).ensureDirs()
                    } finally {
                        // Verified credentials remain usable after a transient directory failure.
                        // Callers waiting in requireAuthorization can then retry the operation.
                        authorization = mAuthorization
                    }
                }
            }.onFailure {
                currentCoroutineContext().ensureActive()
                configError = it.localizedMessage ?: it.javaClass.simpleName
                AppLog.put("WebDAV配置初始化失败\n$configError", it)
            }
        }
    }

    internal suspend fun requireAuthorization(): Authorization {
        authorization?.let { return it }
        upConfig()
        return authorization ?: throw NoStackTraceException(configError ?: "WebDAV 未配置")
    }

    @Throws(WebDavException::class)
    private suspend fun checkAuthorization(authorization: Authorization) {
        if (!WebDav(rootWebDavUrl, authorization).check()) {
            appCtx.removePref(PreferKey.webDavPassword)
            appCtx.toastOnUi(R.string.webdav_application_authorization_error)
            throw WebDavException(appCtx.getString(R.string.webdav_application_authorization_error))
        }
    }

    @Throws(Exception::class)
    suspend fun getBackupNames(): ArrayList<String> {
        val names = arrayListOf<String>()
        val authorization = requireAuthorization()
        var files = WebDav(rootWebDavUrl, authorization).listFiles()
        files = files.sortedWith { o1, o2 ->
            AlphanumComparator.compare(o1.displayName, o2.displayName)
        }.reversed()
        files.forEach { webDav ->
            val name = webDav.displayName
            if (name.startsWith("backup")) {
                names.add(name)
            }
        }
        return names
    }

    @Throws(WebDavException::class)
    suspend fun restoreWebDav(name: String) {
        val webDav = WebDav(rootWebDavUrl + name, requireAuthorization())
        webDav.downloadTo(Backup.zipFilePath, true)
        FileUtils.delete(Backup.backupPath)
        ZipUtils.unZipToPath(File(Backup.zipFilePath), Backup.backupPath)
        Restore.restoreLocked(Backup.backupPath)
    }

    /**
     * webDav备份
     * @param fileName 备份文件名
     */
    @Throws(Exception::class)
    suspend fun backUpWebDav(fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        val putUrl = "$rootWebDavUrl$fileName"
        WebDav(putUrl, requireAuthorization()).upload(Backup.zipFilePath)
    }

}
