package io.wanjuan.app.ui.font

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.LruCache
import io.wanjuan.app.utils.externalFiles
import io.wanjuan.app.utils.getPrefStringSet
import io.wanjuan.app.utils.putPrefStringSet
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Imported copies remain available to books and saved themes even when hidden in the picker. */
object ReaderFontLibrary {
    data class Font(val path: String, val name: String)

    private const val HIDDEN = "readerHiddenFonts"
    private const val MAX_BYTES = 32L * 1024 * 1024
    private val cache = LruCache<String, Typeface>(12)

    fun list(context: Context, current: String): List<Font> {
        val hidden = context.getPrefStringSet(HIDDEN).orEmpty()
        val fonts = File(context.externalFiles, "font").listFiles().orEmpty()
            .filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf") }
            .map { Font(it.absolutePath, it.nameWithoutExtension.substringAfter("__")) }
            .filter { it.path !in hidden || it.path == current }
            .sortedBy { it.name.lowercase() }.toMutableList()
        if (current.isNotBlank() && fonts.none { it.path == current }) {
            fonts.add(0, Font(current, name(context, current)))
        }
        return fonts
    }

    fun name(context: Context, path: String): String {
        if (path.isBlank()) return "系统默认"
        val name = if (path.startsWith("content:")) displayName(context, Uri.parse(path)) else File(path).name
        return name.substringBeforeLast('.').substringAfter("__")
    }

    fun import(context: Context, uri: Uri): Font {
        val original = displayName(context, uri)
        val extension = original.substringAfterLast('.', "").lowercase()
        require(extension in setOf("ttf", "otf")) { "请选择 TTF 或 OTF 字体文件" }
        val directory = File(context.externalFiles, "font").apply { mkdirs() }
        val temp = File.createTempFile("import-", ".tmp", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取字体文件" }
                temp.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_BYTES) { "字体文件不能超过 32 MB" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    require(total > 0) { "字体文件为空" }
                }
            }
            // Validate before publishing the copy; a misleading extension must not enter the library.
            validateFont(temp)
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val safeName = original.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N} _.-]"), "_").take(80).ifBlank { "导入字体" }
            val target = directory.listFiles()?.firstOrNull { it.name.startsWith("${hash}__") }
                ?: File(directory, "${hash}__$safeName.$extension").also {
                    check(temp.renameTo(it)) { "保存字体失败" }
                }
            context.putPrefStringSet(HIDDEN, (context.getPrefStringSet(HIDDEN).orEmpty() - target.absolutePath).toMutableSet())
            return Font(target.absolutePath, target.nameWithoutExtension.substringAfter("__"))
        } finally {
            temp.delete()
        }
    }

    fun hide(context: Context, path: String) {
        context.putPrefStringSet(HIDDEN, (context.getPrefStringSet(HIDDEN).orEmpty() + path).toMutableSet())
    }

    fun restoreHidden(context: Context) = context.putPrefStringSet(HIDDEN, mutableSetOf())

    private fun validateFont(file: File) {
        // Typeface.createFromFile can silently return the default face for malformed files.
        RandomAccessFile(file, "r").use { input ->
            require(input.length() >= 12) { "不是有效的字体文件" }
            require(input.readInt() in setOf(0x00010000, 0x4f54544f, 0x74727565)) { "不是有效的 TTF / OTF 字体" }
            val count = input.readUnsignedShort()
            val directoryEnd = 12L + count * 16L
            require(count > 0 && directoryEnd <= input.length()) { "字体文件已损坏" }
            input.seek(12)
            val tables = mutableMapOf<Int, Pair<Long, Long>>()
            repeat(count) {
                val tag = input.readInt()
                input.readInt()
                val offset = input.readInt().toLong() and 0xffffffffL
                val length = input.readInt().toLong() and 0xffffffffL
                require(offset <= input.length() && length <= input.length() - offset &&
                    (length == 0L || offset >= directoryEnd)) { "字体文件已损坏" }
                tables[tag] = offset to length
            }
            val head = tables[0x68656164]
            require(head != null && head.second >= 54 && tables.containsKey(0x636d6170)) { "字体缺少必要数据" }
            input.seek(head.first + 12)
            require(input.readInt() == 0x5f0f3cf5) { "字体文件已损坏" }
        }
        if (Build.VERSION.SDK_INT >= 29) android.graphics.fonts.Font.Builder(file).build()
        Typeface.createFromFile(file)
    }

    fun typeface(context: Context, path: String): Typeface = synchronized(cache) {
        if (path.isBlank()) return Typeface.DEFAULT
        cache.get(path)?.let { return it }
        val loaded = if (path.startsWith("content:") && Build.VERSION.SDK_INT >= 26) {
            context.contentResolver.openFileDescriptor(Uri.parse(path), "r").use {
                requireNotNull(it) { "字体已不可访问，请重新导入" }
                Typeface.Builder(it.fileDescriptor).build()
            }
        } else {
            Typeface.createFromFile(path)
        }
        cache.put(path, loaded)
        loaded
    }

    private fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()
}
