package io.wanjuan.app.sync

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.sync.remote.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TestGitHubAiConfigInstrumented {
    private class Remote : SyncRemoteStore {
        var encrypted: String? = null
        var writes = 0
        var rejectNextWrite = false
        override suspend fun readForUpdate(path: String): SyncRemoteRead {
            check(path == "settings/ai.json")
            return SyncRemoteRead(encrypted, writes.toString())
        }
        override suspend fun compareAndSetJson(path: String, previous: SyncRemoteRead, json: String): Boolean {
            if (rejectNextWrite) { rejectNextWrite = false; return false }
            if (previous.revision != writes.toString()) return false
            encrypted = json
            writes++
            return true
        }
        override suspend fun uploadJson(relativePath: String, json: String) = error("Expected conditional write")
        override suspend fun ensureDirs() = Unit
        override suspend fun list(relativeDir: String) = emptyList<SyncRemoteFile>()
        override suspend fun downloadJson(relativePath: String): String? = error("Expected versioned read")
        override suspend fun updateBook(id: String, transform: (SyncBookPayload?) -> SyncBookPayload?): SyncBookPayload? = error("Unexpected")
    }

    @Test fun twoDevicesRestoreMergeAndDeleteEncryptedAiSettings() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "ai-sync-test-${UUID.randomUUID()}"
        val a = ctx.getSharedPreferences("$prefix-a", Context.MODE_PRIVATE)
        val b = ctx.getSharedPreferences("$prefix-b", Context.MODE_PRIVATE)
        val remote = Remote()
        val alice = GitHubAiConfigSync(a)
        val bob = GitHubAiConfigSync(b)
        val target = "repo"
        val pass = "test-only password"
        fun provider(id: String) = JSONObject().put("id", id).put("name", id).put("apiKey", "test-only-key-$id")
        try {
            a.edit().putString(PreferKey.gitHubAiSyncPassphrase, pass)
                .putString(PreferKey.aiProviderList, JSONArray().put(provider("one")).toString())
                .putString(PreferKey.aiModelConfigList, """[{"id":"first","providerId":"one","modelId":"model"}]""")
                .putString(PreferKey.aiCurrentModelId, "first").putBoolean(PreferKey.aiAssistantEnabled, true)
                .putStringSet(PreferKey.aiEnabledToolNames, setOf("search"))
                .putString(PreferKey.aiChatSessionList, "private-chat").putString(PreferKey.gitHubSyncToken, "private-token").apply()
            assertEquals(1, alice.sync(target, remote).uploaded)
            val sealed = requireNotNull(remote.encrypted)
            assertFalse(sealed.contains(provider("one").getString("apiKey")))
            assertFalse(sealed.contains(pass))
            val decrypted = AiConfigCipher(pass).open(sealed)
            assertFalse(decrypted.contains("private-chat"))
            assertFalse(decrypted.contains("private-token"))
            b.edit().putString(PreferKey.gitHubAiSyncPassphrase, "incorrect").apply()
            assertTrue(runCatching { bob.sync(target, remote) }.isFailure)
            assertEquals(sealed, remote.encrypted)
            assertFalse(b.contains(PreferKey.aiProviderList))
            b.edit().putString(PreferKey.gitHubAiSyncPassphrase, pass).apply()
            assertEquals(1, bob.sync(target, remote).downloaded)
            assertEquals("first", b.getString(PreferKey.aiCurrentModelId, null))
            assertTrue(b.getBoolean(PreferKey.aiAssistantEnabled, false))
            assertEquals(setOf("search"), b.getStringSet(PreferKey.aiEnabledToolNames, null))
            assertEquals(provider("one").getString("apiKey"),
                JSONArray(b.getString(PreferKey.aiProviderList, "")).getJSONObject(0).getString("apiKey"))
            assertEquals(0, bob.sync(target, remote).uploaded)
            assertEquals(1, remote.writes)
            for ((prefs, id) in listOf(a to "two", b to "three")) {
                prefs.edit().putString(PreferKey.aiProviderList, JSONArray().put(provider("one")).put(provider(id)).toString()).apply()
            }
            alice.sync(target, remote)
            remote.rejectNextWrite = true
            bob.sync(target, remote)
            alice.sync(target, remote)
            assertEquals(3, JSONArray(a.getString(PreferKey.aiProviderList, "")).length())
            a.edit().remove(PreferKey.aiModelConfigList).remove(PreferKey.aiCurrentModelId).apply()
            alice.sync(target, remote)
            bob.sync(target, remote)
            assertFalse(b.contains(PreferKey.aiModelConfigList))
            assertFalse(b.contains(PreferKey.aiCurrentModelId))
        } finally {
            ctx.deleteSharedPreferences("$prefix-a")
            ctx.deleteSharedPreferences("$prefix-b")
        }
    }
}
