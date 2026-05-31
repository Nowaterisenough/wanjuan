package io.wanjuan.app.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SyncRepositorySourceTest {

    @Test
    fun repositoryMarksRemoteApplyScopeAndUpdatesOutbox() {
        val source = File("app/src/main/java/io/wanjuan/app/sync/SyncRepository.kt").readText()
        assertTrue(source.contains("SyncScope.remoteApply"))
        assertTrue(source.contains("runInTransaction"))
        assertTrue(source.contains("syncMetadataDao.get"))
        assertTrue(source.contains("copy("))
        assertTrue(source.contains("syncOutboxDao.pending"))
        assertTrue(source.contains("syncOutboxDao.delete"))
        assertTrue(source.contains("syncOutboxDao.markFailed"))
    }
}
