package io.wanjuan.app.help.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.http.SSLHelper
import io.wanjuan.app.help.http.okHttpClient
import io.wanjuan.app.utils.defaultSharedPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import org.chromium.net.ExperimentalCronetEngine
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestUpdateNetworking {
    private val prefs = InstrumentationRegistry.getInstrumentation().targetContext.defaultSharedPreferences
    private var previous: Boolean? = null

    @Before fun save() {
        previous = prefs.all[PreferKey.cronet] as? Boolean
    }

    @After fun restore() {
        prefs.edit().apply {
            previous?.let { putBoolean(PreferKey.cronet, it) } ?: remove(PreferKey.cronet)
        }.commit()
    }

    @Test fun disablingCronetTakesEffectAfterTheSharedClientWasCreated() {
        prefs.edit().putBoolean(PreferKey.cronet, true).commit()
        val existing = okHttpClient
        assertTrue(AppConfig.isCronet)
        prefs.edit().putBoolean(PreferKey.cronet, false).commit()
        assertFalse(AppConfig.isCronet)
        assertSame(existing, okHttpClient)
        prefs.edit().putBoolean(PreferKey.cronet, true).commit()
        assertTrue(AppConfig.isCronet)
    }

    @Test fun updateNetworkIsIsolatedEvenWithCronetEnabled() {
        prefs.edit().putBoolean(PreferKey.cronet, true).commit()
        val client = GitHubReleaseClient.newClient()
        assertTrue(AppConfig.isCronet)
        assertTrue(client.interceptors.isEmpty())
        assertSame(Dns.SYSTEM, client.dns)
        assertNull(client.proxy)
        assertNotSame(SSLHelper.unsafeHostnameVerifier, client.hostnameVerifier)
    }

    @Test fun cronetBuilderDiscoversAProviderBeforeNativeLibraryLoading() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(ExperimentalCronetEngine.Builder(context))
    }

    @Test fun publicGitHubReleaseWorksWithCronetEnabled() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveGithub") == "true")
        prefs.edit().putBoolean(PreferKey.cronet, true).commit()
        val release = GitHubReleaseClient().fetchLatest()
        assertFalse(release.isPreRelease)
        assertTrue(release.assets!!.any { it.isValid && ReleaseVersion.fromAssetName(it.name).isNotEmpty() })
    }
}
