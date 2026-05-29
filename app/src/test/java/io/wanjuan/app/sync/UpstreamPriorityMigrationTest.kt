package io.wanjuan.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpstreamPriorityMigrationTest {

    @Test
    fun coverPipelinePreservesPngTransparency() {
        val bitmapUtils = repoFile("app/src/main/java/io/wanjuan/app/utils/BitmapUtils.kt").readText()
        val imageProcessUtils = repoFile("app/src/main/java/io/wanjuan/app/utils/ImageProcessUtils.kt").readText()
        val thumbnailCache = repoFile("app/src/main/java/io/wanjuan/app/help/CoverThumbnailCache.kt").readText()
        val bookCover = repoFile("app/src/main/java/io/wanjuan/app/model/BookCover.kt").readText()
        val coverImageView = repoFile("app/src/main/java/io/wanjuan/app/ui/widget/image/CoverImageView.kt").readText()

        assertTrue(bitmapUtils.contains("fun Bitmap.hasTransparentPixels()"))
        assertTrue(bitmapUtils.contains("fun Bitmap.preferredCoverExtension()"))
        assertTrue(bitmapUtils.contains("fun Bitmap.compressPreservingAlpha("))
        assertTrue(imageProcessUtils.contains("scaled.preferredCoverExtension()"))
        assertTrue(imageProcessUtils.contains("scaled.compressPreservingAlpha(it, 92)"))
        assertTrue(thumbnailCache.contains("cover_thumbs_v2"))
        assertTrue(thumbnailCache.contains("listOf(\"png\", \"jpg\")"))
        assertTrue(thumbnailCache.contains("thumb.preferredCoverExtension()"))
        assertTrue(thumbnailCache.contains("thumb.compressPreservingAlpha(out, 86)"))
        assertTrue(bookCover.contains("DecodeFormat.PREFER_ARGB_8888"))
        assertTrue(bookCover.contains(".disallowHardwareConfig()"))
        assertTrue(coverImageView.contains("DecodeFormat.PREFER_ARGB_8888"))
        assertTrue(coverImageView.contains(".disallowHardwareConfig()"))
    }

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
