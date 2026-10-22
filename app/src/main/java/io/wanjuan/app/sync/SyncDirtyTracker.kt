package io.wanjuan.app.sync

import io.wanjuan.app.sync.model.SyncObjectType

object SyncObjectSelection {
    val tables = mapOf(
        "books" to setOf(SyncObjectType.Book, SyncObjectType.BookshelfOrder),
        "book_groups" to setOf(SyncObjectType.BookGroup, SyncObjectType.BookGroupOrder, SyncObjectType.Book),
        "book_sources" to setOf(SyncObjectType.BookSource, SyncObjectType.BookSourceOrder),
        "rsssources" to setOf(SyncObjectType.RssSource, SyncObjectType.RssSourceOrder),
        "rulesubs" to setOf(SyncObjectType.RuleSub, SyncObjectType.RuleSubOrder)
    )
    val all = tables.values.flatten().toSet()
}

/** Generations prevent a write arriving during capture from being cleared by that capture. */
class SyncDirtyTracker(initial: Set<String> = SyncObjectSelection.all) {
    private var generation = 0L
    private val dirty = initial.associateWith { generation }.toMutableMap()

    @Synchronized
    fun mark(types: Set<String>) {
        generation++
        types.forEach { dirty[it] = generation }
    }

    fun markTables(tables: Set<String>) = mark(tables.flatMap {
        SyncObjectSelection.tables[it.lowercase()].orEmpty()
    }.toSet())

    fun capture(forced: Set<String> = emptySet(), action: (Set<String>) -> Unit) {
        val snapshot = synchronized(this) {
            (dirty.keys + forced).associateWith { dirty[it] ?: -1L }
        }
        if (snapshot.isEmpty()) return
        action(snapshot.keys)
        synchronized(this) {
            snapshot.forEach { (type, version) -> if (dirty[type] == version) dirty.remove(type) }
        }
    }
}
