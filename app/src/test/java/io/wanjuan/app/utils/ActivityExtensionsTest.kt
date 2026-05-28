package io.wanjuan.app.utils

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ActivityExtensionsTest {

    @Test
    fun transparentFullscreenStatusBarUsesThemeBackgroundForIconContrast() {
        val source = repoFile("app/src/main/java/io/wanjuan/app/utils/ActivityExtensions.kt").readText()

        assertTrue(source.contains("import io.wanjuan.app.lib.theme.ThemeStore"))
        assertTrue(source.contains("val iconReferenceColor = if (isTransparent && fullScreen)"))
        assertTrue(source.contains("ThemeStore.backgroundColor(this)"))
        assertTrue(source.contains("ColorUtils.isColorLight(iconReferenceColor)"))
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }
}
