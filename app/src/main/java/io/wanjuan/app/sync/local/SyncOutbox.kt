package io.wanjuan.app.sync.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_outbox")
data class SyncOutbox(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val objectType: String,
    val objectId: String,
    val operation: String,
    val payloadJson: String? = null,
    val createdAt: Long,
    val attemptCount: Int = 0,
    val lastError: String? = null
)
