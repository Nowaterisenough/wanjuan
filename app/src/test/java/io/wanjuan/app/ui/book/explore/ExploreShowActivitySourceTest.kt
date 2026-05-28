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

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }
}
