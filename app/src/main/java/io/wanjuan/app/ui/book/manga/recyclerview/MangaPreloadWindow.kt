package io.wanjuan.app.ui.book.manga.recyclerview

/** Only enqueue pages entering the preload window, instead of re-enqueuing on every scroll pixel. */
internal class MangaPreloadWindow {
    private var items: List<Any>? = null
    private var positions = emptySet<Int>()

    fun clear() {
        items = null
        positions = emptySet()
    }

    fun entering(items: List<Any>, first: Int, last: Int, count: Int, forward: Boolean): List<Any> {
        if (this.items !== items) {
            clear()
            this.items = items
        }
        if (count <= 0 || first < 0 || last < first || items.isEmpty()) {
            positions = emptySet()
            return emptyList()
        }
        val next = if (forward) {
            (last + 1)..minOf(last.toLong() + count, items.lastIndex.toLong()).toInt()
        } else {
            (maxOf(0L, first.toLong() - count).toInt() until first).reversed()
        }.filter { it in items.indices }.toSet()
        val entering = next.filter { it !in positions }.map(items::get)
        positions = next
        return entering
    }
}
