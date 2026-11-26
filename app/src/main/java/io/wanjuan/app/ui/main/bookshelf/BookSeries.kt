package io.wanjuan.app.ui.main.bookshelf

import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.utils.StringUtils
import java.text.Normalizer

data class SeriesKey(val title: String, val author: String? = null)
data class BookSeries(val key: SeriesKey, val books: List<Book>)

internal object ShelfSeries {
    private const val number = "[0-9零〇一二两三四五六七八九十百千]+"
    private val extra = Regex("""^(.+?)[\s·：:（(【\[—_-]*(?:番外|外传|外傳)(?:篇)?(.*)$""")
    private val volume = Regex("""^(.+?)[\s·（(【\[—_-]*(?:第\s*($number)\s*[卷册冊集话話回部]|(?:vol\.|ep\.|episode)\s*([0-9]+))(?:[\s)）】\]:：·—_-].*)?$""", RegexOption.IGNORE_CASE)
    private val numbered = Regex("""^(.+?)[\s_-]+([0-9]{1,3})$""")
    private val extraNumber = Regex(number)
    private data class Part(val title: String, val kind: Int = 0, val order: Int = 0)

    private fun clean(title: String) = title.trim().trim('《', '》', '【', '】', '[', ']').trim()

    private fun parse(title: String): Part {
        val name = Normalizer.normalize(title, Normalizer.Form.NFKC).trim()
        extra.matchEntire(name)?.let {
            val order = extraNumber.find(it.groupValues[2])?.value?.let(StringUtils::stringToInt) ?: 0
            return Part(clean(it.groupValues[1]), 2, order)
        }
        volume.matchEntire(name)?.let {
            return Part(clean(it.groupValues[1]), 1, StringUtils.stringToInt(it.groupValues[2].ifEmpty { it.groupValues[3] }))
        }
        numbered.matchEntire(name)?.let { return Part(clean(it.groupValues[1]), 1, it.groupValues[2].toInt()) }
        return Part(clean(name))
    }

    fun booksInOrder(rows: List<Any>): List<Book> = buildList {
        for (row in rows) when (row) {
            is Book -> add(row)
            is BookSeries -> addAll(row.books)
        }
    }

    fun suggestedTitle(books: List<Book>): String? = books.map { it.seriesName?.takeIf(String::isNotBlank) ?: parse(it.name).title }
        .distinct().singleOrNull()

    /** Fold after shelf sorting/filtering; the stack occupies the first visible member's position. */
    fun fold(books: List<Book>): List<Any> {
        val parts = books.associateWith { parse(it.name) }
        val titles = books.mapNotNullTo(hashSetOf()) { book ->
            book.seriesName ?: parts.getValue(book).takeIf { it.kind > 0 }?.title
        }
        titles.remove("")
        if (titles.isEmpty()) return books
        val authors = books.filter {
            val part = parts.getValue(it)
            (it.seriesName == null || it.seriesName == part.title) && part.kind != 2
        }
            .groupBy({ parts.getValue(it).title }, { it.author.trim() }).mapValues { it.value.toSet() }
        fun key(book: Book): SeriesKey? {
            book.seriesName?.let { return if (it.isBlank()) null else SeriesKey(it) }
            val part = parts.getValue(book)
            if (part.title !in titles) return null
            if (authors[part.title].orEmpty().size <= 1) return SeriesKey(part.title)
            return if (part.kind == 2) null else SeriesKey(part.title, book.author.trim())
        }
        val members = books.groupBy(::key)
        val emitted = hashSetOf<SeriesKey>()
        return buildList {
            for (book in books) {
                val key = key(book)
                if (key == null || members.getValue(key).size < 2) add(book)
                else if (emitted.add(key)) {
                    val sorted = members.getValue(key).sortedWith(compareBy({
                        val part = parts.getValue(it)
                        if (part.kind == 0 && part.title != key.title) 3 else part.kind
                    },
                        { parts.getValue(it).order }, { it.name }, { it.author }))
                    add(BookSeries(key, sorted))
                }
            }
        }
    }
}
