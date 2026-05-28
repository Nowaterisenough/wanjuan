package io.wanjuan.app.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class NavigationBarLucideIconTest {

    @Test
    fun bottomNavigationPrimaryItemsUseLucideIconsInMenuAndDefaultConfig() {
        val expected = linkedMapOf(
            "bookshelf" to ExpectedIcon("menu_bookshelf", "ic_lucide_books"),
            "discovery" to ExpectedIcon("menu_discovery", "ic_lucide_compass"),
            "rss" to ExpectedIcon("menu_rss", "ic_lucide_rss"),
            "readRecord" to ExpectedIcon("menu_read_record", "ic_lucide_chart_bar")
        )
        val menu = parseXml(repoFile("app/src/main/res/menu/main_bnv.xml"))
        val navConfig = repoFile(
            "app/src/main/java/io/wanjuan/app/help/config/NavigationBarIconConfig.kt"
        ).readText()

        expected.forEach { (key, icon) ->
            assertEquals(
                "@drawable/${icon.drawableName}",
                menu.elementById(icon.menuId).androidAttr("icon")
            )
            assertTrue(
                "NavigationBarIconConfig should default $key to ${icon.drawableName}",
                navConfig.contains(
                    "NavItem(\"$key\", " +
                        "R.string.${if (key == "readRecord") "side_nav_stats" else keyString(key)}, " +
                        "R.id.${icon.menuId}, R.drawable.${icon.drawableName})"
                )
            )
        }
    }

    private data class ExpectedIcon(
        val menuId: String,
        val drawableName: String
    )

    private fun keyString(key: String): String =
        when (key) {
            "bookshelf" -> "bookshelf"
            "discovery" -> "discovery"
            "rss" -> "rss"
            else -> key
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

    private fun Element.androidAttr(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
