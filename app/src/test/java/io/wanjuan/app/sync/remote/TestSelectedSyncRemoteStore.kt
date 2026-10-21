package io.wanjuan.app.sync.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TestSelectedSyncRemoteStore {
    @Test
    fun changingSettingsCannotMixTargetsWithinOneSession() = runBlocking {
        val calls = mutableListOf<String>()
        val first = remote("first", calls)
        val second = remote("second", calls)
        var selected = SyncRemoteTarget("first", first)
        val router = SelectedSyncRemoteStore { selected }
        router.withTarget {
            router.ensureDirs()
            selected = SyncRemoteTarget("second", second)
            router.downloadJson("books/a.json")
            router.uploadJson("books/a.json", "{}")
        }
        router.withTarget { router.ensureDirs() }
        assertEquals(listOf("first:ensure", "first:get", "first:put", "second:ensure"), calls)
    }

    private fun remote(name: String, calls: MutableList<String>) = object : SyncRemoteStore {
        override suspend fun ensureDirs() { calls += "$name:ensure" }
        override suspend fun list(relativeDir: String) = emptyList<SyncRemoteFile>()
        override suspend fun downloadJson(relativePath: String): String? { calls += "$name:get"; return null }
        override suspend fun uploadJson(relativePath: String, json: String) { calls += "$name:put" }
    }
}
