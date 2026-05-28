package io.wanjuan.app.ui.main.explore

import io.wanjuan.app.data.entities.BookSourcePart
import io.wanjuan.app.data.entities.BookSourceShelfStats
import io.wanjuan.app.help.config.DiscoverSourceUseStats
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoverSourceSorterTest {

    private val now = 1_700_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun keepsCustomOrderWhenNoStatsExist() {
        val sources = listOf(
            source("b", "Beta", customOrder = 2),
            source("a", "Alpha", customOrder = 1),
            source("c", "Gamma", customOrder = 3)
        )

        val sorted = DiscoverSourceSorter.sort(sources, emptyList(), emptyList(), now)

        assertEquals(listOf("Alpha", "Beta", "Gamma"), sorted.map { it.bookSourceName })
    }

    @Test
    fun ignoresWeightWhenNoStatsExist() {
        val sources = listOf(
            source("weighted", "Weighted", customOrder = 2, weight = 20),
            source("manual-first", "Manual First", customOrder = 1, weight = -20)
        )

        val sorted = DiscoverSourceSorter.sort(sources, emptyList(), emptyList(), now)

        assertEquals(listOf("Manual First", "Weighted"), sorted.map { it.bookSourceName })
    }

    @Test
    fun ranksBookshelfCountBeforeManualOrder() {
        val sources = listOf(
            source("manual-first", "Manual First", customOrder = 1),
            source("more-books", "More Books", customOrder = 9)
        )

        val sorted = DiscoverSourceSorter.sort(
            sources = sources,
            shelfStats = listOf(
                BookSourceShelfStats("manual-first", shelfCount = 1, lastReadTime = 0),
                BookSourceShelfStats("more-books", shelfCount = 2, lastReadTime = 0)
            ),
            useStats = emptyList(),
            now = now
        )

        assertEquals(listOf("More Books", "Manual First"), sorted.map { it.bookSourceName })
    }

    @Test
    fun ranksRecentBookshelfSourceWhenCountsTie() {
        val sources = listOf(
            source("old-read", "Old Read", customOrder = 1),
            source("recent-read", "Recent Read", customOrder = 2)
        )

        val sorted = DiscoverSourceSorter.sort(
            sources = sources,
            shelfStats = listOf(
                BookSourceShelfStats("old-read", shelfCount = 2, lastReadTime = now - 40 * day),
                BookSourceShelfStats("recent-read", shelfCount = 2, lastReadTime = now - 3 * day)
            ),
            useStats = emptyList(),
            now = now
        )

        assertEquals(listOf("Recent Read", "Old Read"), sorted.map { it.bookSourceName })
    }

    @Test
    fun ranksDiscoveryUseCountWhenBookshelfStatsTie() {
        val sources = listOf(
            source("less-used", "Less Used", customOrder = 1),
            source("more-used", "More Used", customOrder = 2)
        )

        val sorted = DiscoverSourceSorter.sort(
            sources = sources,
            shelfStats = listOf(
                BookSourceShelfStats("less-used", shelfCount = 2, lastReadTime = now - 3 * day),
                BookSourceShelfStats("more-used", shelfCount = 2, lastReadTime = now - 3 * day)
            ),
            useStats = listOf(
                DiscoverSourceUseStats("less-used", useCount = 1, lastUseTime = 0),
                DiscoverSourceUseStats("more-used", useCount = 2, lastUseTime = 0)
            ),
            now = now
        )

        assertEquals(listOf("More Used", "Less Used"), sorted.map { it.bookSourceName })
    }

    @Test
    fun ranksRecentDiscoveryUseWhenCountsTie() {
        val sources = listOf(
            source("old-use", "Old Use", customOrder = 1),
            source("recent-use", "Recent Use", customOrder = 2)
        )

        val sorted = DiscoverSourceSorter.sort(
            sources = sources,
            shelfStats = emptyList(),
            useStats = listOf(
                DiscoverSourceUseStats("old-use", useCount = 3, lastUseTime = now - 40 * day),
                DiscoverSourceUseStats("recent-use", useCount = 3, lastUseTime = now - 3 * day)
            ),
            now = now
        )

        assertEquals(listOf("Recent Use", "Old Use"), sorted.map { it.bookSourceName })
    }

    @Test
    fun ranksDiscoveryUseWhenAdditiveScoreBeatsSingleShelfBook() {
        val sources = listOf(
            source("single-shelf-book", "Single Shelf Book", customOrder = 1),
            source("frequent-discovery-use", "Frequent Discovery Use", customOrder = 2)
        )

        val sorted = DiscoverSourceSorter.sort(
            sources = sources,
            shelfStats = listOf(
                BookSourceShelfStats("single-shelf-book", shelfCount = 1, lastReadTime = 0)
            ),
            useStats = listOf(
                DiscoverSourceUseStats("frequent-discovery-use", useCount = 6, lastUseTime = 0)
            ),
            now = now
        )

        assertEquals(listOf("Frequent Discovery Use", "Single Shelf Book"), sorted.map { it.bookSourceName })
    }

    @Test
    fun matchesStatsWithTrimmedSourceUrl() {
        val sources = listOf(
            source(" source-with-spaces ", "Source With Spaces", customOrder = 2),
            source("manual-first", "Manual First", customOrder = 1)
        )

        val sorted = DiscoverSourceSorter.sort(
            sources = sources,
            shelfStats = listOf(
                BookSourceShelfStats("source-with-spaces", shelfCount = 1, lastReadTime = 0)
            ),
            useStats = emptyList(),
            now = now
        )

        assertEquals(listOf("Source With Spaces", "Manual First"), sorted.map { it.bookSourceName })
    }

    @Test
    fun combinesBookshelfAndDiscoverySignalsIntoOneScore() {
        val sources = listOf(
            source("two-shelf-books", "Two Shelf Books", customOrder = 1),
            source("mixed-signals", "Mixed Signals", customOrder = 2)
        )

        val sorted = DiscoverSourceSorter.sort(
            sources = sources,
            shelfStats = listOf(
                BookSourceShelfStats("two-shelf-books", shelfCount = 2, lastReadTime = 0),
                BookSourceShelfStats("mixed-signals", shelfCount = 1, lastReadTime = 0)
            ),
            useStats = listOf(
                DiscoverSourceUseStats("mixed-signals", useCount = 2, lastUseTime = now - 3 * day)
            ),
            now = now
        )

        assertEquals(listOf("Mixed Signals", "Two Shelf Books"), sorted.map { it.bookSourceName })
    }

    @Test
    fun usesNameAfterScoreAndManualOrderTie() {
        val sources = listOf(
            source("zeta", "Zeta", customOrder = 1),
            source("alpha", "Alpha", customOrder = 1)
        )

        val sorted = DiscoverSourceSorter.sort(sources, emptyList(), emptyList(), now)

        assertEquals(listOf("Alpha", "Zeta"), sorted.map { it.bookSourceName })
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
