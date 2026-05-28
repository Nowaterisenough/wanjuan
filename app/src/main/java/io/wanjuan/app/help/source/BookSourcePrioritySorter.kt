package io.wanjuan.app.help.source

import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.BookSourcePart
import io.wanjuan.app.data.entities.BookSourceShelfStats
import io.wanjuan.app.help.config.DiscoverSourceUseConfig
import io.wanjuan.app.help.config.DiscoverSourceUseStats
import java.util.concurrent.TimeUnit

object BookSourcePrioritySorter {

    fun sort(
        sources: List<BookSourcePart>,
        shelfStats: Collection<BookSourceShelfStats>,
        useStats: Collection<DiscoverSourceUseStats>,
        now: Long
    ): List<BookSourcePart> {
        return sortByScore(
            sources = sources,
            shelfStats = shelfStats,
            useStats = useStats,
            now = now,
            sourceUrl = BookSourcePart::bookSourceUrl,
            sourceName = BookSourcePart::bookSourceName,
            customOrder = BookSourcePart::customOrder,
            weight = BookSourcePart::weight
        )
    }

    @JvmName("sortBookSources")
    fun sort(
        sources: List<BookSource>,
        shelfStats: Collection<BookSourceShelfStats>,
        useStats: Collection<DiscoverSourceUseStats>,
        now: Long
    ): List<BookSource> {
        return sortByScore(
            sources = sources,
            shelfStats = shelfStats,
            useStats = useStats,
            now = now,
            sourceUrl = BookSource::bookSourceUrl,
            sourceName = BookSource::bookSourceName,
            customOrder = BookSource::customOrder,
            weight = BookSource::weight
        )
    }

    fun sortByPriority(sources: List<BookSourcePart>): List<BookSourcePart> {
        if (sources.size <= 1) {
            return sources
        }
        return runCatching {
            val sourceUrls = sources.map { it.bookSourceUrl }
            val shelfStats = appDb.bookDao.getBookSourceShelfStats()
            val useStats = DiscoverSourceUseConfig.getUseStats(sourceUrls)
            sort(sources, shelfStats, useStats.values, System.currentTimeMillis())
        }.getOrElse {
            sources
        }
    }

    @JvmName("sortBookSourcesByPriority")
    fun sortByPriority(sources: List<BookSource>): List<BookSource> {
        if (sources.size <= 1) {
            return sources
        }
        return runCatching {
            val sourceUrls = sources.map { it.bookSourceUrl }
            val shelfStats = appDb.bookDao.getBookSourceShelfStats()
            val useStats = DiscoverSourceUseConfig.getUseStats(sourceUrls)
            sort(sources, shelfStats, useStats.values, System.currentTimeMillis())
        }.getOrElse {
            sources
        }
    }

    private fun <T> sortByScore(
        sources: List<T>,
        shelfStats: Collection<BookSourceShelfStats>,
        useStats: Collection<DiscoverSourceUseStats>,
        now: Long,
        sourceUrl: (T) -> String,
        sourceName: (T) -> String,
        customOrder: (T) -> Int,
        weight: (T) -> Int
    ): List<T> {
        val shelfStatsByUrl = shelfStats.associateBy { it.origin }
        val useStatsByUrl = useStats.associateBy { it.bookSourceUrl }

        return sources.sortedWith(
            compareByDescending<T> { source ->
                val url = sourceUrl(source).trim()
                score(weight(source), shelfStatsByUrl[url], useStatsByUrl[url], now)
            }
                .thenBy { customOrder(it) }
                .thenBy { sourceName(it) }
        )
    }

    private fun score(
        weight: Int,
        shelfStats: BookSourceShelfStats?,
        useStats: DiscoverSourceUseStats?,
        now: Long
    ): Int {
        val shelfScore = (shelfStats?.shelfCount ?: 0).coerceAtMost(20) * 100
        val readRecencyScore = recencyScore(shelfStats?.lastReadTime ?: 0, now)
        val useScore = (useStats?.useCount ?: 0).coerceAtMost(50) * 20
        val useRecencyScore = recencyScore(useStats?.lastUseTime ?: 0, now)
        val weightScore = if (hasBehaviorSignal(shelfStats, useStats, now)) {
            weight.coerceIn(-20, 20)
        } else {
            0
        }

        return shelfScore + readRecencyScore + useScore + useRecencyScore + weightScore
    }

    private fun hasBehaviorSignal(
        shelfStats: BookSourceShelfStats?,
        useStats: DiscoverSourceUseStats?,
        now: Long
    ): Boolean {
        return (shelfStats?.shelfCount ?: 0) > 0 ||
            isValidTime(shelfStats?.lastReadTime ?: 0, now) ||
            (useStats?.useCount ?: 0) > 0 ||
            isValidTime(useStats?.lastUseTime ?: 0, now)
    }

    private fun recencyScore(time: Long, now: Long): Int {
        if (!isValidTime(time, now)) {
            return 0
        }

        val ageDays = TimeUnit.MILLISECONDS.toDays(now - time)
        return when {
            ageDays <= 7 -> 80
            ageDays <= 30 -> 40
            ageDays <= 90 -> 15
            else -> 0
        }
    }

    private fun isValidTime(time: Long, now: Long): Boolean {
        return time > 0 && time <= now
    }
}
