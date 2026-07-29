package io.wanjuan.app.model.analyzeRule

import org.junit.Assert.assertEquals
import org.junit.Test

class TestAnalyzeRuleRegression {

    @Test
    fun sourceRuleMakeUpDoesNotMutateCachedTemplate() {
        val analyzeRule = AnalyzeRule()
        val sourceRule = analyzeRule.SourceRule("value##{{result}}##x")

        val first = sourceRule.makeUpRule("a")
        val second = sourceRule.makeUpRule("b")

        assertEquals("value", first.rule)
        assertEquals("a", first.replaceRegex)
        assertEquals("value", second.rule)
        assertEquals("b", second.replaceRegex)
    }

    @Test
    fun splitSourceRuleKeepsJsAndWebJsSourceOrder() {
        val analyzeRule = AnalyzeRule()
        val rules = analyzeRule.splitSourceRule("tag.p@text<js>'js'</js>@webjs:return result")

        assertEquals(
            listOf(AnalyzeRule.Mode.Default, AnalyzeRule.Mode.Js, AnalyzeRule.Mode.WebJs),
            rules.map { it.mode }
        )
        assertEquals("tag.p@text", rules[0].rule)
        assertEquals("'js'", rules[1].rule)
        assertEquals("return result", rules[2].rule)
    }

    @Test
    fun ruleAnalyzerTrimHandlesEmptyAndBlankRules() {
        RuleAnalyzer("").trim()
        RuleAnalyzer("@").trim()
        RuleAnalyzer("   ").trim()
    }

    @Test
    fun cssRuleWithoutLastValueDefaultsToText() {
        val analyzeByJSoup = AnalyzeByJSoup("<div>alpha</div><div>beta</div>")

        assertEquals(listOf("alpha", "beta"), analyzeByJSoup.getStringList("@CSS:div"))
        assertEquals(listOf("alpha", "beta"), analyzeByJSoup.getStringList("@CSS:div@text"))
    }

    @Test
    fun htmlRuleDoesNotMutateElementsUsedByLaterRules() {
        val analyzeByJSoup = AnalyzeByJSoup("<div><script>bad()</script><span>ok</span></div>")

        assertEquals(
            "<div><span>ok</span></div>",
            analyzeByJSoup.getString("div@html")?.replace(Regex(">\\s+<"), "><")
        )
        assertEquals("<script>bad()</script>", analyzeByJSoup.getString("script@html"))
    }

    @Test
    fun analyzeUrlPagePlaceholderClampsLowAndHighPages() {
        assertEquals("https://example.com/a", AnalyzeUrl("https://example.com/<a,b,c>", page = 0).url)
        assertEquals("https://example.com/a", AnalyzeUrl("https://example.com/<a,b,c>", page = 1).url)
        assertEquals("https://example.com/c", AnalyzeUrl("https://example.com/<a,b,c>", page = 3).url)
        assertEquals("https://example.com/c", AnalyzeUrl("https://example.com/<a,b,c>", page = 10).url)
    }

    @Test
    fun analyzeUrlParsesAndNormalizesFallbackImageOptions() {
        val analyzeUrl = AnalyzeUrl(
            "https://primary.example/a.jpg?token=1," +
                "{\"headers\":{\"Referer\":\"https://reader.example/\"}," +
                "\"fallbackUrls\":[\"https://mirror.example/a.jpg?token=1\"," +
                "\"https://primary.example/a.jpg?token=1\",\"bad url\"," +
                "\"https://mirror.example/a.jpg?token=1\"],\"fallbackTimeout\":8000}"
        )

        assertEquals(
            listOf("https://mirror.example/a.jpg?token=1"),
            analyzeUrl.getFallbackUrls()
        )
        assertEquals(8000L, analyzeUrl.getFallbackTimeout())
        assertEquals(
            "https://reader.example/",
            analyzeUrl.headerMap["Referer"]
        )
    }

    @Test
    fun analyzeUrlUsesDefaultFallbackTimeoutAndKeepsUnconfiguredUrlsUnchanged() {
        val invalidTimeout = AnalyzeUrl(
            "https://primary.example/a.jpg,{" +
                "\"fallbackUrls\":[\"https://mirror.example/a.jpg\"]," +
                "\"fallbackTimeout\":\"invalid\"}"
        )
        val unconfigured = AnalyzeUrl("https://primary.example/a.jpg")

        assertEquals(AnalyzeUrl.DEFAULT_FALLBACK_TIMEOUT, invalidTimeout.getFallbackTimeout())
        assertEquals(emptyList<String>(), unconfigured.getFallbackUrls())
        assertEquals(AnalyzeUrl.DEFAULT_FALLBACK_TIMEOUT, unconfigured.getFallbackTimeout())
        assertEquals("https://primary.example/a.jpg", unconfigured.url)
    }
}
