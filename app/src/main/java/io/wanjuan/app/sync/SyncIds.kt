package io.wanjuan.app.sync

import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.RuleSub
import java.security.MessageDigest

object SyncIds {

    fun bookId(book: Book): String {
        val key = when {
            book.origin.isNotBlank() && book.bookUrl.isNotBlank() ->
                "${book.origin}\n${book.bookUrl}"
            book.bookUrl.isNotBlank() ->
                "${book.name}\n${book.author}\n${book.bookUrl}"
            else ->
                "${book.name}\n${book.author}\n${book.originName}"
        }
        return hashKey("book", key)
    }

    fun bookSourceId(source: BookSource): String {
        require(source.bookSourceUrl.isNotBlank()) { "BookSource.bookSourceUrl must not be blank" }
        return hashKey("book-source", source.bookSourceUrl)
    }

    fun ruleSubId(ruleSub: RuleSub): String {
        require(ruleSub.url.isNotBlank()) { "RuleSub.url must not be blank" }
        return hashKey("rule-sub", "${ruleSub.type}\n${ruleSub.url}")
    }

    fun hashKey(prefix: String, key: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "${prefix}_${digest.take(32)}"
    }
}
