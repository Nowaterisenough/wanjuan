package io.wanjuan.app.ui.browser

import android.app.Application
import android.content.Intent
import android.util.Base64
import android.webkit.URLUtil
import android.webkit.WebView
import io.wanjuan.app.base.BaseViewModel
import io.wanjuan.app.constant.AppConst
import io.wanjuan.app.constant.AppConst.imagePathKey
import io.wanjuan.app.constant.SourceType
import io.wanjuan.app.data.appDb
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.help.http.newCallResponseBody
import io.wanjuan.app.help.http.okHttpClient
import io.wanjuan.app.help.source.SourceHelp
import io.wanjuan.app.help.source.SourceVerificationHelp
import io.wanjuan.app.model.analyzeRule.AnalyzeUrl
import io.wanjuan.app.utils.ACache
import io.wanjuan.app.utils.FileDoc
import io.wanjuan.app.utils.createFileIfNotExist
import io.wanjuan.app.utils.openOutputStream
import io.wanjuan.app.utils.printOnDebug
import io.wanjuan.app.utils.toastOnUi
import org.apache.commons.text.StringEscapeUtils
import java.util.Date
import io.wanjuan.app.data.entities.BaseSource
import io.wanjuan.app.help.webView.WebJsExtensions.Companion.JS_INJECTION2

class WebViewModel(application: Application) : BaseViewModel(application) {
    var source: BaseSource? = null
    var intent: Intent? = null
    var baseUrl: String = ""
    var html: String? = null
    var localHtml: Boolean = false
    val headerMap: HashMap<String, String> = hashMapOf()
    var sourceVerificationEnable: Boolean = false
    var refetchAfterSuccess: Boolean = true
    var sourceName: String = ""
    var sourceOrigin: String = ""
    var sourceType = SourceType.book

    fun initData(
        intent: Intent,
        success: () -> Unit
    ) {
        execute {
            this@WebViewModel.intent = intent
            val url = intent.getStringExtra("url")
                ?: throw NoStackTraceException("url不能为空")
            sourceName = intent.getStringExtra("sourceName") ?: ""
            sourceOrigin = intent.getStringExtra("sourceOrigin") ?: ""
            sourceType = intent.getIntExtra("sourceType", SourceType.book)
            sourceVerificationEnable = intent.getBooleanExtra("sourceVerificationEnable", false)
            refetchAfterSuccess = intent.getBooleanExtra("refetchAfterSuccess", true)
            html = intent.getStringExtra("html")?.let{
                localHtml = true
                val headIndex = it.indexOf("<head", ignoreCase = true)
                if (headIndex >= 0) {
                    val closingHeadIndex = it.indexOf('>', startIndex = headIndex)
                    if (closingHeadIndex >= 0) {
                        val insertPos = closingHeadIndex + 1
                        StringBuilder(it).insert(insertPos, "<script>$JS_INJECTION2</script>").toString()
                    } else {
                        "<head><script>$JS_INJECTION2</script></head>$it"
                    }
                } else {
                    "<head><script>$JS_INJECTION2</script></head>$it"
                }
            }
            source = SourceHelp.getSource(sourceOrigin, sourceType)
            val analyzeUrl = AnalyzeUrl(url, source = source, coroutineContext = coroutineContext)
            baseUrl = analyzeUrl.url
            headerMap.putAll(analyzeUrl.headerMap)
            if (analyzeUrl.isPost()) {
                html = analyzeUrl.getStrResponseAwait(useWebView = false).body
            }
        }.onSuccess {
            success.invoke()
        }.onError {
            context.toastOnUi("error\n${it.localizedMessage}")
            it.printOnDebug()
        }
    }

    fun shouldAutoReturnCloudflarePage(url: String?): Boolean {
        val title = intent?.getStringExtra("title") ?: return false
        return sourceVerificationEnable && refetchAfterSuccess &&
            title.contains("cloudflare", ignoreCase = true) &&
            URLUtil.isNetworkUrl(url)
    }

