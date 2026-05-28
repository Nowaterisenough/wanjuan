package io.wanjuan.app.ui.book.changesource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ChangeSourceIconTest {

    @Test
    fun changeSourceEntrypointsUseLucideShuffleIcon() {
        val expectedIcon = "@drawable/ic_lucide_shuffle"

        listOf(
            "app/src/main/res/menu/book_read.xml",
            "app/src/main/res/menu/book_manga.xml",
            "app/src/main/res/menu/audio_play.xml"
        ).forEach { path ->
            val menu = parseXml(repoFile(path))

            assertEquals(
                "$path menu_change_source icon",
                expectedIcon,
                menu.elementById("menu_change_source").androidAttr("icon")
            )
        }

        listOf(
            "app/src/main/res/layout/activity_book_info.xml",
            "app/src/main/res/layout-land/activity_book_info.xml",
            "app/src/main/res/layout/activity_video_player.xml"
        ).forEach { path ->
            val layout = parseXml(repoFile(path))

            assertEquals(
                "$path change source origin icon",
                expectedIcon,
                layout.elementByContentDescription("@string/change_origin").androidAttr("src")
            )
        }

        assertTrue(repoFile("app/src/main/res/drawable/ic_lucide_shuffle.xml").isFile)
    }

    private fun parseXml(file: File): Element =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(file).documentElement

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }

    private fun Element.elementById(id: String): Element {
        if (androidAttr("id") == "@+id/$id" || androidAttr("id") == "@id/$id") {
            return this
        }
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element) {
                runCatching { return child.elementById(id) }
            }
        }
        error("Element with id $id not found")
    }

    private fun Element.elementByContentDescription(contentDescription: String): Element {
        if (androidAttr("contentDescription") == contentDescription) {
            return this
        }
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element) {
                runCatching { return child.elementByContentDescription(contentDescription) }
            }
        }
        error("Element with contentDescription $contentDescription not found")
    }

    private fun Element.androidAttr(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
