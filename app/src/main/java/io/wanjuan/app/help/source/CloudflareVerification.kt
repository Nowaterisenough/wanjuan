package io.wanjuan.app.help.source

import org.jsoup.Jsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object CloudflareVerification {

    fun allowedOrigins(url: String): Set<String> {
        val address = url.toHttpUrlOrNull() ?: return emptySet()
        val baseHost = address.host.removePrefix("www.")
        val hosts = if ('.' in baseHost && ':' !in baseHost && baseHost.any { it.isLetter() }) {
            setOf(baseHost, "www.$baseHost")
        } else {
            setOf(address.host)
        }
        val addresses = buildList {
            add(address)
            // Allow a site's canonical www redirect and the standard HTTP-to-HTTPS upgrade.
            if (address.scheme == "http" && address.port == 80) {
                add(address.newBuilder().scheme("https").port(443).build())
            }
        }
        return addresses.flatMap { candidate ->
            hosts.map { candidate.newBuilder().host(it).build().origin() }
        }.toSet()
    }

    fun allowsRedirect(url: String, destination: String): Boolean {
        val address = destination.toHttpUrlOrNull() ?: return false
        return address.origin() in allowedOrigins(url)
    }

    private fun HttpUrl.origin(): String = newBuilder()
        .username("").password("").encodedPath("/").query(null).fragment(null)
        .build().toString().removeSuffix("/")

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
