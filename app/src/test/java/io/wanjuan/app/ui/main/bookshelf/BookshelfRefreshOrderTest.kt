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
            "Style 1 pull refresh should pull WebDAV progress after catalog update.",
            style1.pullRefreshBlock().contains("pullProgressAfterUpdate = true")
        )
        assertTrue(
            "Style 2 pull refresh should trigger remote sync.",
            style2.pullRefreshBlock().contains("SyncManager.syncNow()")
        )
        assertTrue(
            "Style 2 pull refresh should pull WebDAV progress after catalog update.",
            style2.pullRefreshBlock().contains("pullProgressAfterUpdate = true")
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
            refreshBlock.contains("currentUpdateBooks()") &&
                    refreshBlock.contains("onlyUpdateRead") &&
                    refreshBlock.contains("pullProgressAfterUpdate = true")
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
    fun bookshelfPullRefreshAppliesProgressAfterTocRefresh() {
        val main = repoFile("app/src/main/java/io/wanjuan/app/ui/main/MainViewModel.kt").readText()
        val updateTocBlock = main.substringAfter("private suspend fun updateToc")
            .substringBefore("fun postUpBooksLiveData")
        val successBlock = updateTocBlock.substringAfter("appDb.bookChapterDao.insert")
            .substringBefore("addDownload(source, book)")

        assertTrue(main.contains("pullProgressAfterTocBooks"))
        assertTrue(main.contains("pullRemoteProgressAfterToc(book)"))
        assertTrue(successBlock.contains("ReadBook.onChapterListUpdated(book)"))
        assertTrue(successBlock.contains("ReadManga.onChapterListUpdated(book)"))
        assertTrue(successBlock.contains("pullRemoteProgressAfterToc(book)"))
        assertTrue(
            "Remote progress should only be applied when the refreshed catalog contains it.",
            main.contains("progress.durChapterIndex in 0..book.lastChapterIndex") &&
                    main.contains("SyncManager.progress.applyProgress(book, progress)")
        )
    }

    @Test
    fun waitingRefreshBooksShowCoverOverlay() {
        val main = repoFile("app/src/main/java/io/wanjuan/app/ui/main/MainViewModel.kt").readText()
        val style1Base = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style1/books/BaseBooksAdapter.kt"
        ).readText()
        val style2Base = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style2/BaseBooksAdapter.kt"
        ).readText()
        val adapters = listOf(
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style1/books/BooksAdapterGrid.kt",
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style1/books/BooksAdapterList.kt",
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style1/books/BooksAdapterList2.kt",
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style2/BooksAdapterGrid.kt",
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style2/BooksAdapterList.kt"
        ).map { repoFile(it).readText() }
        val layouts = listOf(
            "app/src/main/res/layout/item_bookshelf_grid.xml",
            "app/src/main/res/layout/item_bookshelf_grid2.xml",
            "app/src/main/res/layout/item_bookshelf_list.xml",
            "app/src/main/res/layout/item_bookshelf_list2.xml"
        ).map { repoFile(it).readText() }

        assertTrue(main.contains("waitUpTocBookSet"))
        assertTrue(main.contains("fun isWaitingUpdate(bookUrl: String)"))
        assertTrue(main.contains("waitUpTocBookSet.add(book.bookUrl)"))
        assertTrue(main.contains("waitUpTocBookSet.remove(bookUrl)"))
        assertTrue(style1Base.contains("fun isWaitingUpdate(bookUrl: String): Boolean"))
        assertTrue(style2Base.contains("fun isWaitingUpdate(bookUrl: String): Boolean"))
        adapters.forEach { adapter ->
            assertTrue(adapter.contains("vwCoverPendingOverlay.visible"))
            assertTrue(adapter.contains("callBack.isWaitingUpdate(item.bookUrl)"))
        }
        layouts.forEach { layout ->
            assertTrue(layout.contains("@+id/vw_cover_pending_overlay"))
            assertTrue(layout.contains("#66000000"))
            assertTrue(layout.contains("layout_constraintTop_toTopOf=\"@id/iv_cover\""))
        }
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
