package io.wanjuan.app.ui.book.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

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
        val list = repoFile("app/src/main/res/layout/item_explore_book_list.xml").readText()

        assertTrue(grid.contains("android:id=\"@+id/tv_page_count\""))
        assertTrue(grid.contains("@drawable/bg_discover_page_count_capsule"))
        assertTrue(grid.contains("android:layout_gravity=\"bottom|end\""))
        assertTrue(list.contains("android:id=\"@+id/tv_page_count\""))
        assertTrue(list.indexOf("android:id=\"@+id/tv_name\"") < list.indexOf("android:id=\"@+id/tv_page_count\""))
        assertTrue(list.indexOf("android:id=\"@+id/tv_page_count\"") < list.indexOf("android:id=\"@+id/tv_author\""))
    }

    @Test
    fun bookshelfStateUsesTextBadgeInsideCoverInBothDiscoveryLayouts() {
        listOf(
            "app/src/main/res/layout/item_explore_book_grid.xml",
            "app/src/main/res/layout/item_explore_book_list.xml"
        ).forEach { layoutPath ->
            assertTrue("Discovery layout should exist: $layoutPath", repoFile(layoutPath).exists())
            val badge = layoutElement(layoutPath, "tv_in_bookshelf")

            assertEquals("TextView", badge.tagName)
            assertEquals("@drawable/bg_discover_added_badge", badge.androidAttribute("background"))
            assertEquals("@string/discover_added", badge.androidAttribute("text"))
            assertEquals("@android:color/white", badge.androidAttribute("textColor"))
            assertEquals("top|end", badge.androidAttribute("layout_gravity"))
            assertEquals("gone", badge.androidAttribute("visibility"))

            val coverContainer = badge.parentNode as Element
            assertEquals("FrameLayout", coverContainer.tagName)
            assertTrue(
                "Added badge and cover should share the same overlay container in $layoutPath",
                coverContainer.childElements().any {
                    it.androidAttribute("id") == "@+id/iv_cover"
                }
            )
        }
    }

    @Test
    fun bookshelfBadgeUsesReadableBlueBackground() {
        val document = xmlDocument("app/src/main/res/drawable/bg_discover_added_badge.xml")
        val solid = document.getElementsByTagName("solid").item(0) as Element

        assertEquals("@color/md_blue_800", solid.androidAttribute("color"))
    }

    @Test
    fun discoveryAndSearchAdaptersKeepBookshelfMarkersIsolated() {
        val discoveryAdapter = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/book/explore/ExploreShowAdapter.kt"
        ).readText()
        val searchAdapter = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/book/search/SearchAdapter.kt"
        ).readText()
        val searchMarker = layoutElement("app/src/main/res/layout/item_search.xml", "iv_in_bookshelf")

        assertTrue(discoveryAdapter.contains("ItemExploreBookListBinding.inflate"))
        assertTrue(discoveryAdapter.contains("is ItemExploreBookListBinding"))
        assertTrue(discoveryAdapter.contains("is ItemExploreBookGridBinding"))
        assertTrue(discoveryAdapter.contains("\"isInBookshelf\" -> tvInBookshelf.isVisible"))
        assertEquals(
            2,
            Regex(Regex.escape("tvInBookshelf.isVisible = callBack.isInBookshelf(item)"))
                .findAll(discoveryAdapter)
                .count()
        )

        assertTrue(searchAdapter.contains("ItemSearchBinding.inflate"))
        assertFalse(searchAdapter.contains("ItemExploreBookListBinding"))
        assertTrue(searchAdapter.contains("ivInBookshelf.isVisible = callBack.isInBookshelf(searchBook)"))
        assertEquals("io.wanjuan.app.ui.widget.image.CircleImageView", searchMarker.tagName)
    }

    private fun layoutElement(relativePath: String, id: String): Element {
        val document = xmlDocument(relativePath)

        return document.getElementsByTagName("*").let { nodes ->
            (0 until nodes.length)
                .asSequence()
                .map { nodes.item(it) }
                .filterIsInstance<Element>()
                .first { it.androidAttribute("id") == "@+id/$id" }
        }
    }

    private fun xmlDocument(relativePath: String) =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(repoFile(relativePath))

    private fun Element.childElements(): Sequence<Element> {
        return (0 until childNodes.length)
            .asSequence()
            .map { childNodes.item(it) }
            .filterIsInstance<Element>()
    }

    private fun Element.androidAttribute(name: String): String {
        return getAttributeNS(ANDROID_NAMESPACE, name)
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
