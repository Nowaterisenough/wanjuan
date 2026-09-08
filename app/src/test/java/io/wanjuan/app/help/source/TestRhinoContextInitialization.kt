package io.wanjuan.app.help.source

import com.script.rhino.rhinoContext
import com.script.rhino.runScriptWithContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

class TestRhinoContextInitialization {
    @Test
    fun firstSuspendingScriptCreatesTheConfiguredContextAndRestoresNestedCalls() = runBlocking {
        runScriptWithContext {
            val outer = rhinoContext.coroutineContext
            assertNotNull(outer?.get(Job))
            runScriptWithContext(Job()) {
                assertNotNull(rhinoContext.coroutineContext?.get(Job))
            }
            assertSame(outer, rhinoContext.coroutineContext)
        }
    }
}
