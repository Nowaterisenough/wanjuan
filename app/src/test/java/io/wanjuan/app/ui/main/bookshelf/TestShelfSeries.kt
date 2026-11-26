package io.wanjuan.app.ui.main.bookshelf

import io.wanjuan.app.data.entities.Book
import org.junit.Assert.assertEquals
import org.junit.Test

class TestShelfSeries {
    private fun book(title: String, author: String = "甲", series: String? = null) =
        Book(bookUrl = "$title/$author", name = title, author = author, seriesName = series)

    @Test fun crossAuthorExtraFollowsVolumesInReadingOrder() {
        val extra = book("《海岸线》番外：远航", "乙")
        val two = book("海岸线 第２话")
        val ten = book("海岸线 第十话")
        val main = book("海岸线")
        val other = book("月落")
        val rows = ShelfSeries.fold(listOf(extra, other, ten, main, two))
        assertEquals(listOf(main, two, ten, extra), (rows.first() as BookSeries).books)
        assertEquals(listOf(other), rows.drop(1))
        assertEquals(listOf(main, two, ten, extra, other), ShelfSeries.booksInOrder(rows))
    }

    @Test fun ambiguousExtraStaysSeparateUntilAssigned() {
        val a = book("长夜", "甲")
        val b = book("长夜", "乙")
        val extra = book("长夜 番外", "丙")
        assertEquals(listOf(a, b, extra), ShelfSeries.fold(listOf(a, b, extra)))
        val rows = ShelfSeries.fold(listOf(a.copy(seriesName = "长夜"), b, extra.copy(seriesName = "长夜")))
        assertEquals(listOf(a, extra), (rows.first() as BookSeries).books)
        assertEquals(b, rows.last())
    }

    @Test fun separateOverridesRecognitionAndManualSeriesCanSpanUnrelatedTitles() {
        val first = book("雪原 01")
        val next = book("雪原 02", series = "")
        val extra = book("来信", "乙", "雪原")
        val rows = ShelfSeries.fold(listOf(first, next, extra))
        assertEquals(listOf(first, extra), (rows.first() as BookSeries).books)
        assertEquals(next, rows.last())
    }
}
