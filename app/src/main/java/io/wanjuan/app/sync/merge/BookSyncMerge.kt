package io.wanjuan.app.sync.merge

import io.wanjuan.app.constant.BookType
import io.wanjuan.app.sync.SyncCanonicalJson
import io.wanjuan.app.sync.effectiveProgressUpdatedAt
import io.wanjuan.app.sync.effectiveProgressUpdatedByDeviceId
import io.wanjuan.app.sync.model.SyncBook
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.sync.model.SyncVersion

object BookSyncMerge {
    fun shelfVersion(payload: SyncBookPayload) = SyncVersion(
        payload.shelfUpdatedAt,
        payload.shelfUpdatedByDeviceId ?: payload.updatedByDeviceId
    )

    fun catalogVersion(payload: SyncBookPayload) = SyncVersion(
        payload.catalogUpdatedAt,
        payload.catalogUpdatedByDeviceId ?: payload.updatedByDeviceId
    )

    fun progressVersion(payload: SyncBookPayload) = SyncVersion(
        payload.effectiveProgressUpdatedAt(), payload.effectiveProgressUpdatedByDeviceId()
    )

    fun version(payload: SyncBookPayload): SyncVersion =
        maxOf(shelfVersion(payload), catalogVersion(payload), progressVersion(payload))

    fun merge(local: SyncBookPayload, remote: SyncBookPayload): SyncBookPayload {
        require(local.bookSyncId == remote.bookSyncId) { "Cannot merge different books" }
        val shelf = if (shelfVersion(remote) > shelfVersion(local)) remote else local
        val catalog = if (catalogVersion(remote) > catalogVersion(local)) remote else local
        val progress = if (progressVersion(remote) > progressVersion(local)) remote else local
        return shelf.copy(
            book = shelf.book.withCatalog(catalog.book).withProgress(progress.book).copy(
                syncTime = progressVersion(progress).timestamp,
                order = local.book.order
            ),
            catalogUpdatedAt = catalog.catalogUpdatedAt,
            progressUpdatedAt = progressVersion(progress).timestamp,
            shelfUpdatedByDeviceId = shelfVersion(shelf).deviceId,
            catalogUpdatedByDeviceId = catalogVersion(catalog).deviceId,
            progressUpdatedByDeviceId = progressVersion(progress).deviceId
        )
    }

    fun shelfHash(book: SyncBook): String = SyncCanonicalJson.hash(
        book.withCatalog(SyncBook()).withProgress(SyncBook()).copy(
            group = 0L, order = 0, groupSyncIds = book.groupSyncIds.sorted()
        )
    )

    fun catalogHash(book: SyncBook): String =
        SyncCanonicalJson.hash(SyncBook().withCatalog(book))

    fun progressHash(book: SyncBook): String =
        SyncCanonicalJson.hash(SyncBook().withProgress(book).copy(durChapterTime = 0L))

    private fun SyncBook.withCatalog(other: SyncBook): SyncBook = copy(
        tocUrl = other.tocUrl,
        latestChapterTitle = other.latestChapterTitle,
        latestChapterTime = other.latestChapterTime,
        lastCheckTime = other.lastCheckTime,
        lastCheckCount = other.lastCheckCount,
        totalChapterNum = other.totalChapterNum,
        type = (type and BookType.updateError.inv()) or (other.type and BookType.updateError)
    )

    private fun SyncBook.withProgress(other: SyncBook): SyncBook = copy(
        durChapterTitle = other.durChapterTitle,
        durChapterIndex = other.durChapterIndex,
        durVolumeIndex = other.durVolumeIndex,
        chapterInVolumeIndex = other.chapterInVolumeIndex,
        durChapterPos = other.durChapterPos,
        durChapterTime = other.durChapterTime,
        syncTime = other.syncTime
    )
}
