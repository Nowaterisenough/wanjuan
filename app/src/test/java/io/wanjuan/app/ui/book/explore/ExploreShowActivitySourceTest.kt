package io.wanjuan.app.ui.book.explore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExploreShowActivitySourceTest {

    @Test
    fun nextPageAppendKeepsScrollAnchorByAvoidingFullListReset() {
        val activity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/explore/ExploreShowActivity.kt").readText()
        val upDataBody = activity.substringAfter("private fun upData(books: List<SearchBook>)")
            .substringBefore("private fun upDataTop")

        assertTrue(upDataBody.contains("val oldItemCount = adapter.getActualItemCount()"))
        assertTrue(upDataBody.contains("val appendedBooks = books.drop(oldItemCount)"))
        assertTrue(upDataBody.contains("adapter.addItems(appendedBooks)"))
        assertTrue(upDataBody.contains("adapter.setItems(books)"))
        assertFalse(upDataBody.contains("adapter.setItems(books)\n            if (isClearAll)"))
    }

    @Test
    fun modernDiscoverNextPageAppendKeepsScrollAnchorByAvoidingFullListReset() {
        val fragment = repoFile("app/src/main/java/io/wanjuan/app/ui/main/explore/ExploreFragment.kt").readText()
        val loadBody = fragment.substringAfter("private fun loadDiscoverBooks(reset: Boolean)")
            .substringBefore("override fun onPause")

        assertTrue(loadBody.contains("val oldBookCount = discoverBooks.size"))
        assertTrue(loadBody.contains("val oldAdapterItemCount = discoverBookAdapter.getActualItemCount()"))
        assertTrue(loadBody.contains("oldAdapterItemCount != oldBookCount"))
        assertTrue(loadBody.contains("appendDiscoverBooks(reset, oldBookCount, appendBooks)"))
        assertTrue(loadBody.contains("discoverBookAdapter.addItems(appendBooks)"))
        assertTrue(loadBody.contains("discoverBookAdapter.setItems(discoverBooks.toList())"))
        assertTrue(loadBody.contains("restoreDiscoverScrollAnchor(anchor)"))
        assertFalse(loadBody.contains("discoverBooks.addAll(newBooks)\n                    discoverBookAdapter.setItems(discoverBooks.toList())"))
    }

    @Test
    fun discoverPageCountBadgeIsScopedToImageResultsAndPageLikeText() {
        val adapter = repoFile("app/src/main/java/io/wanjuan/app/ui/book/explore/ExploreShowAdapter.kt").readText()

        assertTrue(adapter.contains("private fun SearchBook.discoveryPageCountText()"))
        assertTrue(adapter.contains("BookType.image"))
        assertTrue(adapter.contains("pageCountRegex"))
        assertTrue(adapter.contains("wordCount?.trim()"))
        assertTrue(adapter.contains("tvPageCount.isVisible"))
    }

    @Test
    fun discoverPageCountViewsExistInGridAndListLayouts() {
        val grid = repoFile("app/src/main/res/layout/item_explore_book_grid.xml").readText()
        val list = repoFile("app/src/main/res/layout/item_search.xml").readText()

        assertTrue(grid.contains("android:id=\"@+id/tv_page_count\""))
        assertTrue(grid.contains("@drawable/bg_discover_page_count_capsule"))
        assertTrue(grid.contains("android:layout_gravity=\"bottom|end\""))
        assertTrue(list.contains("android:id=\"@+id/tv_page_count\""))
        assertTrue(list.indexOf("android:id=\"@+id/tv_name\"") < list.indexOf("android:id=\"@+id/tv_page_count\""))
        assertTrue(list.indexOf("android:id=\"@+id/tv_page_count\"") < list.indexOf("android:id=\"@+id/tv_author\""))
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }
}
