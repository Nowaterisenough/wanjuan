package io.wanjuan.app.help

import android.net.Uri
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
import io.wanjuan.app.lib.webdav.WebDavFile
import io.wanjuan.app.model.remote.RemoteBookWebDav
import io.wanjuan.app.utils.AlphanumComparator
import io.wanjuan.app.utils.FileUtils
import io.wanjuan.app.utils.NetworkUtils
import io.wanjuan.app.utils.UrlUtil
import io.wanjuan.app.utils.compress.ZipUtils
import io.wanjuan.app.utils.getPrefString
import io.wanjuan.app.utils.normalizeFileName
import io.wanjuan.app.utils.removePref
import io.wanjuan.app.utils.toastOnUi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import splitties.init.appCtx
import java.io.File

/**
 * webDav初始化会访问网络,不要放到主线程
 */
object AppWebDav {
    private const val defaultWebDavUrl = "https://dav.jianguoyun.com/dav/"
    private val exportsWebDavUrl get() = "${rootWebDavUrl}books/"
    private val bgWebDavUrl get() = "${rootWebDavUrl}background/"
    private val themesWebDavUrl get() = "${rootWebDavUrl}themes/"
    private val navigationBarsWebDavUrl get() = "${rootWebDavUrl}navigationBars/"

    var authorization: Authorization? = null
        private set

    var defaultBookWebDav: RemoteBookWebDav? = null

