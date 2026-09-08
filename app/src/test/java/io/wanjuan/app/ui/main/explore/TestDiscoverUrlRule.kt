package io.wanjuan.app.ui.main.explore

import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TestDiscoverUrlRule {
    @Test
    fun returnedNavigationUrlIsRetainedAndTheRuleCanEvaluateLaterPages() {
        val url = "{\\{ '/catalog?page=' + page }}"
        val script = DiscoverUrlRule.script(url)!!
        fun evaluate(page: Int): String? = DiscoverUrlRule.returnedUrl(
            RhinoScriptEngine.eval(script, ScriptBindings().apply { put("page", page) })
        )
        assertEquals("/catalog?page=1", evaluate(1))
        assertEquals("/catalog?page=2", evaluate(2))
        assertEquals("{{'/catalog?page=' + page}}", DiscoverUrlRule.requestRule(url))
    }

    @Test
    fun actionOnlyResultsAreNotInterpretedAsNavigation() {
        for (value in listOf(null, true, 1, "", "undefined", "null", "[]", "{}")) {
            assertNull(DiscoverUrlRule.returnedUrl(value))
        }
        assertNull(DiscoverUrlRule.script("/catalog?page={{page}}"))
        assertEquals("/catalog?page={{page}}", DiscoverUrlRule.requestRule("/catalog?page={{page}}"))
    }
}
