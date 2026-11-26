package io.wanjuan.app.sync.remote

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

internal data class GitHubBookFile(val path: String, val sha: String, val size: Long)

internal const val MAX_BOOK_BYTES = 100L * 1024 * 1024

internal fun libraryPathParts(path: String): List<String> = path.split('/').also { parts ->
    require(parts.none { it.isBlank() || it == "." || it == ".." || it.any(Char::isISOControl) }) {
        "书籍目录路径无效"
    }
}

internal fun checkBookSize(size: Long) {
    require(size in 1..MAX_BOOK_BYTES) { "GitHub 书籍文件必须非空，单个文件上限为 100 MiB" }
}

internal fun gitBookSha(file: File): String = file.inputStream().use { gitBookSha(file.length(), it) }

@OptIn(ExperimentalStdlibApi::class)
internal fun gitBookSha(size: Long, input: InputStream): String {
    checkBookSize(size)
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update("blob $size\u0000".toByteArray(Charsets.UTF_8))
    var read = 0L
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        read += count
        check(read <= size) { "书籍文件大小已变化" }
        digest.update(buffer, 0, count)
    }
    check(read == size) { "书籍文件未读取完整" }
    return digest.digest().toHexString()
}
