package io.wanjuan.app.sync.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncMetadataDao {
    @Query("select * from sync_metadata where objectType = :objectType and objectId = :objectId")
    fun get(objectType: String, objectId: String): SyncMetadata?

    @Query("select * from sync_metadata where dirty = 1")
    fun dirty(): List<SyncMetadata>

    @Query("select * from sync_metadata where objectType = :objectType")
    fun allForType(objectType: String): List<SyncMetadata>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(metadata: SyncMetadata)

    @Query("delete from sync_metadata where objectType = :objectType and objectId = :objectId")
    fun delete(objectType: String, objectId: String)

    @Query("delete from sync_metadata")
    fun deleteAll()

    @Query("""update sync_metadata set lastSyncedHash = null,
        remoteFileModifiedAt = 0, remoteUpdatedAt = 0, remoteUpdatedByDeviceId = null,
        dirty = case when deletedAt is null then 1 else dirty end
        where objectType in (:objectTypes)""")
    fun resetRemoteState(objectTypes: Set<String>)

    @Query(
        """update sync_metadata
        set dirty = 0, lastSyncedHash = :hash
        where objectType = :objectType and objectId = :objectId"""
    )
    fun markClean(objectType: String, objectId: String, hash: String?)
}
