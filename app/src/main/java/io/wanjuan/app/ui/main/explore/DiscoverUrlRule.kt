package io.wanjuan.app.ui.main.explore

internal object DiscoverUrlRule {
    fun script(url: String): String? {
        val value = url.trim()
        return when {
            value.startsWith("{{") && value.endsWith("}}") -> value.substring(2, value.length - 2)
            value.startsWith("{\\{") && value.endsWith("}}") -> value.substring(3, value.length - 2)
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun requestRule(url: String): String = script(url)?.let { "{{$it}}" } ?: url

    fun returnedUrl(value: Any?): String? = (value as? CharSequence)?.toString()?.trim()?.takeIf {
        it.isNotEmpty() && it != "null" && it != "undefined" &&
            !it.startsWith("[") && !it.startsWith("{") && !it.startsWith("<")
    }
}
