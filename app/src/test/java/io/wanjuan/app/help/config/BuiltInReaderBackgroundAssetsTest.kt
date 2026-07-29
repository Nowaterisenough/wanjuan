package io.wanjuan.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class BuiltInReaderBackgroundAssetsTest {

    @Test
    fun everyBundledBackgroundIsExposedInReaderSelector() {
        val backgroundNames = repoFile("app/src/main/assets/bg")
            .list()
            ?.toSet()
            .orEmpty()
        val selectableBackgroundNames = Regex(
            """BackgroundSample\(.*,\s*"([^"]+)"\)"""
        ).findAll(
            repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        ).map { it.groupValues[1] }.toSet()

        assertEquals(backgroundNames, selectableBackgroundNames)
    }

    private fun repoFile(path: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, path) }
            .firstOrNull { it.exists() }
            ?: error("$path not found")
    }
}
