package io.wanjuan.app.help.glide.progress

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaProgressRequestTest {

    @Test
    fun listenerStartsAndResetsInConnectingState() {
        val events = mutableListOf<List<Long>>()
        val url = "https://primary.example/a.jpg,{\"fallbackUrls\":[]}"
        ProgressManager.addListener(url) { _, percentage, bytesRead, totalBytes ->
            events += listOf(percentage.toLong(), bytesRead, totalBytes)
        }

        ProgressManager.notifyConnecting(url)
        ProgressManager.removeListener(url)

        assertEquals(listOf(0L, 0L, 0L), events.first())
        assertEquals(listOf(0L, 0L, 0L), events.last())
    }

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
