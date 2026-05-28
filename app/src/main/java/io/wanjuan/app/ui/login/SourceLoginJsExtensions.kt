package io.wanjuan.app.ui.login

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.wanjuan.app.R
import io.wanjuan.app.constant.EventBus
import io.wanjuan.app.data.entities.BaseSource
import io.wanjuan.app.data.entities.HttpTTS
import io.wanjuan.app.model.ReadAloud
import io.wanjuan.app.ui.rss.read.RssJsExtensions
import io.wanjuan.app.ui.widget.dialog.BottomWebViewDialog
import io.wanjuan.app.utils.FileUtils
import io.wanjuan.app.utils.postEvent
import io.wanjuan.app.utils.sendToClip
import io.wanjuan.app.utils.showDialogFragment
import io.wanjuan.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import java.io.File
import java.lang.ref.WeakReference

@Suppress("unused")
class SourceLoginJsExtensions(
    activity: AppCompatActivity?,
    source: BaseSource?,
    bookType: Int = 0,
    callback: Callback? = null
) : RssJsExtensions(activity, source, bookType) {
    private val callbackRef: WeakReference<Callback> = WeakReference(callback)
    interface Callback {
        fun upUiData(data: Map<String, Any?>?)
        fun reUiView(deltaUp: Boolean = false)
        fun showBrowser(
            url: String,
            html: String? = null,
            preloadJs: String? = null,
            config: String? = null
        ): Boolean = false
        fun open(
            name: String,
            url: String? = null,
            title: String? = null,
            origin: String? = null
        ): Boolean = false
    }

    fun upLoginData(data: Map<String, Any?>?) {
        callbackRef.get()?.upUiData(data)
    }

    @JvmOverloads
    fun reLoginView(deltaUp: Boolean = false) {
        callbackRef.get()?.reUiView(deltaUp)
    }

    fun refreshExplore() {
        callbackRef.get()?.reUiView()
    }

    override fun onOpen(
        name: String,
        url: String?,
        title: String?,
        origin: String?
    ): Boolean {
        return callbackRef.get()?.open(name, url, title, origin) == true
    }

    fun refreshBookInfo() {
        postEvent(EventBus.REFRESH_BOOK_INFO, true)
    }

    fun refreshBookToc() {
        postEvent(EventBus.REFRESH_BOOK_TOC, true)
    }

    fun refreshContent() {
        postEvent(EventBus.REFRESH_BOOK_CONTENT, true)
    }

    fun copyText(text: String) {
        activityRef.get()?.sendToClip(text)
    }

    fun clearTtsCache() {
        if (getSource() !is HttpTTS) return
        val activity = activityRef.get() ?: return
        activity.lifecycleScope.launch(IO) {
            ReadAloud.upReadAloudClass()
            val ttsFolderPath = "${activity.cacheDir.absolutePath}${File.separator}httpTTS${File.separator}"
            FileUtils.listDirsAndFiles(ttsFolderPath)?.forEach {
                FileUtils.delete(it.absolutePath)
            }
            activity.toastOnUi(R.string.clear_cache_success)
        }
    }

    @JvmOverloads
    fun showBrowser(url: String, html: String? = null, preloadJs: String? = null, config: String? = null) {
        val activity = activityRef.get() ?: return
        val source = getSource() ?: return
        if (callbackRef.get()?.showBrowser(url, html, preloadJs, config) == true) {
            return
        }
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            activity.showDialogFragment(
                BottomWebViewDialog(
                    source.getKey(),
                    bookType,
                    url,
                    html,
                    preloadJs,
                    config
                )
            )
        }
    }

}
