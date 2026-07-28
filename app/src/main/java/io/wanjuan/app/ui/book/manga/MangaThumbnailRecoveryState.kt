package io.wanjuan.app.ui.book.manga

import io.wanjuan.app.ui.book.manga.entities.MangaPage

internal data class MangaThumbnailPageKey(
    val chapterIndex: Int,
    val pageIndex: Int,
    val imageUrl: String,
)

internal fun MangaPage.thumbnailKeyIfCurrent(
    currentChapterIndex: Int,
    currentImageUrls: List<String>,
): MangaThumbnailPageKey? {
    if (chapterIndex != currentChapterIndex ||
        currentImageUrls.getOrNull(index) != mImageUrl
    ) {
        return null
    }
    return MangaThumbnailPageKey(chapterIndex, index, mImageUrl)
}

internal class MangaThumbnailRecoveryState {
    private var currentChapterIndex: Int? = null
    private var currentImageUrls: List<String> = emptyList()
    private val failedKeys = mutableSetOf<MangaThumbnailPageKey>()
    private val pendingBodyReadyKeys = linkedSetOf<MangaThumbnailPageKey>()
    private val activeCacheRecoveryKeys = mutableSetOf<MangaThumbnailPageKey>()

    fun updatePages(chapterIndex: Int, imageUrls: List<String>) {
        currentChapterIndex = chapterIndex
        currentImageUrls = imageUrls.toList()
        failedKeys.retainAll { isCurrent(it) }
        pendingBodyReadyKeys.retainAll { isCurrent(it) }
        activeCacheRecoveryKeys.retainAll { isCurrent(it) }
    }

    fun keyAt(pageIndex: Int): MangaThumbnailPageKey? {
        val chapterIndex = currentChapterIndex ?: return null
        val imageUrl = currentImageUrls.getOrNull(pageIndex) ?: return null
        return MangaThumbnailPageKey(chapterIndex, pageIndex, imageUrl)
    }

    fun isCurrent(key: MangaThumbnailPageKey): Boolean =
        key.chapterIndex == currentChapterIndex &&
            currentImageUrls.getOrNull(key.pageIndex) == key.imageUrl

    fun canLoadInBackground(key: MangaThumbnailPageKey): Boolean =
        isCurrent(key) &&
            key !in failedKeys &&
            key !in pendingBodyReadyKeys &&
            key !in activeCacheRecoveryKeys

    fun markThumbnailFailed(key: MangaThumbnailPageKey) {
        pendingBodyReadyKeys -= key
        activeCacheRecoveryKeys -= key
        if (isCurrent(key)) {
            failedKeys += key
        }
    }

    fun markThumbnailReady(key: MangaThumbnailPageKey) {
        failedKeys -= key
        pendingBodyReadyKeys -= key
        activeCacheRecoveryKeys -= key
    }

    fun markBodyImageReady(key: MangaThumbnailPageKey) {
        if (isCurrent(key) && key in activeCacheRecoveryKeys) {
            failedKeys -= key
            return
        }
        pendingBodyReadyKeys.removeAll {
            it.chapterIndex == key.chapterIndex && it.pageIndex == key.pageIndex
        }
        pendingBodyReadyKeys += key
        if (isCurrent(key)) failedKeys -= key
    }

    fun cacheRecoveryCandidates(
        availableSlots: Int = Int.MAX_VALUE,
        priorityPageIndex: Int? = null,
    ): List<MangaThumbnailPageKey> =
        if (availableSlots <= 0) {
            emptyList()
        } else {
            pendingBodyReadyKeys.filter(::isCurrent)
                .sortedWith(
                    if (priorityPageIndex == null) {
                        compareBy { it.pageIndex }
                    } else {
                        compareBy<MangaThumbnailPageKey> {
                            kotlin.math.abs(it.pageIndex - priorityPageIndex)
                        }.thenBy { it.pageIndex }
                    }
                )
                .take(availableSlots)
        }

    fun markCacheRecoveryStarted(key: MangaThumbnailPageKey) {
        if (isCurrent(key)) {
            failedKeys -= key
            activeCacheRecoveryKeys += key
        }
        pendingBodyReadyKeys -= key
    }

    fun markCacheRecoveryCancelled(key: MangaThumbnailPageKey) {
        activeCacheRecoveryKeys -= key
        if (isCurrent(key)) {
            failedKeys -= key
            pendingBodyReadyKeys += key
        }
    }
}
