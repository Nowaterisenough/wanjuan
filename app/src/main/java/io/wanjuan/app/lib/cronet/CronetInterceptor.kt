package io.wanjuan.app.lib.cronet

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.Keep
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.http.CookieManager
import io.wanjuan.app.help.http.CookieManager.cookieJarHeader
import io.wanjuan.app.utils.printOnDebug
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

@Keep
@Suppress("unused")
class CronetInterceptor(private val cookieJar: CookieJar) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain.call().isCanceled()) {
            throw IOException("Canceled")
        }
        val original: Request = chain.request()
        if (!AppConfig.isCronet) return chain.proceed(original)
        return CronetSafety.request(
            cronet = {
                if (!CronetLoader.install() || cronetEngine == null) {
                    null
                } else {
                    val builder = original.newBuilder()
                        .removeHeader("Keep-Alive")
                        .removeHeader("Accept-Encoding")
                    if (!original.isHttps && original.header("User-Agent")?.startsWith("Mozilla", true) == true) {
                        original.header("Referer")?.takeIf { it.startsWith("https:", true) }?.let {
                            builder.header("Referer", "http" + it.substring(5))
                        }
                    }
                    var request = builder.build()
                    if (request.header(cookieJarHeader) != null) request = CookieManager.loadRequest(request)
                    proceedWithCronet(request, chain.call(), chain.readTimeoutMillis())
                }
            },
            fallback = {
                if (chain.call().isCanceled()) throw IOException("Canceled")
                chain.proceed(original)
            },
            onFailure = { it.printOnDebug() }
        )
    }

    @SuppressLint("ObsoleteSdkInt")
    @Throws(IOException::class)
    private fun proceedWithCronet(request: Request, call: Call, readTimeoutMillis: Int): Response? {
        val callBack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NewCallBack(request, call, readTimeoutMillis)
        } else {
            OldCallback(request, call, readTimeoutMillis)
        }
        buildRequest(request, callBack)?.let {
            return callBack.waitForDone(it)
        }
        return null
    }


    /** Returns a 'Cookie' HTTP request header with all cookies, like `a=b; c=d`. */
    private fun getCookie(url: HttpUrl): String = buildString {
        val cookies = cookieJar.loadForRequest(url)
        cookies.forEachIndexed { index, cookie ->
            if (index > 0) append("; ")
            append(cookie.name).append('=').append(cookie.value)
        }
    }

}
