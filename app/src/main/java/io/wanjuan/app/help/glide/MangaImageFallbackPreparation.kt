package io.wanjuan.app.help.glide

import com.script.rhino.runScriptWithContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.CoroutineContext

internal fun decodeMangaImageWithContext(
    coroutineContext: CoroutineContext,
    src: String,
    candidate: DownloadedMangaImage,
    files: MangaImagePreparationFiles,
    decode: (String, ByteArray) -> ByteArray?,
): ByteArray? {
    val decoded = runScriptWithContext(coroutineContext) {
        decode(src, files.read(candidate.file))
    }
    coroutineContext.ensureActive()
    return decoded
}
