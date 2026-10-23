package io.wanjuan.app.help.update

import com.google.gson.Gson
import com.google.gson.JsonParser
import io.wanjuan.app.BuildConfig
import io.wanjuan.app.exception.NoStackTraceException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class GitHubReleaseClient(
    private val endpoint: String = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest",
    internal val client: OkHttpClient = newClient(),
    private val totalTimeoutMillis: Long = 45_000
) {
    suspend fun fetchLatest(): GithubRelease = withTimeoutOrNull(totalTimeoutMillis) {
        for (attempt in 0..1) {
            val response = try {
                request()
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                if (attempt == 0 && e !is SSLException) {
                    delay(500)
                    continue
                }
                throw NoStackTraceException(networkError(e))
            }
            if (response.code in setOf(502, 503, 504) && attempt == 0) {
                delay(500)
                continue
            }
            if (response.code !in 200..299) {
                throw NoStackTraceException(httpError(response.code, response.body, response.headers))
            }
            if (response.body.isBlank()) throw NoStackTraceException("GitHub 返回的版本信息为空")
            return@withTimeoutOrNull try {
                Gson().fromJson(response.body, GithubRelease::class.java)
                    ?.takeIf { it.assets != null }
                    ?: throw IllegalArgumentException("Missing release assets")
            } catch (_: Exception) {
                throw NoStackTraceException("GitHub 返回的版本信息格式异常，请稍后重试")
            }
        }
        error("Unreachable")
    } ?: throw NoStackTraceException("检查更新超时（45 秒），请检查 VPN 分流或更换节点后重试")

    private data class Reply(val code: Int, val body: String, val headers: Headers)

    private suspend fun request(): Reply = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(endpoint)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Wanjuan/${BuildConfig.VERSION_NAME}")
            .build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    // Read before resuming so cancellation still closes a stalled response body.
                    val reply = response.use { Reply(it.code, it.body.string(), it.headers) }
                    if (continuation.isActive) continuation.resume(reply)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }

    companion object {
        internal fun newClient(): OkHttpClient = OkHttpClient.Builder()
            // Keep platform DNS, TLS trust and proxy selection: Android routes sockets through VPN/TUN.
            // Do not clone the book-source client or install Cronet/custom Hosts/unsafe TLS here.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        internal fun networkError(error: IOException): String = when (error) {
            is UnknownHostException -> "无法解析 api.github.com，请检查 VPN 的 DNS 和分流设置"
            is SSLException -> "GitHub 安全连接失败，请检查手机时间、证书或 VPN 节点"
            is InterruptedIOException -> "连接 GitHub 超时，请检查 VPN 分流或更换节点后重试"
            else -> "连接 GitHub 失败，请检查网络或 VPN 节点（${error.javaClass.simpleName}）"
        }

        internal fun httpError(code: Int, body: String, headers: Headers): String {
            val message = runCatching {
                JsonParser.parseString(body).asJsonObject.get("message")?.asString.orEmpty()
            }.getOrDefault("")
            if (code == 429 || (code == 403 && (headers["X-RateLimit-Remaining"] == "0" ||
                        message.contains("rate limit", ignoreCase = true)))) {
                val reset = headers["X-RateLimit-Reset"]?.toLongOrNull()
                    ?.takeIf { it in 1..Long.MAX_VALUE / 1000 }
                    ?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it * 1000)) }
                return "GitHub 请求受限（$code），代理出口可能共享额度。" +
                    (reset?.let { "请在 $it 后重试，或更换节点。" } ?: "请稍后重试，或更换节点。")
            }
            return when (code) {
                404 -> "未找到公开的正式发布版本（404），请检查发布仓库配置"
                401, 403 -> "GitHub 拒绝访问版本信息（$code），请检查仓库权限或代理出口"
                in 500..599 -> "GitHub 服务暂时不可用（$code），请稍后重试"
                else -> "获取 GitHub 版本信息失败（HTTP $code）"
            }
        }
    }
}
