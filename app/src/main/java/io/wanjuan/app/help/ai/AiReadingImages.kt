package io.wanjuan.app.help.ai

import android.graphics.Bitmap
import android.util.Base64
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.Glide
import io.wanjuan.app.model.BookCover
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import splitties.init.appCtx
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

object AiReadingImages {
    const val MAX_PAGES = 12

    suspend fun load(urls: List<String>, sourceOrigin: String?): List<String> {
        require(urls.size <= MAX_PAGES)
        var totalBytes = 0
        return urls.map { url ->
            coroutineContext.ensureActive()
            // Reuse the reader's authenticated image loader, never send source cookies/headers to the AI.
            val target = BookCover.loadManga(appCtx, url, sourceOrigin = sourceOrigin)
                .override(1600, 3200).fitCenter().submit()
            try {
                val drawable = runInterruptible(IO) { target.get(45, TimeUnit.SECONDS) }
                val bytes = ByteArrayOutputStream().use { out ->
                    drawable.toBitmap().compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.toByteArray()
                }
                totalBytes += bytes.size
                check(totalBytes <= 8 * 1024 * 1024) { "漫画图像超过 8 MB，请改用当前页解读" }
                "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            } finally {
                Glide.with(appCtx).clear(target)
            }
        }
    }
}
