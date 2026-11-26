package io.wanjuan.app.sync

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.net.Uri
import androidx.core.net.toUri
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.constant.AppPattern
import io.wanjuan.app.constant.BookType
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.help.book.isLocal
import io.wanjuan.app.help.book.isNotShelf
import io.wanjuan.app.help.book.removeLocalUriCache
import io.wanjuan.app.model.localBook.LocalBook
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncResult
import io.wanjuan.app.sync.remote.GitHubBookFile
import io.wanjuan.app.sync.remote.GitHubSyncClient
import io.wanjuan.app.sync.remote.gitBookSha
import io.wanjuan.app.utils.FileDoc
import io.wanjuan.app.utils.inputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import splitties.init.appCtx
import java.io.File

internal object GitHubBookLibrary {
    private val prefs get() = appCtx.getSharedPreferences("github-book-library", Context.MODE_PRIVATE)
    private val fileSuffix = Regex("[a-z0-9]{1,5}")
    private val managedDir = Regex("book_[a-f0-9]{32}")

    // The journal keeps the original bookUrl across devices. Only the reader's file location varies.
    fun copyFile(book: Book): File {
        val extension = book.originName.substringAfterLast('.').lowercase()
        val suffix = if (fileSuffix.matches(extension)) extension else "book"
        return File(appCtx.filesDir, "github-books/${SyncIds.bookId(book)}.$suffix")
    }

    fun markRemote(book: Book) {
        if (book.isLocal) prefs.edit().putBoolean("remote/${SyncIds.bookId(book)}", true).apply()
    }

    fun markImported(book: Book) {
        prefs.edit().putBoolean("remote/${SyncIds.bookId(book)}", false).apply()
    }

    private fun eligible(book: Book) = book.isLocal && !book.isNotShelf &&
        (book.origin == BookType.localTag || book.origin.startsWith("${BookType.localTag}::")) &&
        AppPattern.bookFileRegex.matches(book.originName)

    private fun original(book: Book): FileDoc? {
        val trustKey = "remote/${SyncIds.bookId(book)}"
        if (prefs.getBoolean(trustKey, false)) return null
        val uri = if (book.bookUrl.startsWith('/')) Uri.fromFile(File(book.bookUrl)) else book.bookUrl.toUri()
        if (uri.scheme !in setOf("file", "content")) return null
        // Older imports have no provenance flag. Only consider shared storage or a user-granted document.
        if (!prefs.contains(trustKey)) {
            if (uri.scheme == "file" && !File(uri.path ?: return null).canonicalPath.startsWith("/storage/")) return null
            if (uri.scheme == "content" && appCtx.checkUriPermission(uri, Process.myPid(), Process.myUid(),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED) return null
        }
        return try {
            FileDoc.fromUri(uri, false).takeIf { it.name == book.originName && it.size > 0 }
        } catch (e: SecurityException) {
            null // A revoked document grant can still be restored from the repository.
        }
    }

    suspend fun sync(target: String, client: GitHubSyncClient): SyncResult {
        val entries = client.libraryFiles().associateBy { it.path }.toMutableMap()
        val books = appDb.bookDao.all.filter(::eligible)
        val plainFiles = entries.values.filterNot { managedDir.matches(it.path.substringBefore('/')) }
        val byName = plainFiles.groupBy { it.path.substringAfterLast('/') }
        val shelfNames = books.groupingBy { it.originName }.eachCount()
        val result = SyncResult.Mutable()
        suspend fun attempt(name: String, action: suspend () -> Unit) {
            currentCoroutineContext().ensureActive()
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                result.fail("$name · ${e.localizedMessage ?: "文件同步失败"}")
                AppLog.put("同步本地书失败：$name", e)
            }
        }
        for (book in books) attempt(book.originName) {
            val managed = "${SyncIds.bookId(book)}/${book.originName}"
            val known = byName[book.originName].orEmpty()
            val path = when {
                managed in entries -> managed
                book.bookUrl.startsWith("wanjuan-library://$target/") -> book.bookUrl.toUri().path!!.removePrefix("/")
                known.size == 1 && shelfNames[book.originName] == 1 -> known.single().path
                else -> managed
            }
            syncFile(target, client, book, path, entries.remove(path), result)
            if (path != managed) prefs.edit().putString("path/$target/$path", SyncIds.bookId(book)).apply()
        }
        for (entry in entries.values) attempt(entry.path) {
            // Managed backups belong to a journal record. A deleted shelf entry stays deleted.
            if (managedDir.matches(entry.path.substringBefore('/'))) return@attempt
            val priorId = prefs.getString("path/$target/${entry.path}", null)
            if (priorId != null && appDb.syncDeltaDao.get(target, SyncObjectType.Book, priorId) != null) return@attempt
            val name = entry.path.substringAfterLast('/')
            val url = Uri.Builder().scheme("wanjuan-library").authority(target)
                .apply { entry.path.split('/').forEach { appendPath(it) } }.build().toString()
            val book = Book(bookUrl = url, originName = name, type = BookType.text or BookType.local)
            if (appDb.syncDeltaDao.get(target, SyncObjectType.Book, SyncIds.bookId(book)) != null ||
                appDb.bookDao.getBook(url) != null) return@attempt
            syncFile(target, client, book, entry.path, entry, result)
            LocalBook.importLibraryBook(book, copyFile(book))
        }
        return result.toResult()
    }

