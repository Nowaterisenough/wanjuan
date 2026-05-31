package io.wanjuan.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OutboxRoutingSourceTest {

    @Test
    fun managerRoutesKnownObjectTypes() {
        val source = repoFile("app/src/main/java/io/wanjuan/app/sync/SyncManager.kt").readText()
        assertTrue(source.contains("SyncObjectType.Book"))
        assertTrue(source.contains("SyncObjectType.BookSource"))
        assertTrue(source.contains("SyncObjectType.RuleSub"))
        assertTrue(source.contains("SyncObjectType.BookshelfOrder"))
        assertTrue(source.contains("SyncObjectType.BookSourceOrder"))
        assertTrue(source.contains("SyncObjectType.RuleSubOrder"))
    }

    @Test
    fun readersDoNotCallOldRootProgressApi() {
        val readBook = repoFile("app/src/main/java/io/wanjuan/app/model/ReadBook.kt").readText()
        val readBookVm =
            repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookViewModel.kt").readText()
        val readManga = repoFile("app/src/main/java/io/wanjuan/app/model/ReadManga.kt").readText()
        val readMangaVm =
            repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/ReadMangaViewModel.kt").readText()
        val readBookActivity =
            repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookActivity.kt").readText()
        val bookController =
            repoFile("app/src/main/java/io/wanjuan/app/api/controller/BookController.kt").readText()
        val appWebDav = repoFile("app/src/main/java/io/wanjuan/app/help/AppWebDav.kt").readText()
        assertFalse(readBook.contains("AppWebDav.getBookProgress"))
        assertFalse(readBook.contains("AppWebDav.uploadBookProgress"))
        assertFalse(readBookVm.contains("AppWebDav.getBookProgress"))
        assertFalse(readManga.contains("AppWebDav.getBookProgress"))
        assertFalse(readManga.contains("AppWebDav.uploadBookProgress"))
        assertFalse(readMangaVm.contains("AppWebDav.getBookProgress"))
        assertFalse(readBookActivity.contains("AppWebDav.uploadBookProgress"))
        assertFalse(bookController.contains("AppWebDav.uploadBookProgress"))
        assertFalse(appWebDav.contains("downloadAllBookProgress"))
        assertFalse(appWebDav.contains("fun getBookProgress"))
        assertFalse(appWebDav.contains("fun uploadBookProgress"))
    }

    private fun repoFile(path: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, path) }
            .firstOrNull { it.exists() }
            ?: error("$path not found")
    }
}
