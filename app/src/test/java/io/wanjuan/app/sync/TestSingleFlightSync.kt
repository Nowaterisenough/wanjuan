package io.wanjuan.app.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class TestSingleFlightSync {

    @Test
    fun overlappingRefreshesRequestExactlyOneTrailingExecution() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var executions = 0
        val sync = SingleFlightSync(this) {
            executions++
            if (executions == 1) {
                entered.complete(Unit)
                release.await()
            }
            executions
        }
        val first = sync.start()
        entered.await()
        repeat(20) { assertSame(first, sync.start(rerunIfActive = true)) }
        release.complete(Unit)

        assertEquals(2, first.await())
        assertEquals(2, executions)
    }

    @Test
    fun overlappingCallsShareOneExecution() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var executions = 0
        val sync = SingleFlightSync(this) {
            executions += 1
            entered.complete(Unit)
            release.await()
            executions
        }

        val first = sync.start()
        entered.await()
        val second = sync.start()

        assertSame(first, second)
        release.complete(Unit)
        assertEquals(1, first.await())
        assertEquals(1, second.await())
        assertEquals(1, executions)
    }

    @Test
    fun callAfterCompletionStartsNewExecution() = runBlocking {
        var executions = 0
        val sync = SingleFlightSync(this) { ++executions }

        val first = sync.start()
        assertEquals(1, first.await())
        val second = sync.start()

        assertNotSame(first, second)
        assertEquals(2, second.await())
        assertEquals(2, executions)
    }
}
