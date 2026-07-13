package io.wanjuan.app.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SyncSchemaTest {

    @Test
    fun databaseIncludesSyncTablesAndDaos() {
        val db = File("app/src/main/java/io/wanjuan/app/data/AppDatabase.kt").readText()
        assertTrue(db.contains("SyncMetadata::class"))
        assertTrue(db.contains("SyncOutbox::class"))
        assertTrue(db.contains("abstract val syncMetadataDao: SyncMetadataDao"))
        assertTrue(db.contains("abstract val syncOutboxDao: SyncOutboxDao"))
    }

    @Test
    fun migrationCreatesSyncTables() {
        val migrations = File("app/src/main/java/io/wanjuan/app/data/DatabaseMigrations.kt").readText()
        assertTrue(migrations.contains("CREATE TABLE IF NOT EXISTS `sync_metadata`"))
        assertTrue(migrations.contains("CREATE TABLE IF NOT EXISTS `sync_outbox`"))
    }

    @Test
    fun database95StoresBidirectionalVersionsAndStableGroupIds() {
        val database = File("app/src/main/java/io/wanjuan/app/data/AppDatabase.kt").readText()
        val migrations = File("app/src/main/java/io/wanjuan/app/data/DatabaseMigrations.kt").readText()
        val group = File("app/src/main/java/io/wanjuan/app/data/entities/BookGroup.kt").readText()
        val metadata = File("app/src/main/java/io/wanjuan/app/sync/local/SyncMetadata.kt").readText()
        val outbox = File("app/src/main/java/io/wanjuan/app/sync/local/SyncOutbox.kt").readText()

        assertTrue(database.contains("version = 95"))
        assertTrue(migrations.contains("migration_94_95"))
        assertTrue(migrations.contains("syncId` TEXT NOT NULL DEFAULT ''"))
        assertTrue(group.contains("var syncId: String = \"\""))
        assertTrue(metadata.contains("val localUpdatedByDeviceId: String?"))
        assertTrue(metadata.contains("val remoteUpdatedByDeviceId: String?"))
        assertTrue(metadata.contains("val deletedByDeviceId: String?"))
        assertTrue(metadata.contains("val remoteFileModifiedAt: Long"))
        assertTrue(outbox.contains("val versionTimestamp: Long"))
        assertTrue(outbox.contains("val versionDeviceId: String"))
    }
}
