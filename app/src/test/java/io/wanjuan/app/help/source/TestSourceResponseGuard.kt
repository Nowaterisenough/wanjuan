package io.wanjuan.app.help.source

import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.exception.SourceAccessException
import io.wanjuan.app.help.http.StrResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TestSourceResponseGuard {
    @Test
    fun normalPagesWithChallengeScriptsDoNotTriggerVerification() = runBlocking {
        val response = StrResponse("https://example.org/list", "<title>Library</title><script src='/cdn-cgi/challenge-platform/script.js'></script>")
        assertSame(response, SourceResponseGuard.ensureContent(response) { error("Unexpected verification") })
    }

    @Test
    fun challengeIsResolvedBeforeItsBodyReachesSourceRules() = runBlocking {
        val response = StrResponse("https://example.org/list", challenge).apply { putCallTime(42) }
        val result = SourceResponseGuard.ensureContent(response) { url ->
            assertEquals(response.url, url)
            "https://example.org/library" to "<title>Library</title><article>Book</article>"
        }
        assertEquals("https://example.org/library", result.url)
        assertTrue(result.body.orEmpty().contains("<article>Book</article>"))
        assertEquals(42, result.callTime)
    }

    @Test
    fun explicitNetworkBlocksDoNotOpenAnUnsolvableVerificationLoop() = runBlocking {
        val body = "<title>Attention Required! | Cloudflare</title><h1 data-translate='block_headline'>Blocked</h1>"
        val failure = runCatching {
            SourceResponseGuard.ensureContent(StrResponse("https://example.org/list", body)) {
                error("A hard block cannot be solved by a browser challenge")
            }
        }.exceptionOrNull()
        assertTrue(failure is SourceAccessException)
    }

    @Test
    fun unfinishedVerificationIsAnErrorInsteadOfAnEmptyBookList() = runBlocking {
        for (body in listOf("", challenge)) {
            val failure = runCatching {
                SourceResponseGuard.ensureContent(StrResponse("https://example.org/list", challenge)) {
                    "https://example.org/list" to body
                }
            }.exceptionOrNull()
            assertTrue(failure is NoStackTraceException)
        }
    }

    private val challenge = "<title>Just a moment...</title><form id='challenge-form'></form>"
}
