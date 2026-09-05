package io.wanjuan.app.help.book

import io.wanjuan.app.constant.BookType
import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookChapter

object BookCatalogUpdate {
    fun markFailed(db: AppDatabase, bookUrl: String) {
        db.runInTransaction {
            db.bookDao.getBook(bookUrl)?.let { current ->
                current.type = current.type or BookType.updateError
                db.bookDao.update(current)
            }
        }
    }
    fun merge(current: Book, before: Book, fetched: Book): Book = current.copy(
        bookUrl = fetched.bookUrl,
        tocUrl = fetched.tocUrl,
        name = if (current.name == before.name) fetched.name else current.name,
        author = if (current.author == before.author) fetched.author else current.author,
        kind = if (current.kind == before.kind) fetched.kind else current.kind,
        coverUrl = if (current.coverUrl == before.coverUrl) fetched.coverUrl else current.coverUrl,
        intro = if (current.intro == before.intro) fetched.intro else current.intro,
        wordCount = if (current.wordCount == before.wordCount) fetched.wordCount else current.wordCount,
        variable = if (current.variable == before.variable) fetched.variable else current.variable,
        latestChapterTitle = fetched.latestChapterTitle,
        latestChapterTime = fetched.latestChapterTime,
        lastCheckTime = fetched.lastCheckTime,
        lastCheckCount = fetched.lastCheckCount,
        totalChapterNum = fetched.totalChapterNum,
        type = current.type and BookType.updateError.inv()
    )

    fun save(db: AppDatabase, before: Book, fetched: Book, chapters: List<BookChapter>): Book? {
        var saved: Book? = null
        db.runInTransaction {
            val current = db.bookDao.getBook(before.bookUrl) ?: return@runInTransaction
            val merged = merge(current, before, fetched)
            if (merged.bookUrl == before.bookUrl) {
                db.bookDao.update(merged)
            } else {
                db.bookDao.replace(current, merged)
            }
            db.bookChapterDao.delByBook(before.bookUrl)
            db.bookChapterDao.insert(*chapters.toTypedArray())
            saved = merged
        }
        return saved
    }
}
