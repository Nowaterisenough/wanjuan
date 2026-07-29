package io.wanjuan.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TestUpstreamPriorityMigration {

    @Test
    fun imageCropDecodesThroughStableFileAndSupportsNetworkSources() {
        val source = repoFile("app/src/main/java/io/wanjuan/app/ui/image/ImageCropActivity.kt").readText()

        assertTrue(source.contains("private suspend fun decodeBitmapFromStableFile(uri: Uri): Bitmap?"))
        assertTrue(source.contains("copyImageSourceToFile(uri, tempFile)"))
        assertTrue(source.contains("private suspend fun copyImageSourceToFile(uri: Uri, target: File)"))
        assertTrue(source.contains("uri.scheme.equals(\"http\", true)"))
        assertTrue(source.contains("okHttpClient.newCallResponse(0)"))
        assertTrue(source.contains("addHeaders(analyzeUrl.headerMap)"))
        assertTrue(source.contains("ImageDecoder.createSource(file)"))
        assertTrue(source.contains("decodeBitmapWithBitmapFactory(tempFile, sampleSize)"))
        assertFalse(source.contains("contentResolver.openInputStream(uri)?.use {\n                        BitmapFactory.decodeStream(it, null, options)"))
    }

    @Test
    fun imageClickCreatesLegacyChapterCacheAliasBeforeRunningSourceJs() {
        val bookHelp = repoFile("app/src/main/java/io/wanjuan/app/help/book/BookHelp.kt").readText()
        val readBookActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookActivity.kt").readText()

        assertTrue(bookHelp.contains("fun ensureLegacyContentAlias("))
        assertTrue(bookHelp.contains("val legacyFile = getLegacyContentFile(book, bookChapter, suffix) ?: return"))
        assertTrue(bookHelp.contains("sourceFile.copyTo(legacyFile, overwrite = true)"))
        assertTrue(bookHelp.contains("legacyFile.createFileIfNotExist().writeText(content)"))

        assertTrue(readBookActivity.contains("private fun ensureCurrentChapterCacheForClick(book: Book, chapter: BookChapter)"))
        assertTrue(readBookActivity.contains("BookHelp.saveText(book, chapter, current)"))
        assertTrue(readBookActivity.contains("BookHelp.ensureLegacyContentAlias(book, chapter, current)"))
        assertTrue(readBookActivity.indexOf("ensureCurrentChapterCacheForClick(book, chapter)") <
            readBookActivity.indexOf("source.evalJS(click)"))
    }

    private fun repoFile(path: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, path) }
            .firstOrNull { it.exists() }
            ?: error("$path not found")
    }
}
