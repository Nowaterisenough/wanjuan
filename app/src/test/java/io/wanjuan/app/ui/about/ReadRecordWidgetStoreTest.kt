package io.wanjuan.app.ui.about

import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonArray
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadRecordWidgetStoreTest {

    @Test
    fun sanitizesLegacyRecentSnapshotsWithNullTextFields() {
        val raw = """
            [
              {
                "bookUrl": "book://local/1",
                "name": null,
                "author": null,
                "lastRead": 42
              }
            ]
        """.trimIndent()

        val snapshots = GSON.fromJsonArray<ReadRecentVisualSnapshot>(raw).getOrThrow()
        val sanitized = sanitizeRecentVisualSnapshots(snapshots)

        assertEquals(1, sanitized.size)
        assertEquals("book://local/1", sanitized.single().bookUrl)
        assertEquals("", sanitized.single().name)
        assertEquals("", sanitized.single().author)
        assertEquals(42L, sanitized.single().lastRead)
    }
}
