package io.wanjuan.app.ui.book.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TestSearchProgressRestore {

    @Test
    fun searchOriginProgressIsOnlySavedWhenOpeningFullTextSearchResults() {
        val readActivity = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookActivity.kt"
        ).readText()
        val saveCalls = repoFile("app/src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence()
                    .filter { it.contains("ReadBook.saveSearchOriginProgress()") }
                    .map { file.name }
            }
            .toList()

        assertEquals(listOf("ReadBookActivity.kt", "ReadBookActivity.kt"), saveCalls)
        assertTrue(
            readActivity.substringAfter("private val searchContentActivity")
                .substringBefore("private val bookInfoActivity")
                .let {
                    it.contains("confirmRestoreSearchProgress = null") &&
                        it.contains("ReadBook.saveSearchOriginProgress()")
                }
        )
        assertTrue(
            readActivity.substringAfter("override fun openInlineSearchResult(")
                .substringBefore("override fun disableSource()")
                .let {
                    it.contains("confirmRestoreSearchProgress = null") &&
                        it.contains("ReadBook.saveSearchOriginProgress()")
                }
        )
    }

    @Test
    fun chapterAndBookmarkJumpsDoNotEnableSearchProgressRestore() {
        val readActivity = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookActivity.kt"
        ).readText()
        val readMenu = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt"
        ).readText()
        val readViewModel = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookViewModel.kt"
        ).readText()

        val chapterJump = readActivity.substringAfter("override fun skipToChapter(index: Int)")
            .substringBefore("override fun navigateToSearch(")
        val bookmarkJump = readMenu.substringAfter("private fun openBookmark(bookmark: Bookmark)")
            .substringBefore("private fun animateTocPanelTo(")

        assertFalse(chapterJump.contains("saveSearchOriginProgress"))
        assertFalse(bookmarkJump.contains("saveSearchOriginProgress"))
        assertFalse(readViewModel.contains("saveSearchOriginProgress"))
    }

    private fun repoFile(path: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, path) }
            .firstOrNull { it.exists() }
            ?: error("$path not found")
    }
}
