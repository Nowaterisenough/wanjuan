package io.wanjuan.app.help.source

object CloudflareVerification {

    fun isCloudflareTitle(title: String?): Boolean {
        return title?.contains("cloudflare", ignoreCase = true) == true
    }

    fun isChallengeBody(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        return body.contains("_cf_chl_opt", ignoreCase = true) ||
            body.contains("cf_chl", ignoreCase = true) ||
            body.contains("cf-chl", ignoreCase = true) ||
            body.contains("challenge-platform", ignoreCase = true) ||
            body.contains("Just a moment", ignoreCase = true) ||
            body.contains("Checking your browser", ignoreCase = true) ||
            body.contains("cf_clearance", ignoreCase = true) ||
            body.contains("cf-ray", ignoreCase = true) ||
            body.contains("Attention Required", ignoreCase = true) ||
            body.contains("cf-turnstile", ignoreCase = true) ||
            body.contains("challenges.cloudflare.com", ignoreCase = true) ||
            body.contains("cdn-cgi/challenge-platform", ignoreCase = true) ||
            body.contains("turnstile", ignoreCase = true) ||
            body.contains("Verify you are human", ignoreCase = true) ||
            body.contains("Verifying you are human", ignoreCase = true)
    }
}
