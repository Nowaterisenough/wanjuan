package io.wanjuan.app.help.update

import java.math.BigInteger
import java.time.LocalDateTime

internal object ReleaseVersion {
    private val pattern = Regex("\\d+(?:\\.\\d+)+")

    fun fromAssetName(name: String): String = name.replace(Regex("(?i)\\.apk$"), "").split('_')
        .firstOrNull { pattern.matches(it) }.orEmpty()

    fun compare(left: String, right: String): Int {
        val a = parts(left)
        val b = parts(right)
        for (index in 0 until maxOf(a.size, b.size)) {
            val comparison = (a.getOrNull(index) ?: BigInteger.ZERO)
                .compareTo(b.getOrNull(index) ?: BigInteger.ZERO)
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun parts(version: String): List<BigInteger> {
        require(pattern.matches(version)) { "Invalid release version: $version" }
        val parts = version.split('.').toMutableList()
        // Legacy releases use yy.MMddHH; newer ones use yy.MMddHHmm.
        if (parts.size == 3 && parts[1].length == 2 && parts[2].length in setOf(6, 8)) {
            val date = parts[2].padEnd(8, '0')
            if (runCatching {
                    LocalDateTime.of(2000 + parts[1].toInt(), date.take(2).toInt(),
                        date.substring(2, 4).toInt(), date.substring(4, 6).toInt(), date.takeLast(2).toInt())
                }.isSuccess) parts[2] = date
        }
        return parts.map { it.toBigInteger() }
    }
}
