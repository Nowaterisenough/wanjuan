package io.wanjuan.app.help.glide.progress

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaProgressRequestTest {

    @Test
    fun taggedRequestUsesOriginalProgressUrl() {
        val request = Request.Builder()
            .url("https://mirror.example/a.jpg")
            .tag(MangaProgressKey::class.java, MangaProgressKey("https://primary.example/a.jpg"))
            .build()

        assertEquals("https://primary.example/a.jpg", request.mangaProgressUrl())
    }

    @Test
    fun untaggedRequestUsesActualUrl() {
        val request = Request.Builder().url("https://mirror.example/a.jpg").build()

        assertEquals("https://mirror.example/a.jpg", request.mangaProgressUrl())
    }
}
