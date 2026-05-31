package io.wanjuan.app.sync.merge

object SyncMerge {
    fun remoteWins(localUpdatedAt: Long, remoteUpdatedAt: Long): Boolean {
        return remoteUpdatedAt > localUpdatedAt
    }

    fun remoteProgressWins(localUpdatedAt: Long, remoteUpdatedAt: Long): Boolean {
        return remoteWins(localUpdatedAt, remoteUpdatedAt)
    }

    fun tombstoneWins(objectUpdatedAt: Long, deletedAt: Long): Boolean {
        return deletedAt > objectUpdatedAt
    }
}
