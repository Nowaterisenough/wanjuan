package io.wanjuan.app.help.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestCloudflareVerification {
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
