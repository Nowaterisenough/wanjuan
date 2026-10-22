package io.wanjuan.app.sync.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Entity(tableName = "sync_delta_state", primaryKeys = ["target", "objectType", "objectId"])
data class SyncDeltaState(
    val target: String,
    val objectType: String,
    val objectId: String,
    val remoteJson: String? = null,
    val observedJson: String? = null,
    val pendingId: String? = null,
    val pendingJson: String? = null,
    val pendingDelete: Boolean = false,
    val pendingCreate: Boolean = false,
    val inFlightChange: String? = null,
    val inFlightValue: String? = null
)

@Entity(tableName = "sync_delta_cursor", primaryKeys = ["target"])
data class SyncDeltaCursor(
    val target: String,
    val timestamp: Long = 0,
    val block: String = "",
    val legacyImported: Boolean = false
)

@Entity(tableName = "sync_delta_blob", primaryKeys = ["blobId", "partIndex"])
data class SyncDeltaBlob(val blobId: String, val partIndex: Int, val content: String)

@Dao
interface SyncDeltaDao {
    @Query("select * from sync_delta_state where target = :target and objectType = :type and objectId = :id")
    fun get(target: String, type: String, id: String): SyncDeltaState?

    @Query("select * from sync_delta_state where target = :target")
    fun states(target: String): List<SyncDeltaState>

    @Query("select * from sync_delta_state where target = :target and (pendingId is not null or inFlightChange is not null)")
    fun pending(target: String): List<SyncDeltaState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(state: SyncDeltaState)

    @Query("select * from sync_delta_cursor where target = :target")
    fun cursor(target: String): SyncDeltaCursor?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveCursor(cursor: SyncDeltaCursor)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertParts(parts: List<SyncDeltaBlob>)

    @Query("select * from sync_delta_blob where blobId = :id order by partIndex")
    fun parts(id: String): List<SyncDeltaBlob>

    @Query("""select count(*) from sync_delta_state where remoteJson like :pattern or observedJson like :pattern
        or pendingJson like :pattern or inFlightChange like :pattern or inFlightValue like :pattern""")
    fun references(pattern: String): Int

    @Query("delete from sync_delta_blob where blobId = :id")
    fun deleteParts(id: String)
}
