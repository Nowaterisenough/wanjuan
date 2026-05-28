package io.wanjuan.app.ui.main.explore

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class ExploreFragmentLifecycleTest {

    @Test
    fun modernDiscoveryReinitializesCurrentViewAfterRecreation() {
        val source = repoFile("app/src/main/java/io/wanjuan/app/ui/main/explore/ExploreFragment.kt").readText()
        val onResume = source.substringAfter("override fun onResume()")
            .substringBefore("override fun onPause()")

        assertTrue(onResume.contains("val needsViewInitialization"))
        assertTrue(onResume.contains("!modernModeInitialized"))
        assertTrue(onResume.contains("!oldModeInitialized"))
        assertTrue(onResume.contains("|| needsViewInitialization"))
        assertTrue(onResume.contains("applyDiscoveryMode(loadData = true)"))
    }

    @Test
    fun modernDiscoveryShowsFriendlyRuleErrorsInsteadOfRawRhinoToast() {
        val source = repoFile("app/src/main/java/io/wanjuan/app/ui/main/explore/ExploreFragment.kt").readText()
        val urlScriptBlock = source.substringAfter("private fun executeDiscoverUrlScriptIfNeeded(")
            .substringBefore("private fun extractDiscoverUrlScript")
        val buttonBlock = source.substringAfter("private fun handleDiscoverButtonTag(")
            .substringBefore("private fun applyDiscoverButtonResult")
        val selectBlock = source.substringAfter("private fun handleDiscoverSelectValue(")
            .substringBefore("private fun selectDiscoverTabByCode")

        assertTrue(source.contains("private fun showDiscoverRuleExecutionError(label: String)"))
        assertTrue(source.contains("R.string.discover_rule_error"))
        assertTrue(urlScriptBlock.contains("showDiscoverRuleExecutionError(item.text)"))
        assertTrue(buttonBlock.contains("showDiscoverRuleExecutionError(item.text)"))
        assertTrue(selectBlock.contains("showDiscoverRuleExecutionError(item.text)"))
        assertTrue(selectBlock.contains("catch (_: CancellationException)"))
        assertTrue(urlScriptBlock.contains("AppLog.put("))
        assertTrue(buttonBlock.contains("AppLog.put("))
        assertTrue(source.contains("binding.tvDiscoverEmpty.text = getString(R.string.discover_rule_error"))
        assertFalse(urlScriptBlock.contains("toastOnUi(it.localizedMessage"))
        assertFalse(buttonBlock.contains("toastOnUi(it.localizedMessage"))
    }

    @Test
    fun discoverySourcesUseSmartSortAndRecordUsage() {
        val viewModel = repoFile("app/src/main/java/io/wanjuan/app/ui/main/explore/ExploreViewModel.kt").readText()
        val fragment = repoFile("app/src/main/java/io/wanjuan/app/ui/main/explore/ExploreFragment.kt").readText()
        val adapter = repoFile("app/src/main/java/io/wanjuan/app/ui/main/explore/ExploreAdapter.kt").readText()

        assertTrue(viewModel.contains("suspend fun sortDiscoverSources"))
        assertTrue(viewModel.contains("BookSourcePrioritySorter.sortByPriority(list)"))
        assertTrue(viewModel.contains("DiscoverSourceUseConfig.addUse"))
        assertTrue(fragment.contains("val sortedList = viewModel.sortDiscoverSources(list)"))
        assertTrue(fragment.contains("val sortedList = viewModel.sortDiscoverSources(it)"))
        assertTrue(fragment.contains("recordDiscoverSourceUse(sourceUrl, increment = 2)"))
        assertTrue(fragment.contains("increment = 3"))
        assertTrue(adapter.contains("fun recordSourceUse(source: BookSourcePart, increment: Int = 1)"))
        assertTrue(adapter.contains("callBack.recordSourceUse(it)"))
    }

    @Test
    fun modernDiscoveryFiltersNavigationItemsBeforeAppendingNextPage() {
        val source = repoFile("app/src/main/java/io/wanjuan/app/ui/main/explore/ExploreFragment.kt").readText()
        val loadBlock = source.substringAfter("private fun loadDiscoverBooks(reset: Boolean)")
            .substringBefore("private fun initRecyclerView()")
        val filterBlock = source.substringAfter("private fun filterDiscoverBooksForAppend(")
            .substringBefore("private fun isDiscoverNavigationBook(")
        val navigationBlock = source.substringAfter("private fun isDiscoverNavigationBook(")
            .substringBefore("private fun initRecyclerView()")

        assertTrue(source.contains("private fun filterDiscoverBooksForAppend(newBooks: List<SearchBook>): List<SearchBook>"))
        assertTrue(source.contains("private fun isDiscoverNavigationBook(book: SearchBook): Boolean"))
        assertTrue(loadBlock.contains("val appendBooks = filterDiscoverBooksForAppend(newBooks)"))
        assertTrue(loadBlock.contains("if (appendBooks.isEmpty())"))
        assertTrue(loadBlock.contains("appDb.searchBookDao.insert(*appendBooks.toTypedArray())"))
        assertTrue(loadBlock.contains("discoverBooks.addAll(appendBooks)"))
        assertTrue(loadBlock.contains("appendDiscoverBooks(reset, oldBookCount, appendBooks)"))
        assertFalse(loadBlock.contains("discoverBookAdapter.addItems(newBooks)"))
        assertTrue(filterBlock.contains("seenBookUrls.add(book.bookUrl)"))
        assertTrue(filterBlock.contains("book.bookUrl.isNotBlank()"))
        assertTrue(filterBlock.contains("!isDiscoverNavigationBook(book)"))
        assertTrue(navigationBlock.contains("next"))
        assertTrue(navigationBlock.contains("\\u4e0b\\u4e00\\u9875"))
        assertTrue(navigationBlock.contains("normalizedName in navigationNames"))
    }

    @Test
    fun modernDiscoveryRefreshesIncompleteGridRowWhenAppendingNextPage() {
        val source = repoFile("app/src/main/java/io/wanjuan/app/ui/main/explore/ExploreFragment.kt").readText()
        val appendBlock = source.substringAfter("private fun appendDiscoverBooks(")
            .substringBefore("private fun shouldRefreshDiscoverAppendForGrid")
        val refreshBlock = source.substringAfter("private fun shouldRefreshDiscoverAppendForGrid(")
            .substringBefore("private fun filterDiscoverBooksForAppend(")

        assertTrue(source.contains("private fun appendDiscoverBooks(reset: Boolean, oldBookCount: Int, appendBooks: List<SearchBook>)"))
        assertTrue(source.contains("private fun shouldRefreshDiscoverAppendForGrid(reset: Boolean, oldBookCount: Int): Boolean"))
        assertTrue(appendBlock.contains("val anchor = captureDiscoverScrollAnchor()"))
        assertTrue(appendBlock.contains("discoverBookAdapter.setItems(discoverBooks.toList())"))
        assertTrue(appendBlock.contains("restoreDiscoverScrollAnchor(anchor)"))
        assertTrue(appendBlock.contains("discoverBookAdapter.addItems(appendBooks)"))
        assertTrue(refreshBlock.contains("normalizeDiscoverBookLayout(AppConfig.modernDiscoveryLayout) == DISCOVER_LAYOUT_GRID"))
        assertTrue(refreshBlock.contains("oldBookCount % AppConfig.modernDiscoveryGridColumns != 0"))
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }
}
