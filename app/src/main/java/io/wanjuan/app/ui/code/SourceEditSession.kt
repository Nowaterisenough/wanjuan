package io.wanjuan.app.ui.code

import android.content.Intent
import android.util.AtomicFile
import splitties.init.appCtx
import java.io.File
import java.util.UUID

/** Keep large source fields and their working copy out of Binder transactions. */
internal object SourceEditSession {
    internal const val EXTRA = "sourceEditSession"
    private val directory get() = File(appCtx.cacheDir, "source-editor").apply { mkdirs() }

    fun start(intent: Intent, text: String) {
        val now = System.currentTimeMillis()
        directory.listFiles()?.filter { now - it.lastModified() > 7 * 86_400_000L }?.forEach { it.delete() }
        val id = UUID.randomUUID().toString()
        write(File(directory, "$id.original"), text)
        intent.putExtra(EXTRA, id)
    }

    private fun file(intent: Intent, suffix: String): File? {
        val id = intent.getStringExtra(EXTRA) ?: return null
        if (runCatching { UUID.fromString(id).toString() }.getOrNull() != id) return null
        return File(directory, "$id.$suffix")
    }

    fun original(intent: Intent) = file(intent, "original")?.readText()
    fun draft(intent: Intent) = file(intent, "working")?.takeIf { it.exists() }?.readText()
    fun checkpoint(intent: Intent, text: String) { file(intent, "working")?.let { write(it, text) } }
    fun result(intent: Intent, result: Intent, text: String) {
        if (file(intent, "original") == null) result.putExtra("text", text) else {
            checkpoint(intent, text)
            result.putExtra(EXTRA, intent.getStringExtra(EXTRA))
        }
    }

    fun consume(intent: Intent): String? {
        val value = draft(intent) ?: return intent.getStringExtra("text")
        file(intent, "working")?.delete()
        file(intent, "original")?.delete()
        return value
    }

    fun saveForm(id: String, json: String) = write(File(directory, "$id.form"), json)
    fun readForm(id: String): String? = File(directory, "$id.form").takeIf { it.isFile }?.readText()
    fun removeForm(id: String) { File(directory, "$id.form").delete() }

    private fun write(file: File, text: String) {
        val target = AtomicFile(file)
        val out = target.startWrite()
        try {
            out.write(text.toByteArray(Charsets.UTF_8))
            target.finishWrite(out)
        } catch (error: Exception) {
            target.failWrite(out)
            throw error
        }
    }
}
