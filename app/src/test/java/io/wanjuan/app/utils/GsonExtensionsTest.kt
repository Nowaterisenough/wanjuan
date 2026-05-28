package io.wanjuan.app.utils

import io.wanjuan.app.data.entities.rule.ExploreKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GsonExtensionsTest {

    @Test
    fun fromJsonArrayFiltersNullElementsFromLenientTrailingComma() {
        val json = """
            [
              {
                "title": "All",
                "url": "https://example.org/page/{{page}}"
              },
            ]
        """.trimIndent()

        val result = GSON.fromJsonArray<ExploreKind>(json)

        assertTrue(result.isSuccess)
        assertEquals(
            listOf(ExploreKind(title = "All", url = "https://example.org/page/{{page}}")),
            result.getOrThrow()
        )
    }
}
