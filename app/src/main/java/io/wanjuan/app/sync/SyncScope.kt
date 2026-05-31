package io.wanjuan.app.sync

object SyncScope {
    private val applyingRemote = ThreadLocal.withInitial { false }

    val isApplyingRemote: Boolean
        get() = applyingRemote.get() == true

    fun <T> remoteApply(block: () -> T): T {
        val previous = applyingRemote.get()
        applyingRemote.set(true)
        return try {
            block()
        } finally {
            applyingRemote.set(previous)
        }
    }
}
