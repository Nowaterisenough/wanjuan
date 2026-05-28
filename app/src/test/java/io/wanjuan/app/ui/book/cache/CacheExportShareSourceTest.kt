package io.wanjuan.app.ui.book.cache

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CacheExportShareSourceTest {

    @Test
    fun chapterCacheDialogWiresExportShareButtonToTxtExporter() {
        val layout = repoFile("app/src/main/res/layout/dialog_cache_chapters.xml").readText()
        val dialog = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/book/cache/CacheChapterDialog.kt"
        ).readText()
        val viewModel = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/book/cache/CacheManageViewModel.kt"
        ).readText()

        assertTrue(layout.contains("@+id/btn_export_share"))
        assertTrue(layout.contains("@string/cache_manage_export_share"))

        assertTrue(dialog.contains("btnExportShare"))
        assertTrue(dialog.contains("exportShareBook()"))
        assertTrue(dialog.contains("viewModel.createDownloadAllTxtShareFile(book)"))
        assertTrue(dialog.contains("requireContext().share("))
        assertTrue(dialog.contains("!book.isAudio && !book.isVideo && !book.isImage"))

        assertTrue(viewModel.contains("suspend fun createDownloadAllTxtShareFile"))
        assertTrue(viewModel.contains("WebBook.getContentAwait(source, book, chapter, needSave = true)"))
        assertTrue(viewModel.contains("ContentProcessor.get(book.name, book.origin)"))
        assertTrue(viewModel.contains("book.getExportFileName(\"txt\")"))
        assertTrue(viewModel.contains("refreshManifest(book)"))
    }

    @Test
    fun readDownloadDialogShowsExportShareButton() {
        val activity = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/book/read/BaseReadBookActivity.kt"
        ).readText()

        assertTrue(activity.contains("neutralButton(R.string.cache_manage_export_share)"))
        assertTrue(activity.contains("cacheManageViewModel.createDownloadAllTxtShareFile(book)"))
        assertTrue(activity.contains("share(file, \"text/plain\", getString(R.string.cache_manage_export_share))"))
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }
}
