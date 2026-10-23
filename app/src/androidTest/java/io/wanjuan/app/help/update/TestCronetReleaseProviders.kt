package io.wanjuan.app.help.update

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Reflection entry points; tools/release-probe verifies the actual R8 APK without test-runtime clashes. */
@RunWith(AndroidJUnit4::class)
class TestCronetReleaseProviders {
    @Test fun nativeProviderAndItsHttpEngineDependencyCanBeConstructed() {
        construct("org.chromium.net.impl.HttpEngineNativeProvider")
        construct("org.chromium.net.impl.NativeCronetProvider")
    }

    @Test fun javaFallbackProviderSurvivesReleaseShrinking() {
        construct("org.chromium.net.impl.JavaCronetProvider")
    }

    private fun construct(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = context.classLoader.loadClass(name)
            .getConstructor(Context::class.java).newInstance(context)
        assertEquals(name, provider.javaClass.name)
    }
}
