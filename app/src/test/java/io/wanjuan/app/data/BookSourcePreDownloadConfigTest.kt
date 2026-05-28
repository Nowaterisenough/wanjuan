package io.wanjuan.app.data

import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookSourcePreDownloadConfigTest {

    @Test
    fun effectivePreDownloadNumFallsBackToGlobalWhenSourceDoesNotDeclareOverride() {
        val source = GSON.fromJsonObject<BookSource>(
            """
            {
              "bookSourceUrl": "https://example.org",
              "bookSourceName": "Example"
            }
            """.trimIndent()
        ).getOrThrow()

        assertNull(source.preDownloadNum)
        assertEquals(10, source.effectivePreDownloadNum(10))
    }

    @Test
    fun effectivePreDownloadNumAllowsSourceToDisablePreDownload() {
        val source = BookSource(preDownloadNum = 0)

        assertEquals(0, source.effectivePreDownloadNum(10))
    }

    @Test
    fun effectivePreDownloadNumUsesPositiveSourceOverride() {
        val source = BookSource(preDownloadNum = 3)

        assertEquals(3, source.effectivePreDownloadNum(10))
    }
}
