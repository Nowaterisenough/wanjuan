package io.wanjuan.app.help.glide.progress

import okhttp3.Request

internal data class MangaProgressKey(val url: String)

internal fun Request.mangaProgressUrl(): String =
    tag(MangaProgressKey::class.java)?.url ?: url.toString()
