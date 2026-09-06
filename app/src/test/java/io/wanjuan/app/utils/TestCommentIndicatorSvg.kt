package io.wanjuan.app.utils

import io.wanjuan.app.help.config.ThemeConfig
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TestCommentIndicatorSvg {
    @Test
    fun acceptsInlineSvgActionsWithoutDependingOnCallbackNames() {
        for (key in listOf("js", "click", "pclick")) {
            for (action in listOf("openDiscussion(42)", "a.b(c)", "java.showBrowser(url, title)", "(() => 1)()")) {
                val options = GSON.toJson(mapOf(key to action, "style" to "TEXT"))
                assertTrue(CommentIndicatorSvg.isInlineActionSvg("data:image/svg+xml;base64,PHN2Zz4=,$options"))
            }
        }
        for (src in listOf(
            "data:image/svg+xml;base64,PHN2Zz4=",
            "data:image/svg+xml;base64,PHN2Zz4=,{\"style\":\"text\"}",
            "data:image/svg+xml;base64,PHN2Zz4=,{\"click\":\"   \",\"style\":\"text\"}",
            "data:image/svg+xml;base64,PHN2Zz4=,{\"click\":\"openDiscussion()\",\"style\":\"full\"}",
            "data:image/png;base64,PHN2Zz4=,{\"click\":\"openDiscussion()\",\"style\":\"text\"}",
            "https://example.org/cover.svg,{\"click\":\"openDiscussion()\",\"style\":\"text\"}",
            "data:image/svg+xml;base64,PHN2Zz4=,{broken}"
        )) assertFalse(src, CommentIndicatorSvg.isInlineActionSvg(src))
    }

    @Test
    fun recolorsGenericBubblesWithoutChangingCountGeometryOrBackground() {
        val fixtures = GSON.fromJsonArray<Map<String, Any>>(
            File("tests/fixtures/comment-indicators.json").readText()
        ).getOrThrow()
        for (fixture in fixtures) {
            val original = fixture.getValue("svg").toString()
            val recolored = CommentIndicatorSvg.recolor(original, "#E53935")
            assertNotNull(fixture["name"].toString(), recolored)
            val before = Jsoup.parse(original, "", Parser.xmlParser())
            val after = Jsoup.parse(recolored!!, "", Parser.xmlParser())
            assertEquals(before.select("text").text(), after.select("text").text())
            assertEquals(before.selectFirst("svg")!!.attr("viewBox"), after.selectFirst("svg")!!.attr("viewBox"))
            assertEquals("#E53935", after.selectFirst("text")!!.attr("fill"))
            val beforeShapes = before.select("path, rect, circle, ellipse, polygon")
            val afterShapes = after.select("path, rect, circle, ellipse, polygon")
            beforeShapes.zip(afterShapes).forEach { (old, new) ->
                for (attribute in listOf("d", "x", "y", "width", "height", "r", "rx", "ry", "cx", "cy", "transform", "points")) {
                    assertEquals(old.attr(attribute), new.attr(attribute))
                }
                if (fixture["outline"] == false) {
                    assertEquals(old.attr("fill"), new.attr("fill"))
                    assertEquals(before.selectFirst("svg")!!.attr("style"), after.selectFirst("svg")!!.attr("style"))
                } else if (fixture["hasBackground"] == true) {
                    assertEquals("#E53935", new.attr("stroke"))
                } else {
                    assertEquals("#E53935", new.attr("fill"))
                }
            }
            after.select("tspan").forEach { assertEquals("#E53935", it.attr("fill")) }
        }
    }

    @Test
    fun doesNotTreatIllustrationOrInvalidXmlAsCountBubble() {
        assertNull(CommentIndicatorSvg.recolor("<svg><path/><text>Cover</text></svg>", "#FF0000"))
        assertNull(CommentIndicatorSvg.recolor("<svg><image href='cover.png'/></svg>", "#FF0000"))
        assertNull(CommentIndicatorSvg.recolor("not svg", "#FF0000"))
        assertNull(CommentIndicatorSvg.recolor("<svg><path/><text>1</text><text>2</text></svg>", "#FF0000"))
        assertNull(CommentIndicatorSvg.recolor("<svg><path/><text>1</text><image href='cover.png'/></svg>", "#FF0000"))
    }

    @Test
    fun oldThemeDefaultsToSourceAndNewColorSurvivesSerialization() {
        val oldJson = """{"themeName":"test","isNightTheme":false,"primaryColor":"#123456",
            "accentColor":"#123456","backgroundColor":"#FFFFFF","bottomBackground":"#FFFFFF",
            "transparentNavBar":true,"backgroundImgBlur":0}"""
        val old = GSON.fromJsonObject<ThemeConfig.Config>(oldJson).getOrThrow()
        assertNull(old.commentIndicatorColor)
        val custom = old.copy(commentIndicatorColor = "#FF123456")
        assertNotEquals(old, custom)
        val restored = GSON.fromJsonObject<ThemeConfig.Config>(GSON.toJson(custom)).getOrThrow()
        assertEquals(custom, restored)
        assertEquals("#FF123456", restored.toMap()["commentIndicatorColor"])
    }
}
