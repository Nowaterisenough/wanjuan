package io.wanjuan.app.help.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SourceVerificationHelpSourceTest {

    @Test
    fun browserVerificationIsSharedPerSourceAndRefetchesWaitingUrl() {
        val verificationHelp =
            repoFile("app/src/main/java/io/wanjuan/app/help/source/SourceVerificationHelp.kt")
                .readText()
        val jsExtensions =
            repoFile("app/src/main/java/io/wanjuan/app/help/JsExtensions.kt")
                .readText()

        assertTrue(verificationHelp.contains("browserVerificationStates"))
        assertTrue(verificationHelp.contains("putIfAbsent(source.getKey(), ownerState)"))
        assertTrue(verificationHelp.contains("waitForSharedBrowserVerification"))
        assertTrue(verificationHelp.contains("refetchAfterSharedVerification"))
        assertTrue(verificationHelp.contains("ownerState.latch.countDown()"))
        assertFalse(
            "Browser verification should not serialize all callers and then open one window per stale response",
            verificationHelp.contains("@Synchronized\n    fun getVerificationResult")
        )

        assertTrue(jsExtensions.contains("refetchAfterSharedVerification = { refetchUrl ->"))
        assertTrue(jsExtensions.contains("AnalyzeUrl(refetchUrl"))
        assertTrue(jsExtensions.contains("val analyzeUrl = this as? AnalyzeUrl"))
        assertTrue(jsExtensions.contains("headerMapF = analyzeUrl?.headerMap"))
    }

    @Test
    fun cloudflareBrowserVerificationAutoReturnsWhenChallengeAlreadyPassed() {
        val webViewActivity =
            repoFile("app/src/main/java/io/wanjuan/app/ui/browser/WebViewActivity.kt")
                .readText()
        val webViewModel =
            repoFile("app/src/main/java/io/wanjuan/app/ui/browser/WebViewModel.kt")
                .readText()

        assertTrue(webViewActivity.contains("hasReturnedVerificationResult"))
        assertTrue(webViewActivity.contains("returnVerificationResultAndFinish()"))
        assertTrue(webViewActivity.contains("returnVerificationResultAndFinish(forceCurrentPage = true)"))
        assertTrue(webViewActivity.contains("viewModel.saveVerificationResult(currentWebView, forceCurrentPage)"))
        assertTrue(webViewActivity.contains("viewModel.shouldAutoReturnCloudflarePage(url)"))
        assertTrue(webViewActivity.contains("isCloudflareChallenge = true"))
        assertTrue(webViewActivity.contains("isCloudflareChallenge && viewModel.shouldAutoReturnAfterCloudflareChallenge()"))

        assertTrue(webViewModel.contains("fun shouldAutoReturnCloudflarePage(url: String?)"))
        assertTrue(webViewModel.contains("fun shouldAutoReturnAfterCloudflareChallenge()"))
        assertTrue(webViewModel.contains("forceCurrentPage: Boolean = false"))
        assertTrue(webViewModel.contains("forceCurrentPage || !refetchAfterSuccess"))
        assertTrue(webViewModel.contains("saveCurrentWebViewVerificationResult(webView, success)"))
        assertTrue(webViewModel.contains("return sourceVerificationEnable && refetchAfterSuccess"))
        assertTrue(webViewModel.contains("sourceVerificationEnable && refetchAfterSuccess"))
        assertTrue(webViewModel.contains("intent?.getStringExtra(\"title\")"))
        assertTrue(webViewModel.contains(".contains(\"cloudflare\", ignoreCase = true)"))
        assertTrue(webViewModel.contains("URLUtil.isNetworkUrl(url)"))
    }

    @Test
    fun browserVerificationFallsBackToCurrentWebViewHtmlWhenRefetchIsEmpty() {
        val webViewModel =
            repoFile("app/src/main/java/io/wanjuan/app/ui/browser/WebViewModel.kt")
                .readText()

        assertTrue(webViewModel.contains("saveRefetchedVerificationResult(webView, success)"))
        assertTrue(webViewModel.contains("saveCurrentWebViewVerificationResult(webView, success)"))
        assertTrue(webViewModel.contains("if (result.second.isBlank() || result.second.isCloudflareVerificationBody())"))
        assertTrue(webViewModel.contains("result.second.isCloudflareVerificationBody()"))
        assertTrue(webViewModel.contains("private fun String.isCloudflareVerificationBody()"))
        assertTrue(webViewModel.contains(".onError {"))
        assertTrue(webViewModel.contains("SourceVerificationHelp.setResult(sourceOrigin, result.second, result.first)"))
        assertFalse(
            "Refetch should not save a blank body as a successful verification result",
            webViewModel.contains("SourceVerificationHelp.setResult(sourceOrigin, html ?: \"\", baseUrl)")
        )
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }
}
