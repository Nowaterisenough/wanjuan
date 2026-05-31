package io.wanjuan.app.sync

interface SyncClock {
    fun now(): Long
}

object SystemSyncClock : SyncClock {
    override fun now(): Long = System.currentTimeMillis()
}
