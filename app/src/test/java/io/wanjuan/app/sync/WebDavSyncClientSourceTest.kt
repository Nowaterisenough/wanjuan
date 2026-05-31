package io.wanjuan.app.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WebDavSyncClientSourceTest {

    @Test
    fun clientUsesSyncV1AndJsonContentType() {
        val source = File("app/src/main/java/io/wanjuan/app/sync/remote/WebDavSyncClient.kt").readText()
        val appWebDavSource = File("app/src/main/java/io/wanjuan/app/help/AppWebDav.kt").readText()
        assertTrue(source.contains("sync/v1/"))
        assertTrue(source.contains("application/json"))
        assertTrue(source.contains("makeAsDir"))
        assertTrue(source.contains("listFiles"))
        assertTrue(source.contains("Sync path must be relative"))
        assertTrue(source.contains("StandardCharsets.UTF_8"))
        assertTrue(source.contains("GSON.fromJsonObject"))
        assertTrue(source.contains("GSON.toJson"))
        assertTrue(source.contains(".delete()"))
        assertTrue(source.contains("fun AppWebDav.newSyncClient()"))
        assertTrue(appWebDavSource.contains("fun syncRootWebDavUrl()"))
    }
}
