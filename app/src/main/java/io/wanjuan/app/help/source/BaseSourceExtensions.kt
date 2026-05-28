package io.wanjuan.app.help.source

import io.wanjuan.app.constant.SourceType
import io.wanjuan.app.data.entities.BaseSource
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.RssSource
import io.wanjuan.app.model.SharedJsScope
import org.mozilla.javascript.Scriptable
import kotlin.coroutines.CoroutineContext

fun BaseSource.getShareScope(coroutineContext: CoroutineContext? = null): Scriptable? {
    return SharedJsScope.getScope(jsLib, coroutineContext)
}

fun BaseSource.getSourceType(): Int {
    return when (this) {
        is BookSource -> SourceType.book
        is RssSource -> SourceType.rss
        else -> error("unknown source type: ${this::class.simpleName}.")
    }
}
