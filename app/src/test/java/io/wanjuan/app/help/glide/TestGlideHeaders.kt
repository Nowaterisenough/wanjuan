package io.wanjuan.app.help.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestGlideHeaders {
    @Test
    fun imageRequestsAdvertiseImagesAndKeepSourceHeaders() {
        val sourceHeaders = linkedMapOf(
            "Referer" to "https://reader.example/chapter",
            "User-Agent" to "SourceAgent",
            "Cookie" to "session=fixture"
        )

        val requestHeaders = GlideHeaders(sourceHeaders).getHeaders()

        assertTrue(requestHeaders.getValue("Accept").split(',').contains("image/webp"))
        assertTrue(requestHeaders.getValue("Accept").split(',').contains("image/*"))
        sourceHeaders.forEach { (key, value) -> assertEquals(value, requestHeaders[key]) }
        assertFalse(sourceHeaders.containsKey("Accept"))
    }

    @Test
    fun explicitAcceptRemainsAuthoritativeRegardlessOfHeaderCase() {
        for (name in listOf("Accept", "accept", "ACCEPT")) {
            val requestHeaders = GlideHeaders(mapOf(name to "image/png")).getHeaders()
            assertEquals(mapOf(name to "image/png"), requestHeaders)
        }
    }

    @Test
    fun acceptEncodingDoesNotSuppressImageContentNegotiation() {
        val requestHeaders = GlideHeaders(mapOf("Accept-Encoding" to "gzip")).getHeaders()
        assertEquals("gzip", requestHeaders["Accept-Encoding"])
        assertTrue(requestHeaders.containsKey("Accept"))
    }
}
