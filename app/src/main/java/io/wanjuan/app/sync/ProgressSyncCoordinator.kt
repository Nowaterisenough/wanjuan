package io.wanjuan.app.sync

import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookProgress
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.sync.mapper.BookSyncMapper
import io.wanjuan.app.sync.merge.SyncMerge
import io.wanjuan.app.sync.model.SyncBookProgressPayload
import io.wanjuan.app.sync.remote.WebDavSyncClient
import io.wanjuan.app.utils.NetworkUtils
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class ProgressSyncCoordinator(
    private val client: WebDavSyncClient,
    private val deviceIdProvider: () -> String
) {

    suspend fun pullProgress(book: Book): BookProgress? {
        val id = SyncIds.bookId(book)
        val payload = client.download<SyncBookProgressPayload>("bookProgress/$id.json") ?: return null
        if (!SyncMerge.remoteProgressWins(book.durChapterTime, payload.progressUpdatedAt)) {
            return null
        }
        return BookProgress(
            name = payload.name,
            author = payload.author,
            durChapterIndex = payload.durChapterIndex,
            durChapterPos = payload.durChapterPos,
            durChapterTime = payload.progressUpdatedAt,
            durChapterTitle = payload.durChapterTitle
        )
    }

    suspend fun pushProgress(book: Book, toast: Boolean = false): Boolean {
        if (!AppConfig.syncBookProgress) return false
        if (!NetworkUtils.isAvailable()) return false
        return try {
            val payload = BookSyncMapper.toProgressPayload(book, deviceIdProvider())
            client.upload("bookProgress/${payload.bookSyncId}.json", payload)
            book.syncTime = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("上传进度失败\n${e.localizedMessage}", e, toast)
            false
        }
    }

    fun applyProgress(book: Book, progress: BookProgress) {
        SyncScope.remoteApply {
            book.durChapterIndex = progress.durChapterIndex
            book.durChapterPos = progress.durChapterPos
            book.durChapterTitle = progress.durChapterTitle
            book.durChapterTime = progress.durChapterTime
            appDb.bookDao.update(book)
        }
    }
}
