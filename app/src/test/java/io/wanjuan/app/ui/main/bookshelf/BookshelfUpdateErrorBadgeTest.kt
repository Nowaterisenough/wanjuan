package io.wanjuan.app.ui.main.bookshelf

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookshelfUpdateErrorBadgeTest {

    @Test
    fun badgeViewCanRenderUpdateErrorAsRedExclamation() {
        val badgeView = repoFile("app/src/main/java/io/wanjuan/app/ui/widget/text/BadgeView.kt")
            .readText()

        assertTrue(
            "BadgeView should expose a dedicated update-error state for bookshelf refresh failures.",
            badgeView.contains("fun setUpdateError()")
        )
        assertTrue(
            "Update-error badge should show a white exclamation mark on the error color.",
            badgeView.contains("text = \"!\"") &&
                    badgeView.contains("R.color.error") &&
                    badgeView.contains("setTextColor(Color.WHITE)")
        )
    }

    @Test
    fun bookshelfAdaptersPreferUpdateErrorBadgeOverUnreadBadge() {
        listOf(
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style1/books/BooksAdapterList.kt",
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style1/books/BooksAdapterList2.kt",
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style1/books/BooksAdapterGrid.kt",
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style2/BooksAdapterList.kt",
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style2/BooksAdapterGrid.kt"
        ).forEach { relativePath ->
            val adapter = repoFile(relativePath).readText()

            assertTrue(
                "$relativePath should check update-error state before the unread-count setting.",
                adapter.indexOf("item.isUpError").let { errorIndex ->
                    errorIndex >= 0 && errorIndex < adapter.indexOf("AppConfig.showUnread")
                }
            )
            assertTrue(
                "$relativePath should render refresh failures with the update-error badge.",
                adapter.contains("bvUnread.setUpdateError()") ||
                        adapter.contains("binding.bvUnread.setUpdateError()")
            )
        }
    }

    @Test
    fun bookshelfDiffPayloadsRefreshWhenUpdateErrorChanges() {
        listOf(
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style1/books/BaseBooksAdapter.kt",
            "app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style2/BaseBooksAdapter.kt"
        ).forEach { relativePath ->
            val adapter = repoFile(relativePath).readText()

            assertTrue(
                "$relativePath should treat update-error flag changes as content changes.",
                adapter.contains("oldItem.isUpError != newItem.isUpError")
            )
            assertTrue(
                "$relativePath should send refresh payload when the update-error flag changes.",
                adapter.contains("bundle.putBoolean(\"refresh\", true)")
            )
        }
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }
}
