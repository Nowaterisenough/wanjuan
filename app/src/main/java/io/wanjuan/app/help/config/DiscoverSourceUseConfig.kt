package io.wanjuan.app.help.config

import android.content.Context.MODE_PRIVATE
import androidx.core.content.edit
import splitties.init.appCtx

data class DiscoverSourceUseStats(
    val bookSourceUrl: String,
    val useCount: Int,
    val lastUseTime: Long
)

object DiscoverSourceUseConfig {

    private const val MAX_USE_COUNT = 10000
    private const val COUNT_PREFIX = "count:"
    private const val TIME_PREFIX = "time:"

    private val sp = appCtx.getSharedPreferences("DiscoverSourceUseConfig", MODE_PRIVATE)

    fun addUse(
        sourceUrl: String?,
        increment: Int = 1,
        now: Long = System.currentTimeMillis()
    ) {
        val url = sourceUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val countKey = countKey(url)
        val useCount = (sp.getInt(countKey, 0).toLong() + increment.coerceAtLeast(1))
            .coerceAtMost(MAX_USE_COUNT.toLong())
            .toInt()

        sp.edit {
            putInt(countKey, useCount)
            putLong(timeKey(url), now)
        }
    }

    fun getUseStats(sourceUrls: Collection<String>): Map<String, DiscoverSourceUseStats> {
        return sourceUrls.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .mapNotNull { url ->
                val useCount = sp.getInt(countKey(url), 0)
                val lastUseTime = sp.getLong(timeKey(url), 0)
                if (useCount > 0 || lastUseTime > 0) {
                    url to DiscoverSourceUseStats(url, useCount, lastUseTime)
                } else {
                    null
                }
            }
            .toMap()
    }

    fun removeSource(sourceUrl: String) {
        val url = sourceUrl.trim().takeIf { it.isNotEmpty() } ?: return
        sp.edit {
            remove(countKey(url))
            remove(timeKey(url))
        }
    }

    private fun countKey(sourceUrl: String): String {
        return "$COUNT_PREFIX$sourceUrl"
    }

    private fun timeKey(sourceUrl: String): String {
        return "$TIME_PREFIX$sourceUrl"
    }
}
