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
}
