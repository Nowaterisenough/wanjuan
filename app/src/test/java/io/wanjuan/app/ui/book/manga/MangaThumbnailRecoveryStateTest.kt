package io.wanjuan.app.ui.book.manga

import io.wanjuan.app.ui.book.manga.entities.MangaPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaThumbnailRecoveryStateTest {

    @Test
    fun failedThumbnailIsBlockedFromImmediateBackgroundRetry() {
        val state = MangaThumbnailRecoveryState()
        val key = MangaThumbnailPageKey(66, 81, "https://img.example/82.jpg")
        state.updatePages(66, pageUrls(168))

        state.markThumbnailFailed(key)

        assertFalse(state.canLoadInBackground(key))
        assertEquals(emptyList<MangaThumbnailPageKey>(), state.cacheRecoveryCandidates())
    }

    @Test
    fun bodyReadyMakesFailedThumbnailEligibleForOneCacheRecovery() {
        val state = MangaThumbnailRecoveryState()
        val key = MangaThumbnailPageKey(66, 81, "https://img.example/82.jpg")
        state.updatePages(66, pageUrls(168))
        state.markThumbnailFailed(key)

        state.markBodyImageReady(key)

        assertEquals(listOf(key), state.cacheRecoveryCandidates())
        assertFalse(state.canLoadInBackground(key))
        state.markCacheRecoveryStarted(key)
        assertEquals(emptyList<MangaThumbnailPageKey>(), state.cacheRecoveryCandidates())
        assertFalse(state.canLoadInBackground(key))
        state.markThumbnailReady(key)
        assertTrue(state.canLoadInBackground(key))
        assertTrue(state.isCurrent(key))
    }

    @Test
    fun bodyReadyBeforePagesWaitsForMatchingPageList() {
        val state = MangaThumbnailRecoveryState()
        val key = MangaThumbnailPageKey(66, 81, "https://img.example/82.jpg")

        state.markBodyImageReady(key)
        assertEquals(emptyList<MangaThumbnailPageKey>(), state.cacheRecoveryCandidates())

        state.updatePages(66, pageUrls(168))

        assertEquals(listOf(key), state.cacheRecoveryCandidates())
    }

    @Test
    fun staleChapterAndWrongUrlNeverBecomeRecoveryCandidates() {
        val state = MangaThumbnailRecoveryState()
        state.updatePages(66, pageUrls(168))

        state.markBodyImageReady(MangaThumbnailPageKey(65, 81, "https://img.example/82.jpg"))
        state.markBodyImageReady(MangaThumbnailPageKey(66, 81, "https://img.example/wrong.jpg"))

        assertEquals(emptyList<MangaThumbnailPageKey>(), state.cacheRecoveryCandidates())
    }

    @Test
    fun failedCacheRecoveryDoesNotQueueItselfAgain() {
        val state = MangaThumbnailRecoveryState()
        val key = MangaThumbnailPageKey(66, 81, "https://img.example/82.jpg")
        state.updatePages(66, pageUrls(168))
        state.markBodyImageReady(key)
        state.markCacheRecoveryStarted(key)

        state.markThumbnailFailed(key)

        assertFalse(state.canLoadInBackground(key))
        assertEquals(emptyList<MangaThumbnailPageKey>(), state.cacheRecoveryCandidates())
    }

    @Test
    fun pageListChangeDiscardsMismatchedState() {
        val state = MangaThumbnailRecoveryState()
        val oldKey = MangaThumbnailPageKey(66, 0, "https://img.example/1.jpg")
        state.updatePages(66, pageUrls(2))
        state.markThumbnailFailed(oldKey)
        state.markBodyImageReady(oldKey)

        state.updatePages(67, listOf("https://other.example/1.jpg"))

        assertEquals(emptyList<MangaThumbnailPageKey>(), state.cacheRecoveryCandidates())
        assertFalse(state.isCurrent(oldKey))

        state.updatePages(66, pageUrls(2))

        assertEquals(emptyList<MangaThumbnailPageKey>(), state.cacheRecoveryCandidates())
        assertTrue(state.canLoadInBackground(oldKey))
    }

    @Test
    fun recoveryCandidateWaitsUntilARequestSlotIsAvailable() {
        val state = MangaThumbnailRecoveryState()
        val key = MangaThumbnailPageKey(66, 81, "https://img.example/82.jpg")
        state.updatePages(66, pageUrls(168))
        state.markBodyImageReady(key)

        assertEquals(emptyList<MangaThumbnailPageKey>(), state.cacheRecoveryCandidates(0))
        assertEquals(listOf(key), state.cacheRecoveryCandidates(1))
        assertEquals(listOf(key), state.cacheRecoveryCandidates(1))

        state.markCacheRecoveryStarted(key)
        assertEquals(emptyList<MangaThumbnailPageKey>(), state.cacheRecoveryCandidates(1))
    }

    @Test
    fun cancelledCacheRecoveryRemainsCacheOnlyAndCanResume() {
        val state = MangaThumbnailRecoveryState()
        val key = MangaThumbnailPageKey(66, 81, "https://img.example/82.jpg")
        state.updatePages(66, pageUrls(168))
        state.markThumbnailFailed(key)
        state.markBodyImageReady(key)
        state.markCacheRecoveryStarted(key)

        assertFalse(state.canLoadInBackground(key))

        state.markCacheRecoveryCancelled(key)

        assertFalse(state.canLoadInBackground(key))
        assertEquals(listOf(key), state.cacheRecoveryCandidates())
        state.markCacheRecoveryStarted(key)
        state.markThumbnailFailed(key)
        assertEquals(emptyList<MangaThumbnailPageKey>(), state.cacheRecoveryCandidates())
        assertFalse(state.canLoadInBackground(key))
    }

    @Test
    fun recoveryCandidatesPrioritizeCurrentPage() {
        val state = MangaThumbnailRecoveryState()
        val first = MangaThumbnailPageKey(66, 0, "https://img.example/1.jpg")
        val current = MangaThumbnailPageKey(66, 81, "https://img.example/82.jpg")
        val last = MangaThumbnailPageKey(66, 167, "https://img.example/168.jpg")
        state.updatePages(66, pageUrls(168))
        state.markBodyImageReady(first)
        state.markBodyImageReady(last)
        state.markBodyImageReady(current)

        assertEquals(
            listOf(current, first),
            state.cacheRecoveryCandidates(2, priorityPageIndex = 81),
        )
    }

    @Test
    fun currentMangaPageCreatesThumbnailKey() {
        val page = mangaPage(66, 81, "https://img.example/82.jpg")

        assertEquals(
            MangaThumbnailPageKey(66, 81, "https://img.example/82.jpg"),
            page.thumbnailKeyIfCurrent(66, pageUrls(168)),
        )
    }

    @Test
    fun oldChapterMangaPageDoesNotCreateThumbnailKey() {
        val page = mangaPage(65, 81, "https://img.example/82.jpg")

        assertEquals(null, page.thumbnailKeyIfCurrent(66, pageUrls(168)))
    }

    @Test
    fun reboundUrlDoesNotCreateThumbnailKey() {
        val page = mangaPage(66, 81, "https://img.example/old.jpg")

        assertEquals(null, page.thumbnailKeyIfCurrent(66, pageUrls(168)))
    }

    private fun mangaPage(chapterIndex: Int, index: Int, url: String) = MangaPage(
        chapterIndex = chapterIndex,
        chapterSize = 100,
        mImageUrl = url,
        index = index,
        imageCount = 168,
        mChapterName = "第67话",
    )

    private fun pageUrls(count: Int): List<String> =
        (1..count).map { "https://img.example/$it.jpg" }
}
