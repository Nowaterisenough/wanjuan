package io.wanjuan.app.model.jsSource

import androidx.annotation.Keep
import com.script.buildScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.help.JsExtensions
import io.wanjuan.app.help.CacheManager
import io.wanjuan.app.help.http.CookieStore
import io.wanjuan.app.model.SharedJsScope
import kotlin.coroutines.CoroutineContext

@Keep
internal class JsSourceEngine(private val source: BookSource, private val context: CoroutineContext, private val batch: io.wanjuan.app.model.BatchContent? = null) : JsExtensions {
    fun cacheContent(chapter: Any?, content: String) = batch?.accept(chapter, content) ?: false

    override fun getSource() = source
    override fun getTag() = source.getTag()

    fun callFunction(name: String, args: List<Pair<String, Any?>>) = invoke(name, args, false)
    fun callFunctionIfExists(name: String, args: List<Pair<String, Any?>>) = invoke(name, args, true)

    private fun invoke(name: String, args: List<Pair<String, Any?>>, optional: Boolean): String? {
        val bindings = buildScriptBindings { b ->
            b["java"] = this
            b["source"] = source
            b["baseUrl"] = source.bookSourceUrl
            b["cache"] = CacheManager
            b["cookie"] = CookieStore
            args.forEachIndexed { index, (key, value) -> b[key] = value; b["__arg$index"] = value }
        }
        val parent = SharedJsScope.getScope(source.jsLib, context)
        val scope = if (parent == null) RhinoScriptEngine.getRuntimeScope(bindings) else bindings.apply { prototype = parent }
        RhinoScriptEngine.eval(requireNotNull(source.mainJs), scope, context)
        val expression = "$name(${args.indices.joinToString { "__arg$it" }})"
        val call = if (optional) "typeof $name === 'function' ? $expression : null" else expression
        return RhinoScriptEngine.eval("(function(v){return v == null ? null : typeof v === 'string' ? v : JSON.stringify(v)})($call)", scope, context)?.toString()
    }
}
