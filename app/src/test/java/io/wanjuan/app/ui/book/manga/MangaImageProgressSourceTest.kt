package io.wanjuan.app.ui.book.manga

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MangaImageProgressSourceTest {

    @Test
    fun mangaProgressUsesConnectingResourceInsteadOfHardcodedOnePercent() {
        val manager = repoFile(
            "app/src/main/java/io/wanjuan/app/help/glide/progress/ProgressManager.kt"
        ).readText()
        val viewHolder = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/book/manga/recyclerview/MangaVH.kt"
        ).readText()
        val zh = repoFile("app/src/main/res/values-zh/strings.xml").readText()
        val modelLoader = repoFile(
            "app/src/main/java/io/wanjuan/app/help/glide/OkHttpModelLoader.kt"
        ).readText()

        assertFalse(manager.contains("listener.invoke(false, 1, 0, 0)"))
        assertTrue(viewHolder.contains("R.string.manga_image_connecting"))
        assertTrue(zh.contains("<string name=\"manga_image_connecting\">连接中…</string>"))
        assertTrue(modelLoader.contains("ModelLoader.LoadData(model, OkHttpStreamFetcher(model, options))"))
    }

    private fun repoFile(relativePath: String): File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
}
