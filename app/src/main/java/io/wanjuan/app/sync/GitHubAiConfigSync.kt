package io.wanjuan.app.sync

import android.content.SharedPreferences
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.sync.model.SyncResult
import io.wanjuan.app.sync.remote.SyncRemoteStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object AiSyncSettings {
    private val lists = setOf(PreferKey.aiProviderList, PreferKey.aiModelConfigList, PreferKey.aiSkillList, PreferKey.aiMcpServerList)
    private val strings = setOf(PreferKey.aiCurrentProviderId, PreferKey.aiCurrentModelId, PreferKey.aiSystemPrompt,
        PreferKey.aiTavilyApiKey, PreferKey.aiTavilyBaseUrl, PreferKey.aiTavilySearchDepth, PreferKey.aiTavilyTopic)
    private val flags = setOf(PreferKey.aiAssistantEnabled, PreferKey.aiShowToolSummary, PreferKey.aiEnterToSend, PreferKey.aiTavilyEnabled)
    val keys = lists + strings + flags + setOf(PreferKey.aiEnabledToolNames, PreferKey.aiTavilyMaxResults)

    // Split lists by stable IDs so independent provider/model edits can merge across devices.
    fun snapshot(values: Map<String, *>): JsonObject = JsonObject().apply {
        for (key in keys) when (val value = values[key]) {
            null -> Unit
            is String -> if (key in lists) {
                for (record in JsonParser.parseString(value).asJsonArray) {
                    val id = record.asJsonObject.get("id").asString
                    require(id.isNotBlank() && !has("$key/$id")) { "AI 配置中存在无效的重复记录" }
                    add("$key/$id", record)
                }
            } else addProperty(key, value)
            is Boolean -> addProperty(key, value)
            is Int -> addProperty(key, value)
            is Set<*> -> add(key, JsonArray().apply { value.filterIsInstance<String>().sorted().forEach(::add) })
        }
    }

    fun fingerprints(settings: JsonObject) = JsonObject().apply {
        settings.entrySet().forEach { (k, v) -> addProperty(k, SyncCanonicalJson.hash(v)) }
    }

    fun merge(base: JsonObject?, local: JsonObject, remote: JsonObject): JsonObject = JsonObject().apply {
        for (key in remote.keySet() + local.keySet()) {
            val l = local[key]
            val r = remote[key]
            val previous = base?.get(key)?.asString
            val value = when {
                key.substringBefore('/') !in keys -> r
                base == null -> r ?: l
                l == r -> l
                r?.let(SyncCanonicalJson::hash) == previous -> l
                else -> r // Remote edits, including deletion, win a simultaneous edit to the same record.
            }
            if (value != null) add(key, value)
        }
    }

    fun writeChanges(editor: SharedPreferences.Editor, before: JsonObject, after: JsonObject) {
        val changed = (before.keySet() + after.keySet()).filter { before[it] != after[it] }.map { it.substringBefore('/') }.toSet()
        for (key in changed.intersect(keys)) {
            val value = after[key]
            when {
                key in lists -> {
                    val records = JsonArray()
                    after.entrySet().filter { it.key.startsWith("$key/") }.forEach { (path, item) ->
                        require(item.asJsonObject.get("id").asString == path.substringAfter('/')) { "AI 配置记录无效" }
                        records.add(item)
                    }
                    if (records.size() == 0) editor.remove(key) else editor.putString(key, records.toString())
                }
                value == null -> editor.remove(key)
                key in strings -> editor.putString(key, value.asString)
                key in flags -> editor.putBoolean(key, value.asBoolean)
                key == PreferKey.aiEnabledToolNames -> editor.putStringSet(key, value.asJsonArray.map { it.asString }.toSet())
                key == PreferKey.aiTavilyMaxResults -> editor.putInt(key, value.asInt)
            }
        }
    }
}

internal class GitHubAiConfigSync(private val prefs: SharedPreferences) {
    private val path = "settings/ai.json"
    private var lastPassword: String? = null
    private var cipher: AiConfigCipher? = null

    // Called under the shared sync mutex, once per pull. The merge baseline and restored values
    // are saved in the same preferences edit, after the remote conditional write succeeds.
    suspend fun sync(target: String, store: SyncRemoteStore): SyncResult {
        val password = prefs.getString(PreferKey.gitHubAiSyncPassphrase, null)
        if (password.isNullOrBlank()) return SyncResult()
        if (lastPassword != password) { cipher = AiConfigCipher(password); lastPassword = password }
        val codec = requireNotNull(cipher)
        val baseKey = "githubAiSyncBase/$target"
        repeat(3) {
            val remoteRead = store.readForUpdate(path)
            val remote = remoteRead.json?.let { JsonParser.parseString(codec.open(it)).asJsonObject } ?: JsonObject()
            val values = prefs.all
            val local = AiSyncSettings.snapshot(values)
            val base = (values[baseKey] as? String)?.let { JsonParser.parseString(it).asJsonObject }
            val merged = if (remoteRead.json == null) local else AiSyncSettings.merge(base, local, remote)
            val upload = merged != remote
            if (upload && !store.compareAndSetJson(path, remoteRead, codec.seal(merged.toString()))) return@repeat
            val hashes = AiSyncSettings.fingerprints(merged)
            if (local == merged && hashes == base) return SyncResult(uploaded = if (upload) 1 else 0)
            // UI edits may have landed during network I/O. Keep these edits pending for the next sync.
            withContext(Dispatchers.Main.immediate) {
                val now = AiSyncSettings.snapshot(prefs.all)
                val rebased = merged.deepCopy()
                for (key in local.keySet() + now.keySet()) if (local[key] != now[key]) {
                    now[key]?.let { rebased.add(key, it) } ?: rebased.remove(key)
                }
                val edit = prefs.edit()
                AiSyncSettings.writeChanges(edit, now, rebased)
                edit.putString(baseKey, hashes.toString()).apply()
            }
            return SyncResult(uploaded = if (upload) 1 else 0, downloaded = if (local != merged) 1 else 0)
        }
        error("AI 配置正在其他设备更新，请再次同步")
    }
}
