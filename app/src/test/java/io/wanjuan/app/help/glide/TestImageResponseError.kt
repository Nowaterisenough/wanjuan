package io.wanjuan.app.help.glide

import io.wanjuan.app.exception.SourceAccessException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestImageResponseError {
    @Test
    fun blockedImageGetsAnActionableErrorWithoutConsumingTheResponse() {
        val body = "<title>Attention Required! | Cloudflare</title><h1 data-translate='block_headline'>Blocked</h1>"
        val response = response(403, body, "text/html")
        assertTrue(ImageResponseError.from(response) is SourceAccessException)
        assertEquals(body, response.body.string())
    }

    @Test
    fun challengePagesReturnedWithSuccessStatusAreNotDecodedAsImages() {
        val response = response(200, "<title>Just a moment...</title><form id='challenge-form'></form>", "text/html")
        assertTrue(ImageResponseError.from(response) is SourceAccessException)
    }

    @Test
    fun successfulImageDataIsLeftForTheImageDecoder() {
        val response = response(200, "image bytes", "image/webp")
        assertNull(ImageResponseError.from(response))
        assertEquals("image bytes", response.body.string())
    }

    private fun response(code: Int, body: String, contentType: String) = Response.Builder()
        .request(Request.Builder().url("https://example.org/image").build())
        .protocol(Protocol.HTTP_1_1).code(code).message("Test")
        .body(body.toResponseBody(contentType.toMediaType())).build()
}
