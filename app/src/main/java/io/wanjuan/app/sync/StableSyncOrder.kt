package io.wanjuan.app.sync

object StableSyncOrder {
    fun <T> merge(
        remoteIds: List<String>,
        localItems: List<T>,
        idOf: (T) -> String,
        orderOf: (T) -> Int
    ): List<T> {
        val byId = localItems.associateBy(idOf)
        return buildList {
            remoteIds.distinct().mapNotNullTo(this) { byId[it] }
            localItems.sortedBy(orderOf).forEach { item ->
                if (none { idOf(it) == idOf(item) }) add(item)
            }
        }
    }
}
