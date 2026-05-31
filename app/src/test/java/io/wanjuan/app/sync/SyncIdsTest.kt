package io.wanjuan.app.sync

import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.RuleSub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncIdsTest {

    @Test
    fun bookIdUsesOriginAndBookUrl() {
        val a = Book(origin = "source-a", bookUrl = "https://book/1", name = "A", author = "B")
        val b = Book(origin = "source-a", bookUrl = "https://book/1", name = "Other", author = "B")
        assertEquals(SyncIds.bookId(a), SyncIds.bookId(b))
        assertEquals("book_a98689827d513d2b9bbedca988421845", SyncIds.bookId(a))
    }

    @Test
    fun sourceIdUsesSourceUrl() {
        val a = BookSource(bookSourceUrl = "https://source", bookSourceName = "S")
        val b = BookSource(bookSourceUrl = "https://source", bookSourceName = "Other")
        assertEquals(SyncIds.bookSourceId(a), SyncIds.bookSourceId(b))
        assertEquals("book-source_a756f50e1d04f06c6682cf311520130c", SyncIds.bookSourceId(a))
    }

    @Test
    fun ruleSubIdUsesTypeAndUrl() {
        val a = RuleSub(type = 0, url = "https://sub")
        val b = RuleSub(type = 1, url = "https://sub")
        assertNotEquals(SyncIds.ruleSubId(a), SyncIds.ruleSubId(b))
        assertEquals("rule-sub_fb4b5952d2690557c18b16875301c312", SyncIds.ruleSubId(a))
        assertEquals("rule-sub_4f338b84688c286d1aed755e0cbb3108", SyncIds.ruleSubId(b))
    }

    @Test
    fun hashIsFileNameSafe() {
        val id = SyncIds.hashKey("prefix", "https://a/b?c=d")
        assertEquals("prefix_7004e504ad4304d8277736925f082efb", id)
        assertTrue(id.matches(Regex("[a-z0-9_\\-]+")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun sourceIdRejectsBlankUrl() {
        SyncIds.bookSourceId(BookSource(bookSourceUrl = ""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun ruleSubIdRejectsBlankUrl() {
        SyncIds.ruleSubId(RuleSub(type = 0, url = ""))
    }
}
