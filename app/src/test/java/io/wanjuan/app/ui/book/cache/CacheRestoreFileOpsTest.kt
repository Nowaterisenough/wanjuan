package io.wanjuan.app.ui.book.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class CacheRestoreFileOpsTest {

    @Test
    fun cacheRestoreReplacesChaptersInsideRoomTransaction() {
        val source = repoFile("app/src/main/java/io/wanjuan/app/ui/book/cache/CacheManageViewModel.kt").readText()

        val helper = source.substringAfter("private fun replaceBookChapters")
            .substringBefore("\n    private fun", missingDelimiterValue = "")
        assertTrue(helper.contains("appDb.runInTransaction"))
        assertTrue(helper.contains("appDb.bookChapterDao.delByBook(bookUrl)"))
        assertTrue(helper.contains("appDb.bookChapterDao.insert(*chapters.toTypedArray())"))
        assertFalse(source.contains("appDb.bookChapterDao.delByBook(cacheBook.bookUrl)\n                appDb.bookChapterDao.insert"))
    }

    @Test
    fun cacheRestoreFileOpsStagesPayloadThenReplacesTarget() {
        val root = createTempDirectory("cache-restore").toFile()
        val target = File(root, "target").apply {
            mkdirs()
            resolve("old.txt").writeText("old")
        }
        val payload = File(root, "payload").apply {
            mkdirs()
            resolve("new.txt").writeText("new")
        }

        val staging = CacheRestoreFileOps.copyPayloadToStaging(payload, target)
        CacheRestoreFileOps.replaceDirectory(target, staging)

        assertFalse(File(target, "old.txt").exists())
        assertEquals("new", File(target, "new.txt").readText())
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }
}
