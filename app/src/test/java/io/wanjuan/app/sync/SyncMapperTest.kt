package io.wanjuan.app.sync

import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.RuleSub
import io.wanjuan.app.sync.mapper.BookSourceSyncMapper
import io.wanjuan.app.sync.mapper.BookSyncMapper
import io.wanjuan.app.sync.mapper.RuleSubSyncMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMapperTest {

    @Test
    fun bookProgressUsesDurChapterTimeAsUpdatedAt() {
        val book = Book(name = "N", author = "A", bookUrl = "u", origin = "o").apply {
            durChapterIndex = 3
            durChapterPos = 99
            durChapterTitle = "C"
            durChapterTime = 123456L
        }
        val payload = BookSyncMapper.toProgressPayload(book, "device")
        assertEquals(123456L, payload.progressUpdatedAt)
        assertEquals(3, payload.durChapterIndex)
        assertEquals(99, payload.durChapterPos)
        assertEquals("C", payload.durChapterTitle)
    }

    @Test
    fun bookPayloadUsesStableBookIdOnly() {
        val book = Book(name = "N", author = "A", bookUrl = "u", origin = "o")
        val payload = BookSyncMapper.toBookPayload(book, "device", shelfUpdatedAt = 10L, catalogUpdatedAt = 20L)
        assertEquals(SyncIds.bookId(book), payload.bookSyncId)
        assertEquals(10L, payload.shelfUpdatedAt)
        assertEquals(20L, payload.catalogUpdatedAt)
    }

    @Test
    fun sourcePayloadUsesBookSourceUrlHash() {
        val source = BookSource(bookSourceUrl = "https://source", bookSourceName = "S")
        val payload = BookSourceSyncMapper.toPayload(source, "device", updatedAt = 10L)
        assertEquals(SyncIds.bookSourceId(source), payload.sourceHash)
        assertEquals("https://source", payload.bookSourceUrl)
    }

    @Test
    fun ruleSubPayloadDoesNotUseLocalIdForHash() {
        val sub = RuleSub(id = 1L, type = 0, url = "https://sub")
        val sameRemote = sub.copy(id = 2L)
        assertEquals(SyncIds.ruleSubId(sub), SyncIds.ruleSubId(sameRemote))
        val payload = RuleSubSyncMapper.toPayload(sub, "device", updatedAt = 10L)
        assertEquals(SyncIds.ruleSubId(sub), payload.ruleSubHash)
    }
}
