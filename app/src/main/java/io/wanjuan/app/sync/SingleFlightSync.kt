package io.wanjuan.app.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Shares one in-flight execution between overlapping callers.
 *
 * A later call starts a new execution only after the current one has completed. This prevents
 * lifecycle, network and manual refresh triggers from building an unbounded queue of identical
 * full WebDAV syncs.
 */
internal class SingleFlightSync<T>(
    private val scope: CoroutineScope,
    private val action: suspend () -> T
) {
    private val lock = Any()
    private var active: Deferred<T>? = null

    fun start(): Deferred<T> = synchronized(lock) {
        active?.takeUnless { it.isCompleted } ?: scope.async(start = CoroutineStart.LAZY) {
            action()
        }.also { task ->
            active = task
            task.invokeOnCompletion {
                synchronized(lock) {
                    if (active === task) {
                        active = null
                    }
                }
            }
            task.start()
        }
    }
}
