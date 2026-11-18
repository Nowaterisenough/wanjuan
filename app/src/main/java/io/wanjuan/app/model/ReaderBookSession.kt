package io.wanjuan.app.model

import io.wanjuan.app.data.dao.BookDao
import io.wanjuan.app.data.entities.Book

/** The reader keeps a mutable book. Only persist settings or rule data edited in this session. */
internal class ReaderBookSession(val book: Book) {
    private var savedConfig = book.readConfig?.copy() ?: Book.ReadConfig()
    private var savedCharset = book.charset
    private var savedVariable = book.variable

    // Called on the readers' serial save executor. A queued save retains its own session.
    fun save(dao: BookDao) {
        with(book) {
            if (dao.saveReaderPosition(bookUrl, durChapterIndex, durChapterPos, durChapterTitle,
                    durChapterTime, syncTime) == 0) {
                // A newer synced position already won. Only record the visit.
                dao.touchRecentRead(bookUrl, durChapterTime)
            }
            val currentConfig = readConfig ?: Book.ReadConfig()
            if (currentConfig != savedConfig) {
                val snapshot = currentConfig.copy()
                dao.saveReaderConfig(bookUrl, snapshot)
                savedConfig = snapshot
            }
            if (charset != savedCharset) {
                dao.saveReaderCharset(bookUrl, charset)
                savedCharset = charset
            }
            if (variable != savedVariable) {
                dao.saveReaderVariable(bookUrl, variable)
                savedVariable = variable
            }
        }
    }
}
