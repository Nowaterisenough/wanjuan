package io.wanjuan.app.utils

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.util.Locale

object CommentIndicatorSvg {
    private val optionStart = Regex(",\\s*(?=\\{)")
    private val commentCount = Regex("[0-9]+\\+?")
    private val badgeElements = setOf(
        "svg", "g", "path", "rect", "circle", "ellipse", "polygon", "polyline", "line",
        "text", "tspan", "title", "desc"
    )

    fun isInlineActionSvg(src: String): Boolean {
        if (!src.startsWith("data:image/svg+xml;base64,", ignoreCase = true)) return false
        val start = optionStart.find(src)?.range?.last?.plus(1) ?: return false
        val options = GSON.fromJsonObject<Map<String, String>>(src.substring(start)).getOrNull()
            ?: return false
        return options["style"].equals("text", ignoreCase = true) &&
            listOf("js", "click", "pclick").any { key ->
                !options[key].isNullOrBlank()
            }
    }

    fun recolor(svgText: String, color: String): String? = runCatching {
        val document = Jsoup.parse(svgText, "", Parser.xmlParser())
        val svg = document.children().singleOrNull()?.takeIf { it.tagName() == "svg" }
            ?: return null
        if (svg.getAllElements().any { it.tagName() !in badgeElements }) return null
        val text = svg.select("text").singleOrNull()
            ?.takeIf { commentCount.matches(it.text().trim()) } ?: return null
        val shapes = svg.select("path, rect, circle, ellipse, polygon, polyline, line")
        if (shapes.isEmpty()) return null

        val foregroundFills = text.getAllElements().filter { it.ownText().isNotBlank() }
            .map { it.resolvedFill() }.toSet()
        val shapePaints = shapes.mapNotNull { shape ->
            val stroke = shape.inheritedPaint("stroke")
            when {
                stroke.isNotBlank() && !stroke.equals("none", ignoreCase = true) -> shape to "stroke"
                shape.resolvedFill() in foregroundFills -> shape to "fill"
                else -> null
            }
        }

        text.setPaint("fill", color)
        text.select("tspan").forEach { it.setPaint("fill", color) }
        // Filled shapes can be backgrounds; only tint fills that share the number's original paint.
        shapePaints.forEach { (shape, paint) -> shape.setPaint(paint, color) }
        document.outputSettings().prettyPrint(false)
        svg.outerHtml()
    }.getOrNull()

    private fun Element.styleProperties(): MutableMap<String, String> =
        attr("style").split(';').mapNotNull { declaration ->
            val colon = declaration.indexOf(':')
            if (colon < 0) null else declaration.substring(0, colon).trim() to
                declaration.substring(colon + 1).trim()
        }.toMap(linkedMapOf())

    private fun Element.inheritedPaint(name: String): String =
        generateSequence(this) { it.parent() }
            .map { it.styleProperties()[name] ?: it.attr(name) }
            .firstOrNull { it.isNotBlank() && it != "inherit" }.orEmpty()

    private fun Element.resolvedFill(): String {
        val fill = inheritedPaint("fill").ifBlank { "black" }
        return (if (fill.equals("currentColor", ignoreCase = true)) {
            inheritedPaint("color").ifBlank { "black" }
        } else fill).lowercase(Locale.ROOT).filterNot { it.isWhitespace() }
    }

    private fun Element.setPaint(name: String, color: String) {
        attr(name, color)
        val properties = styleProperties().apply { put(name, color) }
        attr("style", properties.entries.joinToString(";") { "${it.key}:${it.value}" })
    }
}
