package io.wanjuan.app.sync.delta

import com.google.gson.annotations.SerializedName

/** Optional head metadata. Keep it out of DeltaHead, which is embedded in every previous pointer. */
data class DeltaIndexEntry(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("block") val block: String,
    @SerializedName("bytes") val bytes: Int = 0,
    @SerializedName("inline") val inline: String? = null
) {
    fun head() = DeltaHead(timestamp, block)
}

data class DeltaReadIndex(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("recent") val recent: List<DeltaIndexEntry>,
    @SerializedName("previousIndex") val previousIndex: String? = null,
    @SerializedName("indexedThrough") val indexedThrough: Long = 0
)

data class DeltaBlockRead(val block: DeltaBlock, val reused: Boolean, val bytes: Int)
