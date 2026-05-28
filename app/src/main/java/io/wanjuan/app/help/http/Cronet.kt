package io.wanjuan.app.help.http

import io.wanjuan.app.lib.cronet.CronetInterceptor
import io.wanjuan.app.lib.cronet.CronetLoader
import okhttp3.Interceptor

object Cronet {

    val loader: LoaderInterface? by lazy {
        CronetLoader
    }

    fun preDownload() {
        loader?.preDownload()
    }

    val interceptor: Interceptor? by lazy {
        CronetInterceptor(cookieJar)
    }

    interface LoaderInterface {

        fun install(): Boolean

        fun preDownload()

    }

}