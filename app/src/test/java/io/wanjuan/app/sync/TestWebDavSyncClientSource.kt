package io.wanjuan.app.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TestWebDavSyncClientSource {

    @Test
    fun clientUsesSyncV1AndJsonContentType() {
        val source = File("app/src/main/java/io/wanjuan/app/sync/remote/WebDavSyncClient.kt").readText()
        val appWebDavSource = File("app/src/main/java/io/wanjuan/app/help/AppWebDav.kt").readText()
        assertTrue(source.contains("sync/v1/"))
        assertTrue(source.contains("application/json"))
        assertTrue(source.contains("makeAsDir"))
        assertTrue(source.contains("ensureDirsMutex.withLock"))
        assertTrue(source.contains("readyEndpoint = endpoint"))
        assertTrue(source.contains("authorizationLoader()"))
        assertTrue(source.indexOf("syncRoot,") < source.indexOf("root,"))
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
        assertTrue(appWebDavSource.contains("WebDav(rootWebDavUrl, authorization).listFiles()"))
        assertTrue(appWebDavSource.contains("WebDav(rootWebDavUrl + name, requireAuthorization())"))
        assertTrue(appWebDavSource.contains("WebDav(putUrl, requireAuthorization()).upload"))
        assertTrue(appWebDavSource.contains("authorizationProvider = { mAuthorization }"))
        assertTrue(appWebDavSource.contains("finally {"))
        assertTrue(appWebDavSource.contains("authorization = mAuthorization"))
    }
}
