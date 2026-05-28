package io.wanjuan.app.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class MainTopActionLayoutTest {

    @Test
    fun modernTopActionGapsUseSharedCompactDimen() {
        val dimens = parseXml(repoFile("app/src/main/res/values/dimens.xml"))
        val gap = dimens.dimenByName("main_top_action_button_gap")
        assertNotNull("main_top_action_button_gap should exist", gap)
        assertEquals("6dp", gap?.textContent?.trim())

        val bookshelf1 = parseXml(repoFile("app/src/main/res/layout/fragment_bookshelf1.xml"))
        assertEquals(
            "@dimen/main_top_action_button_gap",
            bookshelf1.elementById("btn_bookshelf_read_record").androidAttr("layout_marginEnd")
        )
        assertEquals(
            "@dimen/main_top_action_button_gap",
            bookshelf1.elementById("btn_bookshelf_layout_toggle").androidAttr("layout_marginEnd")
        )

        val bookshelf2 = parseXml(repoFile("app/src/main/res/layout/fragment_bookshelf2.xml"))
        assertEquals(
            "@dimen/main_top_action_button_gap",
            bookshelf2.elementById("btn_bookshelf_read_record").androidAttr("layout_marginEnd")
        )

        val explore = parseXml(repoFile("app/src/main/res/layout/fragment_explore.xml"))
        assertEquals(
            3,
            explore.elementsWithAndroidAttr("layout_width", "@dimen/main_top_action_button_gap").size
        )

        val rss = parseXml(repoFile("app/src/main/res/layout/fragment_rss.xml"))
        assertEquals(
            3,
            rss.elementsWithAndroidAttr("layout_width", "@dimen/main_top_action_button_gap").size
        )
    }

    @Test
    fun modernRssTopActionsMatchDiscoverButtonMetrics() {
        val rss = parseXml(repoFile("app/src/main/res/layout/fragment_rss.xml"))
        listOf(
            "btn_rss_source_search",
            "btn_rss_source_star",
            "btn_rss_source_refresh",
            "btn_rss_source_login"
        ).forEach { id ->
            val button = rss.elementById(id)
            assertEquals("@dimen/discover_top_action_button_size", button.androidAttr("layout_width"))
            assertEquals("@dimen/discover_top_action_button_size", button.androidAttr("layout_height"))
            assertEquals("@dimen/discover_top_action_button_padding", button.androidAttr("padding"))
        }
    }

    @Test
    fun mainRssMenuTopActionsUseLucideIcons() {
        val menu = parseXml(repoFile("app/src/main/res/menu/main_rss.xml"))
        val expectedIcons = mapOf(
            "menu_read_record" to "@drawable/ic_lucide_chart_bar",
            "menu_rss_star" to "@drawable/ic_lucide_star",
            "menu_group" to "@drawable/ic_lucide_tags",
            "menu_rss_config" to "@drawable/ic_lucide_settings"
        )

        expectedIcons.forEach { (id, icon) ->
            assertEquals(icon, menu.elementById(id).androidAttr("icon"))
        }
    }

    @Test
    fun modernRssSourceActionFallsBackToInAppWebViewForSourcesWithoutLoginUrl() {
        val rssFragment =
            repoFile("app/src/main/java/io/wanjuan/app/ui/main/rss/RssFragment.kt").readText()
        val selectSourceBlock = rssFragment
            .substringAfter("private fun selectSource(")
            .substringBefore("private suspend fun presentSource")
        val updateButtonBlock = rssFragment
            .substringAfter("private fun updateRssSourceActionButtonState(")
            .substringBefore("private fun selectSource(")
        val openActionBlock = rssFragment
            .substringAfter("private fun openSelectedRssSourceAction(")
            .substringBefore("override fun openRss(")

        assertFalse(
            selectSourceBlock.contains(
                "binding.btnRssSourceLogin.isVisible = !source.loginUrl.isNullOrBlank()"
            )
        )
        assertTrue(updateButtonBlock.contains("binding.btnRssSourceLogin.visible()"))
        assertTrue(updateButtonBlock.contains("R.drawable.ic_lucide_link_2"))
        assertTrue(updateButtonBlock.contains("R.drawable.ic_lucide_user"))
        assertTrue(updateButtonBlock.contains("R.string.open_in_app_webview"))
        assertTrue(openActionBlock.contains("openRssSourceLink(rssSource)"))
        assertTrue(openActionBlock.contains("startActivity<WebViewActivity>"))
        assertTrue(openActionBlock.contains("putExtra(\"sourceType\", SourceType.rss)"))
        assertTrue(openActionBlock.contains("putExtra(\"refetchAfterSuccess\", false)"))
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

    private fun Element.dimenByName(name: String): Element? {
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element && child.tagName == "dimen" && child.getAttribute("name") == name) {
                return child
            }
        }
        return null
    }

    private fun Element.elementsWithAndroidAttr(name: String, value: String): List<Element> {
        val elements = mutableListOf<Element>()
        fun visit(element: Element) {
            if (element.androidAttr(name) == value) {
                elements += element
            }
            val children = element.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child is Element) {
                    visit(child)
                }
            }
        }
        visit(this)
        return elements
    }

    private fun Element.androidAttr(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
