package io.wanjuan.app.sync.local

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "sync_metadata",
    primaryKeys = ["objectType", "objectId"]
)
data class SyncMetadata(
    val objectType: String,
    val objectId: String,
    val localUpdatedAt: Long = 0L,
    val remoteUpdatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val dirty: Boolean = false,
    val lastSyncedHash: String? = null,
    val updatedByDeviceId: String? = null,
    val localUpdatedByDeviceId: String? = null,
    val remoteUpdatedByDeviceId: String? = null,
    val deletedByDeviceId: String? = null,
    @ColumnInfo(defaultValue = "0")
    val remoteFileModifiedAt: Long = 0L
)
