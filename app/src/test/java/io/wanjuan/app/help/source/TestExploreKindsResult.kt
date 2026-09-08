package io.wanjuan.app.help.source

import com.script.rhino.RhinoScriptEngine
import io.wanjuan.app.data.entities.rule.ExploreKind
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonArray
import org.junit.Assert.assertEquals
import org.junit.Test

class TestExploreKindsResult {
    @Test
    fun scriptCanReturnAnArrayWithoutCallingJsonStringify() {
        val rule = RhinoScriptEngine.eval("let prefix = 'Lat'; [{title: prefix + 'est', url: '/latest'}, {title: 'Popular', url: '/popular'}]")
            .toExploreKindsRule()
        assertEquals(listOf(ExploreKind("Latest", "/latest"), ExploreKind("Popular", "/popular")),
            GSON.fromJsonArray<ExploreKind>(rule).getOrThrow())
    }

    @Test
    fun existingStringRulesRemainUnchanged() {
        assertEquals("Latest::/latest", " Latest::/latest ".toExploreKindsRule())
        assertEquals("", null.toExploreKindsRule())
    }
}
