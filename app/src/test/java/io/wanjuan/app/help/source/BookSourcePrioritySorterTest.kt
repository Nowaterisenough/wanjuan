package io.wanjuan.app.help.source

import io.wanjuan.app.data.entities.BookSourcePart
import io.wanjuan.app.data.entities.BookSourceShelfStats
import io.wanjuan.app.help.config.DiscoverSourceUseStats
import org.junit.Assert.assertEquals
import org.junit.Test

class BookSourcePrioritySorterTest {

    private val now = 1_700_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun keepsManualOrderWhenNoPrioritySignalsExist() {
        val sources = listOf(
            source("b", "Beta", customOrder = 2),
            source("a", "Alpha", customOrder = 1),
            source("c", "Gamma", customOrder = 3)
        )

        val sorted = BookSourcePrioritySorter.sort(sources, emptyList(), emptyList(), now)

        assertEquals(listOf("Alpha", "Beta", "Gamma"), sorted.map { it.bookSourceName })
    }

    @Test
    fun ranksBehaviorSignalsBeforeManualOrderForAllSourceLists() {
        val sources = listOf(
            source("manual-first", "Manual First", customOrder = 1),
            source("shelf-source", "Shelf Source", customOrder = 9),
            source("discovery-source", "Discovery Source", customOrder = 8)
        )

        val sorted = BookSourcePrioritySorter.sort(
            sources = sources,
            shelfStats = listOf(
                BookSourceShelfStats("shelf-source", shelfCount = 2, lastReadTime = now - 3 * day)
            ),
            useStats = listOf(
                DiscoverSourceUseStats("discovery-source", useCount = 1, lastUseTime = now - day)
            ),
            now = now
        )

        assertEquals(listOf("Shelf Source", "Discovery Source", "Manual First"), sorted.map { it.bookSourceName })
    }

    @Test
    fun trimsSourceUrlBeforeMatchingStats() {
        val sources = listOf(
            source(" source-with-spaces ", "Source With Spaces", customOrder = 2),
            source("manual-first", "Manual First", customOrder = 1)
        )

        val sorted = BookSourcePrioritySorter.sort(
            sources = sources,
            shelfStats = listOf(
                BookSourceShelfStats("source-with-spaces", shelfCount = 1, lastReadTime = 0)
            ),
            useStats = emptyList(),
            now = now
        )

        assertEquals(listOf("Source With Spaces", "Manual First"), sorted.map { it.bookSourceName })
    }

    private fun source(
        url: String,
        name: String,
        customOrder: Int,
        weight: Int = 0
    ): BookSourcePart {
        return BookSourcePart(
            bookSourceUrl = url,
            bookSourceName = name,
            customOrder = customOrder,
            weight = weight
        )
    }
}
