package io.wanjuan.app.sync

import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookGroup
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.help.book.isNotShelf
import io.wanjuan.app.sync.merge.BookSyncMerge
import io.wanjuan.app.sync.model.SyncBook
import io.wanjuan.app.sync.model.SyncBookSource

object SyncChangeFingerprint {
    fun of(books: List<Book>, groups: List<BookGroup>, sources: List<BookSource>): String =
        SyncCanonicalJson.hash(listOf(books(books), groups(groups), sources(sources)))

    fun books(books: List<Book>): String = SyncCanonicalJson.hash(
            books.filterNot { it.isNotShelf }.sortedBy { it.bookUrl }.map {
                // Progress/catalog writes have their own triggers; watch shelf membership and metadata here.
                listOf(SyncIds.bookId(it), it.order,
                    BookSyncMerge.shelfHash(SyncBook.from(it, listOf(it.group.toString()))))
            })

    fun groups(groups: List<BookGroup>): String =
        SyncCanonicalJson.hash(groups.filter { it.groupId > 0L }.sortedBy { it.groupId })

    fun sources(sources: List<BookSource>): String =
        SyncCanonicalJson.hash(sources.sortedBy { it.bookSourceUrl }.map(SyncBookSource::from))
}
