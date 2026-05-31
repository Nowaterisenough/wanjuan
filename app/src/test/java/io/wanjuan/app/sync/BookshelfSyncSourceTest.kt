package io.wanjuan.app.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookshelfSyncSourceTest {

    @Test
    fun bookshelfSyncIncludesBadgeFields() {
        val source = File("app/src/main/java/io/wanjuan/app/sync/BookshelfSyncCoordinator.kt").readText()
        assertTrue(source.contains("totalChapterNum"))
        assertTrue(source.contains("lastCheckCount"))
        assertTrue(source.contains("latestChapterTitle"))
        assertTrue(source.contains("latestChapterTime"))
        assertTrue(source.contains("catalogUpdatedAt"))
        assertTrue(source.contains("syncMetadataDao"))
        assertTrue(source.contains("localUpdatedAt"))
        assertTrue(source.contains("bookShelf"))
        assertTrue(source.contains("bookCatalog"))
        assertTrue(!source.contains("local.durChapterTime, payload.shelfUpdatedAt"))
    }

    @Test
    fun bookshelfSyncDoesNotMentionRssSources() {
        val source = File("app/src/main/java/io/wanjuan/app/sync/BookshelfSyncCoordinator.kt").readText()
        assertTrue(!source.contains("rssSourceUpdatedAt"))
        assertTrue(!source.contains("rssSources/"))
    }
}
