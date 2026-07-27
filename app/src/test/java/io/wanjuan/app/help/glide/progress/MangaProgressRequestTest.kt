package io.wanjuan.app.help.glide.progress

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.ArrayDeque

class MangaProgressRequestTest {

    @Test
    fun queuedConnectingOnlyDeliversToItsOriginalRegistration() {
        val postedToMain = ArrayDeque<() -> Unit>()
        val registry = ProgressListenerRegistry { action -> postedToMain.addLast(action) }
        val listenerAEvents = mutableListOf<ProgressEvent>()
        val listenerBEvents = mutableListOf<ProgressEvent>()
        var activeEvents = listenerAEvents
        val reusedListener: OnProgressListener = { complete, percentage, bytesRead, totalBytes ->
            activeEvents += ProgressEvent(complete, percentage, bytesRead, totalBytes)
        }
        val model = "https://primary.example/a.jpg?token=query-secret," +
            "{\"fallbackUrls\":[\"https://mirror.example/a.jpg\"]}"

        registry.addListener(model, reusedListener)
        val staleRegistrationConnecting = postedToMain.removeFirst()
        registry.removeListener(model)
        activeEvents = listenerBEvents
        registry.addListener(model, reusedListener)
        val currentRegistrationConnecting = postedToMain.removeFirst()

        staleRegistrationConnecting.invoke()
        assertEquals(emptyList<ProgressEvent>(), listenerAEvents)
        assertEquals(emptyList<ProgressEvent>(), listenerBEvents)

        currentRegistrationConnecting.invoke()
        assertEquals(listOf(ProgressEvent(false, 0, 0, 0)), listenerBEvents)

        registry.notifyConnecting(model)
        val staleCandidateReconnect = postedToMain.removeFirst()
        registry.removeListener(model)
        staleCandidateReconnect.invoke()

        assertEquals(listOf(ProgressEvent(false, 0, 0, 0)), listenerBEvents)
    }

    @Test
    fun taggedFallbackDeliversQueuedReconnectAndUnknownAndKnownLengthProgressInOrder() {
        val postedToMain = ArrayDeque<() -> Unit>()
        val registry = ProgressListenerRegistry { action -> postedToMain.addLast(action) }
        val events = mutableListOf<ProgressEvent>()
        val model = "https://primary.example/a.jpg?token=query-secret," +
            "{\"fallbackUrls\":[\"https://mirror.example/a.jpg\"]}"
        registry.addListener(model) { complete, percentage, bytesRead, totalBytes ->
            events += ProgressEvent(complete, percentage, bytesRead, totalBytes)
        }
        val taggedRequest = Request.Builder()
            .url("https://mirror.example/a.jpg")
            .tag(MangaProgressKey::class.java, MangaProgressKey(model))
            .build()
        val progressKey = taggedRequest.mangaProgressUrl()

        assertNotNull(registry.getProgressListener(model))
        assertNotNull(registry.getProgressListener(model.substringBefore(",{")))
        assertEquals(emptyList<ProgressEvent>(), events)
        postedToMain.removeFirst().invoke()
        registry.notifyConnecting(progressKey)
        assertEquals(listOf(ProgressEvent(false, 0, 0, 0)), events)
        postedToMain.removeFirst().invoke()
        registry.onProgress(progressKey, 7L, -1L)
        registry.onProgress(progressKey, 4L, 10L)
        registry.onProgress(progressKey, 10L, 10L)

        assertEquals(
            listOf(
                ProgressEvent(false, 0, 0, 0),
                ProgressEvent(false, 0, 0, 0),
                ProgressEvent(false, 0, 7, -1),
                ProgressEvent(false, 40, 4, 10),
                ProgressEvent(true, 100, 10, 10),
            ),
            events,
        )
        assertNull(registry.getProgressListener(model))
        registry.notifyConnecting(progressKey)
        postedToMain.removeFirst().invoke()
        assertEquals(5, events.size)
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

    private data class ProgressEvent(
        val complete: Boolean,
        val percentage: Int,
        val bytesRead: Long,
        val totalBytes: Long,
    )
}
