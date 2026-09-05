package io.wanjuan.app.ui.main.bookshelf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TestBookshelfRefreshOrder {

    @Test
    fun bothLayoutsUseSharedRefreshPipelineAndObserveItsResult() {
        val paths = listOf(
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style1/books/BooksFragment.kt",
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style2/BookshelfFragment2.kt"
        )
        paths.forEach { path ->
            val source = repoFile(path).readText()
            val refresh = source.pullRefreshBlock()
            assertTrue(refresh.contains("activityViewModel.refreshBookshelf("))
            assertTrue(refresh.contains("bookshelfRefreshStatus.observe(viewLifecycleOwner)"))
            assertTrue(refresh.indexOf("binding.refreshLayout.isRefreshing = false") <
                refresh.indexOf("activityViewModel.refreshBookshelf("))
            assertFalse(refresh.contains("SyncManager.syncNow"))
            assertFalse(source.contains("enableRefresh && itemCount > 0"))
        }
    }

    @Test
    fun style2PullRefreshUsesCurrentAdapterBookOrder() {
        val fragment = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style2/BookshelfFragment2.kt"
        ).readText()
        val refreshBlock = fragment.pullRefreshBlock()

        assertTrue(
            "Style 2 pull refresh should collect books from the current adapter list so the " +
                    "refresh queue matches the order currently shown on the bookshelf.",
            refreshBlock.contains("currentUpdateBooks()") &&
                    refreshBlock.contains("onlyUpdateRead") &&
                    refreshBlock.contains("updateCatalog = enableRefresh")
        )
        assertTrue(
            "Style 2 should derive refresh books from adapter items and filter out group tiles.",
            fragment.contains("override fun currentUpdateBooks(): List<Book>") &&
                    fragment.contains("booksAdapter.getItems().filterIsInstance<Book>()")
        )
        assertFalse(
            "Style 2 pull refresh should not use the backing books cache directly.",
            refreshBlock.contains("activityViewModel.upToc(books, onlyUpdateRead)")
        )
    }

    @Test
    fun bookshelfMenuRefreshUsesCurrentUpdateBooksHook() {
        val base = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/BaseBookshelfFragment.kt"
        ).readText()

        assertTrue(
            "Shared bookshelf menu refresh should use the same current-order hook as pull refresh.",
            base.contains("protected open fun currentUpdateBooks(): List<Book> = books") &&
                    base.contains(
                        "R.id.menu_update_toc -> activityViewModel.upToc(" +
                                "currentUpdateBooks(), onlyUpdateRead)"
                    )
        )
    }

    @Test
    fun manualRefreshRetriesUpdateErrorBooksEvenWhenOnlyUpdateReadIsEnabled() {
        val main = repoFile("app/src/main/java/io/wanjuan/app/ui/main/MainViewModel.kt").readText()
        val autoRefreshBlock = main.substringAfter("fun upAllBookToc()")
            .substringBefore("fun ruleSubsUp()")
        val manualRefreshBlock = main.substringAfter("fun upToc(")
            .substringBefore("@Synchronized")
        val addToWaitBlock = main.substringAfter("private fun addToWaitUp(")
            .substringBefore("private fun startUpTocJob()")

        assertTrue(
            "Manual bookshelf refresh should retry books that already have an update-error badge.",
            manualRefreshBlock.contains("retryUpdateErrorBooks = true")
        )
        assertTrue(
            "Automatic startup refresh should keep the original only-update-read behavior.",
            autoRefreshBlock.contains("retryUpdateErrorBooks = false")
        )
        assertTrue(
            "Only-update-read filtering should not skip update-error books during manual retry.",
            addToWaitBlock.contains("retryUpdateErrorBooks") &&
                    addToWaitBlock.contains("book.isUpError") &&
                    addToWaitBlock.contains("!(retryUpdateErrorBooks && book.isUpError)")
        )
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }

    private fun String.pullRefreshBlock(): String {
        return substringAfter("binding.refreshLayout.setOnRefreshListener")
            .substringBefore("updateLayoutManager()")
    }
}
