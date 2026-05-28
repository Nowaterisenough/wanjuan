package io.wanjuan.app.help.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookSourcePrioritySorterUsageTest {

    @Test
    fun discoveryDelegatesToSharedPrioritySorter() {
        val viewModel = repoFile("app/src/main/java/io/wanjuan/app/ui/main/explore/ExploreViewModel.kt").readText()

        assertTrue(viewModel.contains("BookSourcePrioritySorter.sortByPriority(list)"))
        assertFalse(viewModel.contains("DiscoverSourceSorter.sort"))
        assertFalse(viewModel.contains("DiscoverSourceUseConfig.getUseStats(list.map { it.bookSourceUrl })"))
    }

    @Test
    fun searchScopeUsesSharedPriorityOrderForSearchExecutionAndStatusPanel() {
        val searchScope = repoFile("app/src/main/java/io/wanjuan/app/ui/book/search/SearchScope.kt").readText()

        assertTrue(searchScope.contains("BookSourcePrioritySorter.sortByPriority(list.toList())"))
        assertFalse(searchScope.contains("return list.sortedBy { it.customOrder }"))
    }

    @Test
    fun searchScopeDialogDisplaysSourcesInSharedPriorityOrder() {
        val dialog = repoFile("app/src/main/java/io/wanjuan/app/ui/book/search/SearchScopeDialog.kt").readText()

        assertTrue(dialog.contains("BookSourcePrioritySorter.sortByPriority(data)"))
        assertFalse(dialog.contains("screenSources.addAll(data)"))
    }

    @Test
    fun bookSourceManagementDefaultSortUsesSharedPriorityOrder() {
        val activity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/source/manage/BookSourceActivity.kt").readText()
        val viewModel = repoFile("app/src/main/java/io/wanjuan/app/ui/book/source/manage/BookSourceViewModel.kt").readText()

        assertTrue(activity.contains("BookSourcePrioritySorter.sortByPriority(data)"))
        assertTrue(activity.contains("BookSourcePrioritySorter.sortByPriority(data).reversed()"))
        assertTrue(viewModel.contains("BookSourcePrioritySorter.sortByPriority(data)"))
        assertTrue(viewModel.contains("BookSourcePrioritySorter.sortByPriority(data).reversed()"))
    }

    @Test
    fun otherUserFacingSourcePickersUseSharedPriorityOrder() {
        val sourcePicker = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manage/SourcePickerDialog.kt").readText()
        val changeSource = repoFile("app/src/main/java/io/wanjuan/app/ui/book/changesource/ChangeBookSourceViewModel.kt").readText()
        val changeCover = repoFile("app/src/main/java/io/wanjuan/app/ui/book/changecover/ChangeCoverViewModel.kt").readText()

        assertTrue(sourcePicker.contains("BookSourcePrioritySorter.sortByPriority(data)"))
        assertTrue(changeSource.contains("BookSourcePrioritySorter.sortByPriority(appDb.bookSourceDao.allEnabledPart)"))
        assertTrue(changeSource.contains("BookSourcePrioritySorter.sortByPriority(sources)"))
        assertTrue(changeCover.contains("BookSourcePrioritySorter.sortByPriority(appDb.bookSourceDao.allEnabledPart)"))
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }
}
