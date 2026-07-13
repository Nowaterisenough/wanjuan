package io.wanjuan.app.sync.model

data class SyncVersion(
    val timestamp: Long,
    val deviceId: String
) : Comparable<SyncVersion> {

    override fun compareTo(other: SyncVersion): Int {
        val timeComparison = timestamp.compareTo(other.timestamp)
        return if (timeComparison != 0) timeComparison else deviceId.compareTo(other.deviceId)
    }
}
