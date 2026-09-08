package io.wanjuan.app.help.glide

import com.bumptech.glide.load.HttpException
import io.wanjuan.app.exception.SourceAccessException
import io.wanjuan.app.help.source.CloudflareVerification
import okhttp3.Response

internal object ImageResponseError {
    fun from(response: Response): Exception? {
        if (!response.isSuccessful || response.body.contentType()?.subtype == "html") {
            val body = response.peekBody(64 * 1024).string()
            if (CloudflareVerification.isBlockedBody(body)) {
                return SourceAccessException("图片服务器拒绝了当前网络，请切换网络后重试")
            }
            if (CloudflareVerification.isChallengeBody(body)) {
                return SourceAccessException("图片服务器需要验证，请在书源页面完成验证后重试")
            }
        }
        return if (response.isSuccessful) null else HttpException(response.message, response.code)
    }
}
