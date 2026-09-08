package io.wanjuan.app.help.source

import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.exception.SourceAccessException
import io.wanjuan.app.help.http.StrResponse

internal object SourceResponseGuard {
    suspend fun ensureContent(
        response: StrResponse,
        verify: suspend (String) -> Pair<String, String>
    ): StrResponse {
        if (!CloudflareVerification.isChallengeBody(response.body)) return response
        if (CloudflareVerification.isBlockedBody(response.body)) {
            throw SourceAccessException("书源服务器拒绝了当前网络，请切换网络后重试")
        }
        val (url, body) = verify(response.url)
        if (body.isBlank() || CloudflareVerification.isChallengeBody(body)) {
            throw NoStackTraceException("书源验证未完成，请完成验证后重试")
        }
        return StrResponse(url.ifBlank { response.url }, body).apply {
            putCallTime(response.callTime)
        }
    }
}
