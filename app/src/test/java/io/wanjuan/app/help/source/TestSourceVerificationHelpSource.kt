package io.wanjuan.app.help.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TestSourceVerificationHelpSource {

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

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }
}
