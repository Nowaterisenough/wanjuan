package io.wanjuan.app.help.source

import org.jsoup.Jsoup

object CloudflareVerification {

    fun isCloudflareTitle(title: String?): Boolean {
        return title?.contains("cloudflare", ignoreCase = true) == true
    }

    fun isChallengeBody(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        if (body.contains("_cf_chl_opt", ignoreCase = true)) return true
        // Cloudflare also injects challenge-platform scripts into ordinary content pages.
        val document = Jsoup.parse(body)
        val title = document.title()
        return challengeTitles.any { title.startsWith(it, ignoreCase = true) } ||
            document.selectFirst(
                "#challenge-form, #cf-challenge-running, #cf-challenge-stage, #cf-error-details"
            ) != null
    }

    private val challengeTitles = listOf(
        "Just a moment", "Checking your browser", "Attention Required",
        "Verify you are human", "Verifying you are human"
    )
}
