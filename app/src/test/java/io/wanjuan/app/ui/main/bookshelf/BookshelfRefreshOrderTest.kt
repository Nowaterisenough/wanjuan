package io.wanjuan.app.ui.main.bookshelf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookshelfRefreshOrderTest {

    @Test
    fun bookshelfPullRefreshTriggersSync() {
        val style1 = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style1/books/BooksFragment.kt"
        ).readText()
        val style2 = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style2/BookshelfFragment2.kt"
        ).readText()

        assertTrue(
            "Style 1 pull refresh should trigger remote sync.",
            style1.pullRefreshBlock().contains("SyncManager.syncNow()")
        )
        assertTrue(
            "Style 2 pull refresh should trigger remote sync.",
            style2.pullRefreshBlock().contains("SyncManager.syncNow()")
        )
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
            refreshBlock.contains("activityViewModel.upToc(currentUpdateBooks(), onlyUpdateRead)")
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
