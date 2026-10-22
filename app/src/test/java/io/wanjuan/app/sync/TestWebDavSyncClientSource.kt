package io.wanjuan.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TestWebDavSyncClientSource {

    @Test
    fun clientUsesSyncV1AndJsonContentType() {
        val source = File("app/src/main/java/io/wanjuan/app/sync/remote/WebDavSyncClient.kt").readText()
        val appWebDavSource = File("app/src/main/java/io/wanjuan/app/help/AppWebDav.kt").readText()
        assertTrue(source.contains("sync/v1/"))
        assertTrue(source.contains(".uploadJsonVersioned"))
        assertTrue(File("app/src/main/java/io/wanjuan/app/lib/webdav/WebDav.kt").readText()
            .contains("\"application/json\".toMediaType()"))
        assertTrue(source.contains("makeAsDir"))
        assertTrue(source.contains("ensureDirsMutex.withLock"))
        assertTrue(source.contains("readyEndpoint = endpoint"))
        assertTrue(source.contains("authorizationLoader()"))
        assertTrue(source.contains("listOf(syncRoot, root)"))
        assertTrue(source.contains("ensureParent(relativePath)"))
        assertFalse(source.contains("addAll(resource.listFiles())"))
        assertTrue(source.contains("file.etag"))
        assertTrue(source.contains("listFiles"))
        assertTrue(source.contains("Sync path must be relative"))
        assertTrue(source.contains("StandardCharsets.UTF_8"))
        assertTrue(source.contains("GSON.fromJsonObject"))
        assertTrue(source.contains("GSON.toJson"))
        assertTrue(source.contains(".delete()"))
        assertTrue(source.contains("ObjectNotFoundException"))
        assertTrue(source.contains("return null"))
        assertTrue(source.contains("404:"))
        assertTrue(source.contains("fun AppWebDav.newSyncClient()"))
        assertTrue(appWebDavSource.contains("fun syncRootWebDavUrl()"))
        assertTrue(appWebDavSource.contains("configMutex.withLock"))
        assertTrue(appWebDavSource.contains("requireAuthorization()"))
        assertFalse(appWebDavSource.contains("getBackupNames"))
        assertFalse(appWebDavSource.contains("restoreWebDav"))
        assertFalse(appWebDavSource.contains("backUpWebDav"))
        assertTrue(appWebDavSource.contains("authorizationProvider = { mAuthorization }"))
        assertTrue(appWebDavSource.contains("finally {"))
        assertTrue(appWebDavSource.contains("authorization = mAuthorization"))
    }

    @Test
    fun appWebDavInitializationIsLazyAndCachesFailedConfiguration() {
        val source = File("app/src/main/java/io/wanjuan/app/help/AppWebDav.kt").readText()

        assertFalse(source.contains("import kotlinx.coroutines.runBlocking"))
        assertFalse(Regex("""init\s*\{""").containsMatchIn(source))
        assertTrue(source.contains("initializeConfig(force = false)"))
        assertTrue(source.contains("if (!force && failedConfig == config) return@withLock"))
        assertTrue(source.contains("failedConfig = config"))
    }
}
