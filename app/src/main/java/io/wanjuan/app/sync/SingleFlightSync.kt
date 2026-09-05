package io.wanjuan.app.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Shares one in-flight execution between overlapping callers.
 *
 * Repeated triggers can request one trailing execution to capture changes made during a run.
 * Callers share its result, so repeated gestures cannot build an unbounded queue.
 */
internal class SingleFlightSync<T>(
    private val scope: CoroutineScope,
    private val action: suspend () -> T
) {
    private val lock = Any()
    private var active: Deferred<T>? = null
    private var rerun = false

    fun start(rerunIfActive: Boolean = false): Deferred<T> = synchronized(lock) {
        active?.takeUnless { it.isCompleted }?.let {
            if (rerunIfActive) rerun = true
            return@synchronized it
        }
        scope.async(start = CoroutineStart.LAZY) {
            var result: T
            do {
                result = action()
                val again = synchronized(lock) {
                    if (rerun) {
                        rerun = false
                        true
                    } else {
                        active = null
                        false
                    }
                }
            } while (again)
            result
        }.also { task ->
            active = task
            task.invokeOnCompletion {
                synchronized(lock) {
                    if (active === task) {
                        active = null
                        rerun = false
                    }
                }
            }
            task.start()
        }
    }
}
