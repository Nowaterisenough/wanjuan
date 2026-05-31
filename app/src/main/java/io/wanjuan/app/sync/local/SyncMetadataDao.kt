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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(metadata: SyncMetadata)

    @Query("delete from sync_metadata where objectType = :objectType and objectId = :objectId")
    fun delete(objectType: String, objectId: String)
}
