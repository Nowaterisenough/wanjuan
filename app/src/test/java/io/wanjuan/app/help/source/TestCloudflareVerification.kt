package io.wanjuan.app.help.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class TestCloudflareVerification {
    @Test
    fun verificationOriginsComeFromTheRequestedUrl() {
        for (host in listOf("books.example", "reader.example.org", "library.example.net")) {
            assertEquals(
                setOf("https://$host", "https://www.$host"),
                CloudflareVerification.allowedOrigins("https://$host/chapter?q=1#section")
            )
            assertTrue(CloudflareVerification.allowsRedirect("https://www.$host", "https://$host/chapter"))
            assertTrue(CloudflareVerification.allowsRedirect("https://$host", "https://www.$host/chapter"))
        }
    }

    @Test
    fun verificationAllowsHttpsUpgradeButRejectsOtherSitesPortsAndSchemes() {
        val url = "http://reader.example/chapter"
        assertTrue(CloudflareVerification.allowsRedirect(url, "https://www.reader.example/verified"))
        assertFalse(CloudflareVerification.allowsRedirect(url, "https://other.example/"))
        assertFalse(CloudflareVerification.allowsRedirect(url, "https://reader.example.attacker.test/"))
        assertFalse(CloudflareVerification.allowsRedirect(url, "https://reader.example:8443/"))
        assertFalse(CloudflareVerification.allowsRedirect("https://reader.example/", url))
        assertFalse(CloudflareVerification.allowsRedirect(url, "file:///chapter.html"))
        assertTrue(CloudflareVerification.allowedOrigins("source-id").isEmpty())
        assertTrue(CloudflareVerification.allowedOrigins("javascript:alert(1)").isEmpty())
    }

    @Test
    fun localAddressesKeepTheirExactHostAndPort() {
        for (url in listOf("http://127.0.0.1:8123", "http://[::1]:8123", "http://localhost:8123")) {
            assertEquals(setOf(url), CloudflareVerification.allowedOrigins("$url/chapter"))
            assertTrue(CloudflareVerification.allowsRedirect(url, "$url/verified"))
        }
        assertFalse(CloudflareVerification.allowsRedirect("http://127.0.0.1:8123", "http://localhost:8123"))
    }

    @Test
    fun contentWithPassiveDetectionDoesNotRequireVerification() {
        assertFalse(CloudflareVerification.isChallengeBody("""
            <title>Chapter 1</title><div id="readerContent">Chapter text</div>
            <script src="/cdn-cgi/challenge-platform/scripts/jsd/main.js"></script>
            <script>var cookieName = 'cf_clearance';</script>
        """))
        assertFalse(CloudflareVerification.isChallengeBody("""
            <title>Sign in</title><form><div class="cf-turnstile"></div></form>
            <script src="https://challenges.cloudflare.com/turnstile/v0/api.js"></script>
        """))
    }

    @Test
    fun challengeAndBlockPagesAreNotContent() {
        listOf(
            "<title>Just a moment...</title><p>Waiting</p>",
            "<script>window._cf_chl_opt = {};</script>",
            "<form id='challenge-form'>Verify you are human</form>",
            "<div id='cf-error-details'>Access denied</div>",
            "<title>Attention Required! | Cloudflare</title>"
        ).forEach { assertTrue(it, CloudflareVerification.isChallengeBody(it)) }
    }

    @Test
    fun ordinaryTextAndEmptyResponsesAreNotChallenges() {
        assertFalse(CloudflareVerification.isChallengeBody(null))
        assertFalse(CloudflareVerification.isChallengeBody(""))
        assertFalse(CloudflareVerification.isChallengeBody("<p>Just a moment, she said.</p>"))
        assertFalse(CloudflareVerification.isChallengeBody("""{"message":"cf-ray"}"""))
    }
}
