package io.wanjuan.app.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class TestNavigationBarLucideIcon {

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
