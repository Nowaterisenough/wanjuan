package io.wanjuan.app.help.glide

import com.bumptech.glide.load.model.Headers

class GlideHeaders(headers: Map<String, String>) : Headers {

    private val headers = headers.toMutableMap().apply {
        // Some image hosts reject generic requests even when the URL and Referer are valid.
        if (keys.none { it.equals("Accept", ignoreCase = true) }) {
            put("Accept", IMAGE_ACCEPT)
        }
    }

    override fun getHeaders(): MutableMap<String, String> {
        return headers
    }

    companion object {
        private const val IMAGE_ACCEPT = "image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
    }
}