    val isOk get() = authorization != null

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
        kotlin.runCatching {
            authorization = null
            defaultBookWebDav = null
            val account = appCtx.getPrefString(PreferKey.webDavAccount)
            val password = appCtx.getPrefString(PreferKey.webDavPassword)
            if (!account.isNullOrEmpty() && !password.isNullOrEmpty()) {
                val mAuthorization = Authorization(account, password)
                checkAuthorization(mAuthorization)
                WebDav(rootWebDavUrl, mAuthorization).makeAsDir()
                WebDav(exportsWebDavUrl, mAuthorization).makeAsDir()
                WebDav(bgWebDavUrl, mAuthorization).makeAsDir()
                WebDav(themesWebDavUrl, mAuthorization).makeAsDir()
                WebDav(navigationBarsWebDavUrl, mAuthorization).makeAsDir()
                val rootBooksUrl = "${rootWebDavUrl}books/"
                defaultBookWebDav = RemoteBookWebDav(rootBooksUrl, mAuthorization)
                authorization = mAuthorization
            }
        }
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
        authorization?.let {
            var files = WebDav(rootWebDavUrl, it).listFiles()
            files = files.sortedWith { o1, o2 ->
                AlphanumComparator.compare(o1.displayName, o2.displayName)
            }.reversed()
            files.forEach { webDav ->
                val name = webDav.displayName
                if (name.startsWith("backup")) {
                    names.add(name)
                }
            }
        } ?: throw NoStackTraceException("webDav没有配置")
        return names
    }

    @Throws(WebDavException::class)
    suspend fun restoreWebDav(name: String) {
        authorization?.let {
            val webDav = WebDav(rootWebDavUrl + name, it)
            webDav.downloadTo(Backup.zipFilePath, true)
            FileUtils.delete(Backup.backupPath)
            ZipUtils.unZipToPath(File(Backup.zipFilePath), Backup.backupPath)
            Restore.restoreLocked(Backup.backupPath)
        }
    }

    suspend fun hasBackUp(backUpName: String): Boolean {
        authorization?.let {
            val url = "$rootWebDavUrl${backUpName}"
            return WebDav(url, it).exists()
        }
        return false
    }

    suspend fun lastBackUp(): Result<WebDavFile?> {
        return kotlin.runCatching {
            authorization?.let {
                var lastBackupFile: WebDavFile? = null
                WebDav(rootWebDavUrl, it).listFiles().reversed().forEach { webDavFile ->
                    if (webDavFile.displayName.startsWith("backup")) {
                        if (lastBackupFile == null
                            || webDavFile.lastModify > lastBackupFile.lastModify
                        ) {
                            lastBackupFile = webDavFile
                        }
                    }
                }
                lastBackupFile
            }
        }
    }

    /**
     * webDav备份
     * @param fileName 备份文件名
     */
    @Throws(Exception::class)
    suspend fun backUpWebDav(fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        authorization?.let {
            val putUrl = "$rootWebDavUrl$fileName"
            WebDav(putUrl, it).upload(Backup.zipFilePath)
        }
    }

    suspend fun listThemePackages(isNightTheme: Boolean): List<WebDavFile> {
        val authorization = authorization ?: return emptyList()
        if (!NetworkUtils.isAvailable()) return emptyList()
        val dirUrl = getThemeTypeUrl(isNightTheme)
        WebDav(dirUrl, authorization).makeAsDir()
        return WebDav(dirUrl, authorization).listFiles()
            .filter { !it.isDir && it.displayName.endsWith(".zip", ignoreCase = true) }
    }

    suspend fun uploadThemePackage(isNightTheme: Boolean, remoteDirName: String, zipFile: File) {
        val authorization = authorization ?: throw NoStackTraceException("webDav未配置")
        if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络未连接")
        val fileName = "${remoteDirName.trimEnd('/').removeSuffix(".zip")}.zip"
        val typeUrl = getThemeTypeUrl(isNightTheme)
        WebDav(typeUrl, authorization).makeAsDir()
        WebDav(typeUrl + fileName, authorization).upload(zipFile)
    }

    suspend fun uploadCachePackage(fileName: String, zipFile: File) {
        val authorization = authorization ?: throw NoStackTraceException("webDav未配置")
        if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络未连接")
        val safeFileName = UrlUtil.replaceReservedChar(
            fileName.trimEnd('/').removeSuffix(".zip").normalizeFileName()
        ).ifBlank { "cache_${System.currentTimeMillis()}" }
        WebDav(exportsWebDavUrl, authorization).makeAsDir()
        WebDav(exportsWebDavUrl + safeFileName + ".zip", authorization)
            .upload(zipFile, "application/zip")
    }

    suspend fun downloadThemePackage(isNightTheme: Boolean, remoteDirName: String, zipFile: File) {
        val authorization = authorization ?: throw NoStackTraceException("webDav未配置")
        if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络未连接")
        val fileName = "${remoteDirName.trimEnd('/').removeSuffix(".zip")}.zip"
        zipFile.parentFile?.mkdirs()
        WebDav(getThemeTypeUrl(isNightTheme) + fileName, authorization)
            .downloadTo(zipFile.absolutePath, true)
    }

    suspend fun deleteThemePackage(isNightTheme: Boolean, remoteDirName: String) {
        val authorization = authorization ?: throw NoStackTraceException("webDav未配置")
        if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络未连接")
        val fileName = "${remoteDirName.trimEnd('/').removeSuffix(".zip")}.zip"
        WebDav(getThemeTypeUrl(isNightTheme) + fileName, authorization).delete()
    }

    suspend fun listNavigationBarPackages(isNightTheme: Boolean): List<WebDavFile> {
        val authorization = authorization ?: return emptyList()
        if (!NetworkUtils.isAvailable()) return emptyList()
        val dirUrl = getNavigationBarTypeUrl(isNightTheme)
        WebDav(dirUrl, authorization).makeAsDir()
        return WebDav(dirUrl, authorization).listFiles()
            .filter { !it.isDir && it.displayName.endsWith(".zip", ignoreCase = true) }
    }

    suspend fun uploadNavigationBarPackage(isNightTheme: Boolean, remoteDirName: String, zipFile: File) {
        val authorization = authorization ?: throw NoStackTraceException("webDav未配置")
        if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络未连接")
        val fileName = "${remoteDirName.trimEnd('/').removeSuffix(".zip")}.zip"
        val typeUrl = getNavigationBarTypeUrl(isNightTheme)
        WebDav(typeUrl, authorization).makeAsDir()
        WebDav(typeUrl + fileName, authorization).upload(zipFile)
    }

    suspend fun downloadNavigationBarPackage(isNightTheme: Boolean, remoteDirName: String, zipFile: File) {
        val authorization = authorization ?: throw NoStackTraceException("webDav未配置")
        if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络未连接")
        val fileName = "${remoteDirName.trimEnd('/').removeSuffix(".zip")}.zip"
        zipFile.parentFile?.mkdirs()
        WebDav(getNavigationBarTypeUrl(isNightTheme) + fileName, authorization)
            .downloadTo(zipFile.absolutePath, true)
    }

    suspend fun deleteNavigationBarPackage(isNightTheme: Boolean, remoteDirName: String) {
        val authorization = authorization ?: throw NoStackTraceException("webDav未配置")
        if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络未连接")
        val fileName = "${remoteDirName.trimEnd('/').removeSuffix(".zip")}.zip"
        WebDav(getNavigationBarTypeUrl(isNightTheme) + fileName, authorization).delete()
    }

    private fun getThemeTypeUrl(isNightTheme: Boolean): String {
        return themesWebDavUrl + if (isNightTheme) "night/" else "day/"
    }

    private fun getNavigationBarTypeUrl(isNightTheme: Boolean): String {
        return navigationBarsWebDavUrl + if (isNightTheme) "night/" else "day/"
    }

    /**
     * 获取云端所有背景名称
     */
    private suspend fun getAllBgWebDavFiles(): Result<List<WebDavFile>> {
        return kotlin.runCatching {
            if (!NetworkUtils.isAvailable())
                throw NoStackTraceException("网络未连接")
            authorization.let {
                it ?: throw NoStackTraceException("webDav未配置")
                WebDav(bgWebDavUrl, it).listFiles()
            }
        }
    }

    /**
     * 上传背景图片
     */
    suspend fun upBgs(files: Array<File>) {
        val authorization = authorization ?: return
        if (!NetworkUtils.isAvailable()) return
        val bgWebDavFiles = getAllBgWebDavFiles().getOrThrow()
            .map { it.displayName }
            .toSet()
        files.forEach {
            if (!bgWebDavFiles.contains(it.name) && it.exists()) {
                WebDav("$bgWebDavUrl${it.name}", authorization)
                    .upload(it)
            }
        }
    }

    /**
     * 下载背景图片
     */
    suspend fun downBgs() {
        val authorization = authorization ?: return
        if (!NetworkUtils.isAvailable()) return
        val bgWebDavFiles = getAllBgWebDavFiles().getOrThrow()
            .map { it.displayName }
            .toSet()
    }

    @Suppress("unused")
    suspend fun exportWebDav(byteArray: ByteArray, fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        try {
            authorization?.let {
                // 如果导出的本地文件存在,开始上传
                val putUrl = exportsWebDavUrl + fileName
                WebDav(putUrl, it).upload(byteArray, "text/plain")
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("WebDav导出失败\n${e.localizedMessage}", e, true)
        }
    }

    suspend fun exportWebDav(uri: Uri, fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        try {
            authorization?.let {
                // 如果导出的本地文件存在,开始上传
                val putUrl = exportsWebDavUrl + fileName
                WebDav(putUrl, it).upload(uri, "text/plain")
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("WebDav导出失败\n${e.localizedMessage}", e, true)
        }
    }

}
