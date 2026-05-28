package io.wanjuan.app.ui.book

import android.content.Context
import android.content.Intent
import io.wanjuan.app.constant.BookSourceType
import io.wanjuan.app.constant.BookType
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.SearchBook
import io.wanjuan.app.ui.book.info.BookInfoActivity
import io.wanjuan.app.ui.video.VideoPlayerActivity

object SearchBookOpenHelper {

    fun open(context: Context, book: SearchBook, isVideo: Boolean) {
        val target = if (isVideo) VideoPlayerActivity::class.java else BookInfoActivity::class.java
        context.startActivity(Intent(context, target).apply {
            putExtra("name", book.name)
            putExtra("author", book.author)
            putExtra("bookUrl", book.bookUrl)
            putExtra("origin", book.origin)
            putExtra("originName", book.originName)
            putExtra("coverUrl", book.coverUrl)
            if (isVideo) {
                putExtra(VideoPlayerActivity.EXTRA_PREPARE_BOOK_INFO, true)
            }
        })
    }

    fun isVideoResult(book: SearchBook, sourceTypeHint: Int? = null): Boolean {
        return book.type and BookType.video > 0 ||
                sourceTypeHint == BookSourceType.video ||
                appDb.bookSourceDao.getBookSource(book.origin)?.bookSourceType == BookSourceType.video
    }
}