    fun shouldAutoReturnAfterCloudflareChallenge(): Boolean {
        return sourceVerificationEnable && refetchAfterSuccess
    }

    fun saveImage(webPic: String?, path: String) {
        webPic ?: return
        execute {
            val fileName = "${AppConst.fileNameFormat.format(Date(System.currentTimeMillis()))}.jpg"
            webData2bitmap(webPic)?.let { byteArray ->
                val fileDoc = FileDoc.fromDir(path)
                val picFile = fileDoc.createFileIfNotExist(fileName)
                picFile.openOutputStream().getOrThrow().use {
                    it.write(byteArray)
                }
            } ?: throw Throwable("NULL")
        }.onError {
            ACache.get().remove(imagePathKey)
            context.toastOnUi("保存图片失败:${it.localizedMessage}")
        }.onSuccess {
            context.toastOnUi("保存成功")
        }
    }

    private suspend fun webData2bitmap(data: String): ByteArray? {
        return if (URLUtil.isValidUrl(data)) {
            okHttpClient.newCallResponseBody {
                url(data)
            }.bytes()
        } else {
            Base64.decode(data.split(",").toTypedArray()[1], Base64.DEFAULT)
        }
    }

    fun saveVerificationResult(
        webView: WebView,
        forceCurrentPage: Boolean = false,
        success: () -> Unit
    ) {
        if (!sourceVerificationEnable) {
            return success.invoke()
        }
        if (forceCurrentPage || !refetchAfterSuccess) {
            saveCurrentWebViewVerificationResult(webView, success)
        } else {
            saveRefetchedVerificationResult(webView, success)
        }
    }

    private fun saveRefetchedVerificationResult(webView: WebView, success: () -> Unit) {
        execute {
            val url = intent!!.getStringExtra("url")!!
            val source = appDb.bookSourceDao.getBookSource(sourceOrigin)
            if (html == null) {
                val response = AnalyzeUrl(
                    url,
                    headerMapF = headerMap,
                    source = source,
                    coroutineContext = coroutineContext
                ).getStrResponseAwait(useWebView = false)
                response.url to (response.body ?: "")
            } else {
                baseUrl to html.orEmpty()
            }
        }.onSuccess { result ->
            if (result.second.isBlank() || result.second.isCloudflareVerificationBody()) {
                saveCurrentWebViewVerificationResult(webView, success)
            } else {
                html = result.second
                SourceVerificationHelp.setResult(sourceOrigin, result.second, result.first)
                success.invoke()
            }
        }.onError {
            saveCurrentWebViewVerificationResult(webView, success)
        }
    }

    private fun saveCurrentWebViewVerificationResult(webView: WebView, success: () -> Unit) {
        webView.evaluateJavascript("document.documentElement.outerHTML") {
            execute {
                html = StringEscapeUtils.unescapeJson(it).trim('"')
            }.onSuccess {
                SourceVerificationHelp.setResult(sourceOrigin, html ?: "", webView.url ?: "")
                success.invoke()
            }
        }
    }

    private fun String.isCloudflareVerificationBody(): Boolean {
        return contains("_cf_chl_opt", ignoreCase = true) ||
            contains("cf_chl", ignoreCase = true) ||
            contains("cf-chl", ignoreCase = true) ||
            contains("challenge-platform", ignoreCase = true) ||
            contains("Just a moment", ignoreCase = true) ||
            contains("Checking your browser", ignoreCase = true) ||
            contains("cf_clearance", ignoreCase = true) ||
            contains("cf-ray", ignoreCase = true) ||
            contains("Attention Required", ignoreCase = true)
    }

    fun disableSource(block: () -> Unit) {
        execute {
            SourceHelp.enableSource(sourceOrigin, sourceType, false)
        }.onSuccess {
            block.invoke()
        }
    }

    fun deleteSource(block: () -> Unit) {
        execute {
            SourceHelp.deleteSource(sourceOrigin, sourceType)
        }.onSuccess {
            block.invoke()
        }
    }

}
