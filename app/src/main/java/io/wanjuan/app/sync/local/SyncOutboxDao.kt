package io.wanjuan.app.sync.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncOutboxDao {
    @Query("select * from sync_outbox order by createdAt asc, id asc limit :limit")
    fun pending(limit: Int = 50): List<SyncOutbox>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: SyncOutbox): Long

    @Query("delete from sync_outbox where id = :id")
    fun delete(id: Long)

    @Query("update sync_outbox set attemptCount = attemptCount + 1, lastError = :error where id = :id")
    fun markFailed(id: Long, error: String)

    @Query("delete from sync_outbox where objectType = :objectType and objectId = :objectId")
    fun deleteForObject(objectType: String, objectId: String)

    @Query(
        """select * from sync_outbox
        where objectType = :objectType and objectId = :objectId
        order by id desc limit 1"""
    )
    fun latestForObject(objectType: String, objectId: String): SyncOutbox?
}