    private suspend fun syncFile(target: String, client: GitHubSyncClient, book: Book, path: String,
                                 remote: GitHubBookFile?, result: SyncResult.Mutable) {
        val key = "$target/${SyncIds.bookId(book)}/$path"
        val base = prefs.getString(key, null)
        val copy = copyFile(book)
        val doc = original(book)
        val originalSha = doc?.let { file ->
            file.uri.inputStream(appCtx).getOrNull()?.use { gitBookSha(file.size, it) }
        }
        val originalChanged = originalSha != null && originalSha != prefs.getString("original/$key", null)
        val useOriginal = originalSha != null && (!copy.exists() || originalChanged)
        val local = when {
            useOriginal -> originalSha
            copy.isFile -> gitBookSha(copy)
            else -> null
        }
        val synced = when {
            local != null && local == remote?.sha -> local
            remote != null && (local == null || local == base) -> {
                download(client, book, remote, copy)
                result.downloaded++
                remote.sha
            }
            local != null && (remote == null || remote.sha == base) -> {
                upload(client, path, remote?.sha, local, if (useOriginal) doc else null, copy)
                result.uploaded++
                local
            }
            local == null && remote == null -> error("找不到本地原文件和仓库备份")
            else -> error("本地和仓库文件内容不同，请保留需要的版本后再下拉")
        }
        if (useOriginal && synced == originalSha && copy.exists()) {
            check(copy.delete()) { "无法切换到更新后的本地文件" }
            invalidate(book)
        }
        prefs.edit().putString(key, synced).putString("original/$key", originalSha).apply()
    }

    private suspend fun download(client: GitHubSyncClient, book: Book, remote: GitHubBookFile, copy: File) {
        val directory = requireNotNull(copy.parentFile)
        directory.mkdirs()
        val temp = File.createTempFile("download-", ".part", directory)
        try {
            client.downloadBook(remote, temp)
            check(temp.renameTo(copy)) { "无法保存下载的书籍" }
            invalidate(book)
        } finally {
            temp.delete()
        }
    }

    private fun invalidate(book: Book) {
        book.removeLocalUriCache()
        LocalBook.invalidateFile(book)
        appDb.bookChapterDao.delByBook(book.bookUrl)
    }

    private suspend fun upload(client: GitHubSyncClient, path: String, previous: String?, sha: String,
                               doc: FileDoc?, copy: File) {
        // Freeze a provider-backed original before the conditional write, even if another app edits it.
        val temp = doc?.let { File.createTempFile("upload-", ".book", appCtx.cacheDir) }
        try {
            if (temp != null) {
                requireNotNull(doc).uri.inputStream(appCtx).getOrThrow().use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                check(gitBookSha(temp) == sha) { "书籍在上传前发生变化，请再次下拉" }
            }
            check(client.uploadBook(path, temp ?: copy, previous) == sha) { "上传的书籍校验失败" }
        } finally {
            temp?.delete()
        }
    }
}
