package io.wanjuan.app.ui.book.bookmark

import android.content.Context
import android.net.Uri
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Bookmark
import io.wanjuan.app.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import splitties.init.appCtx

internal object BookmarkMarkdownSync {
    private val prefs by lazy { appCtx.getSharedPreferences("bookmarkMarkdown", Context.MODE_PRIVATE) }
    private val folders by lazy { MutableStateFlow(prefs.getString("folder", null)) }
    val enabled get() = folders.value != null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: Job? = null

    @Synchronized fun start() {
        if (observer?.isActive == true) return
        observer = scope.launch {
            folders.collectLatest { folder ->
                if (folder == null) return@collectLatest
                appDb.bookmarkDao.flowAll().collect { bookmarks ->
                    try {
                        update(Uri.parse(folder), bookmarks)
                    } catch (error: Exception) {
                        currentCoroutineContext().ensureActive()
                        AppLog.put("自动更新 Markdown 摘录失败", error, true)
                    }
                }
            }
        }
    }

    fun select(uri: Uri?) {
        prefs.edit().putString("folder", uri?.toString()).apply()
        folders.value = uri?.toString()
        start()
    }

    internal fun update(uri: Uri, bookmarks: List<Bookmark>) {
        val folder = FileDoc.fromUri(uri, true).createFolderIfNotExist("万卷摘录")
        val ledger = "files-" + MD5Utils.md5Encode(uri.toString())
        val previous = prefs.getStringSet(ledger, emptySet()).orEmpty()
        val current = mutableSetOf<String>()
        for ((book, notes) in bookmarks.groupBy { it.bookName to it.bookAuthor }) {
            val id = MD5Utils.md5Encode(GSON.toJson(listOf(book.first, book.second)))
            val title = book.first.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_").take(48).ifBlank { "摘录" }
            val name = "$title-${id.take(12)}.md"
            val marker = "<!-- wanjuan-bookmarks:$id -->"
            val text = buildString {
                appendLine(marker)
                appendLine("# ${book.first}")
                appendLine(book.second)
                for (note in notes) {
                    appendLine(); appendLine("## ${note.chapterName}"); appendLine()
                    note.bookText.lineSequence().forEach { appendLine("> $it") }
                    appendLine(); appendLine(note.content)
                }
            }
            val existing = folder.find(name)
            val oldText = existing?.readText()
            if (oldText != null && oldText.lineSequence().firstOrNull() != marker) continue
            if (oldText != text) (existing ?: folder.createFileIfNotExist(name)).writeText(text)
            current.add(name)
        }
        for (name in previous - current) {
            folder.find(name)?.let { file ->
                val marker = file.readText().lineSequence().firstOrNull().orEmpty()
                val id = Regex("<!-- wanjuan-bookmarks:([a-fA-F0-9]{32}) -->").matchEntire(marker)?.groupValues?.get(1)
                if (id != null && name.endsWith("-${id.take(12)}.md")) file.delete()
            }
        }
        prefs.edit().putStringSet(ledger, current).apply()
    }
}
