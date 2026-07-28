package io.wanjuan.app.help.config

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BuiltInReaderBackgroundAssetsTest {

    @Test
    fun paperTextureBackgroundsAreBundledWithDisplayNames() {
        val backgroundNames = repoFile("app/src/main/assets/bg")
            .list()
            ?.toSet()
            .orEmpty()

        assertTrue("素白宣纸.jpg should be a bundled background", "素白宣纸.jpg" in backgroundNames)
        assertTrue("暖黄宣纸.png should be a bundled background", "暖黄宣纸.png" in backgroundNames)
    }

    private fun repoFile(path: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, path) }
            .firstOrNull { it.exists() }
            ?: error("$path not found")
    }
}
