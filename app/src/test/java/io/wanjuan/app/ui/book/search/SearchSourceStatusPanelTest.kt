package io.wanjuan.app.ui.book.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SearchSourceStatusPanelTest {

    @Test
    fun searchLayoutContainsSourceStatusPanelBetweenInputAndResults() {
        val layout = parseXml(repoFile("app/src/main/res/layout/activity_book_search.xml"))
        val statusPanel = layout.elementById("ll_source_status_panel")
        val statusChips = layout.elementById("fl_search_source_statuses")
        val contentView = layout.elementById("content_view")

        assertEquals("@id/refresh_progress_bar", statusPanel.appAttr("layout_constraintTop_toBottomOf"))
        assertEquals("@id/ll_source_status_panel", contentView.appAttr("layout_constraintTop_toBottomOf"))
        assertTrue(statusChips.hasAncestor(statusPanel))
        assertTrue(statusPanel.isBefore(contentView))
    }

    @Test
    fun searchActivityRendersSourceStatusesAndJumpsToFoundResults() {
        val activity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/search/SearchActivity.kt").readText()

        assertTrue(activity.contains("viewModel.searchSourceStatusLiveData.observe(this)"))
        assertTrue(activity.contains("private fun renderSearchSourceStatuses(statuses: List<SearchSourceStatus>)"))
        assertTrue(activity.contains("private fun createSearchSourceStatusChip(status: SearchSourceStatus, overflowCount: Int? = null): TextView"))
        assertTrue(activity.contains("private fun scrollToSearchSourceResult(sourceUrl: String)"))
        assertTrue(activity.contains("it.sourceUrl == sourceUrl"))
        assertTrue(activity.contains("binding.recyclerView.smoothScrollToPosition(index)"))
        assertTrue(activity.contains("SearchSourceState.FOUND ->"))
        assertTrue(activity.contains("SearchSourceState.FAILED ->"))
        assertTrue(activity.contains("SearchSourceState.EMPTY ->"))
        assertTrue(activity.contains("SearchSourceState.PENDING ->"))
    }

    @Test
    fun viewModelExposesSourceStatusSummaryFromSearchModel() {
        val viewModel = repoFile("app/src/main/java/io/wanjuan/app/ui/book/search/SearchViewModel.kt").readText()

        assertTrue(viewModel.contains("val searchSourceStatusLiveData = ConflateLiveData<List<SearchSourceStatus>>(250)"))
        assertTrue(viewModel.contains("private val sourceStatuses = linkedMapOf<String, SearchSourceStatus>()"))
        assertTrue(viewModel.contains("override fun onSearchSourcesReset(sources: List<BookSourcePart>)"))
        assertTrue(viewModel.contains("override fun onSearchSourceFound(source: BookSourcePart, resultCount: Int)"))
        assertTrue(viewModel.contains("override fun onSearchSourceEmpty(source: BookSourcePart)"))
        assertTrue(viewModel.contains("override fun onSearchSourceFailed(source: BookSourcePart, error: Throwable)"))
        assertTrue(viewModel.contains("sourceStatuses[source.bookSourceUrl] = source.toSearchSourceStatus(SearchSourceState.FOUND, resultCount)"))
    }

    @Test
    fun searchModelReportsPerSourceStatusOutcomes() {
        val searchModel = repoFile("app/src/main/java/io/wanjuan/app/model/webBook/SearchModel.kt").readText()

        assertTrue(searchModel.contains("callBack.onSearchSourcesReset(bookSourceParts)"))
        assertTrue(searchModel.contains("private suspend fun searchSource("))
        assertTrue(searchModel.contains("sourcePart: BookSourcePart"))
        assertTrue(searchModel.contains("callBack.onSearchSourceFound(sourcePart, items.size)"))
        assertTrue(searchModel.contains("callBack.onSearchSourceEmpty(sourcePart)"))
        assertTrue(searchModel.contains("callBack.onSearchSourceFailed(sourcePart, throwable)"))
        assertTrue(searchModel.contains("fun onSearchSourcesReset(sources: List<BookSourcePart>)"))
        assertTrue(searchModel.contains("fun onSearchSourceFound(source: BookSourcePart, resultCount: Int)"))
        assertTrue(searchModel.contains("fun onSearchSourceEmpty(source: BookSourcePart)"))
        assertTrue(searchModel.contains("fun onSearchSourceFailed(source: BookSourcePart, error: Throwable)"))
    }

    @Test
    fun sourceStatusModelDefinesFourRequestedStates() {
        val status = repoFile("app/src/main/java/io/wanjuan/app/ui/book/search/SearchSourceStatus.kt").readText()

        assertTrue(status.contains("enum class SearchSourceState"))
        assertTrue(status.contains("FOUND"))
        assertTrue(status.contains("FAILED"))
        assertTrue(status.contains("EMPTY"))
        assertTrue(status.contains("PENDING"))
        assertTrue(status.contains("data class SearchSourceStatus"))
        assertTrue(status.contains("val sourceUrl: String"))
        assertTrue(status.contains("val resultCount: Int = 0"))
        assertTrue(status.contains("val errorMessage: String? = null"))
    }

    private fun parseXml(file: File): Element {
        return DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(file).documentElement
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }

    private fun Element.elementById(id: String): Element {
        if (androidAttr("id") == "@+id/$id" || androidAttr("id") == "@id/$id") {
            return this
        }
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element) {
                runCatching { return child.elementById(id) }
            }
        }
        error("Element with id $id not found")
    }

    private fun Element.hasAncestor(ancestor: Element): Boolean {
        return generateSequence(parentNode) { it.parentNode }
            .filterIsInstance<Element>()
            .any { it === ancestor }
    }

    private fun Element.isBefore(other: Element): Boolean {
        val parent = parentNode
        require(parent === other.parentNode) { "Elements do not share a parent" }
        val children = parent.childNodes
        for (index in 0 until children.length) {
            when (children.item(index)) {
                this -> return true
                other -> return false
            }
        }
        error("Elements not found under parent")
    }

    private fun Element.androidAttr(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private fun Element.appAttr(name: String): String =
        getAttributeNS(APP_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val APP_NAMESPACE = "http://schemas.android.com/apk/res-auto"
    }
}
