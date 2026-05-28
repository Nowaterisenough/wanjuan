package io.wanjuan.app.ui.main.explore

import io.wanjuan.app.data.entities.BookSourcePart
import io.wanjuan.app.data.entities.BookSourceShelfStats
import io.wanjuan.app.help.config.DiscoverSourceUseStats
import io.wanjuan.app.help.source.BookSourcePrioritySorter

internal object DiscoverSourceSorter {

    fun sort(
        sources: List<BookSourcePart>,
        shelfStats: Collection<BookSourceShelfStats>,
        useStats: Collection<DiscoverSourceUseStats>,
        now: Long
    ): List<BookSourcePart> {
        return BookSourcePrioritySorter.sort(sources, shelfStats, useStats, now)
    }
}
