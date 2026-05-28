package io.wanjuan.app.ui.video

import android.app.Application
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import com.script.rhino.runScriptWithContext
import io.wanjuan.app.base.BaseViewModel
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.RssSource
import io.wanjuan.app.model.VideoPlay
import io.wanjuan.app.ui.login.SourceLoginJsExtensions
import io.wanjuan.app.utils.toastOnUi

class VideoPlayerViewModel(application: Application) : BaseViewModel(application) {
    val upStarMenuData = MutableLiveData<Boolean>()
    fun removeFromBookshelf(success: (() -> Unit)?) {
        execute {
            VideoPlay.book?.let {
                appDb.bookDao.delete(it)
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    fun addFavorite(success: () -> Unit) {
        execute {
            VideoPlay.rssStar ?: VideoPlay.rssRecord?.toStar()?.let {
                appDb.rssStarDao.insert(it)
                VideoPlay.rssStar = it
            }
        }.onSuccess {
            upStarMenuData.postValue(true)
            success.invoke()
        }
    }

    fun updateFavorite(title: String?, group: String?) {
        execute {
            (VideoPlay.rssStar ?: VideoPlay.rssRecord?.toStar())?.let {
                it.title = title ?: it.title
                it.group = group ?: it.group
                appDb.rssStarDao.update(it)
                VideoPlay.rssStar = it
            }
        }.onSuccess {
            upStarMenuData.postValue(true)
        }
    }

    fun delFavorite() {
        execute {
            VideoPlay.rssStar?.let {
                appDb.rssStarDao.delete(it.origin, it.link)
                VideoPlay.rssRecord = it.toRecord()
                VideoPlay.rssStar = null
            }
        }.onSuccess {
            upStarMenuData.postValue(true)
        }
    }

    fun upSource(success: (() -> Unit)? = null) {
        when (val source = VideoPlay.source) {
            is BookSource -> {
                VideoPlay.source = appDb.bookSourceDao.getBookSource(source.getKey())?.also {
                    success?.invoke()
                }
            }
            is RssSource -> {
                VideoPlay.source = appDb.rssSourceDao.getByKey(source.getKey())
            }
        }
    }

    fun onButtonClick(activity: AppCompatActivity, name: String, click: String) {
        val source = VideoPlay.source ?: return
        val book = VideoPlay.book ?: return
        execute {
            val java = SourceLoginJsExtensions(activity, source)
            runScriptWithContext {
                source.evalJS(click) {
                    put("result", null)
                    put("java", java)
                    put("book", book)
                }
            }
        }.onError {
            AppLog.put("${source.getTag()}: ${it.localizedMessage}", it)
            context.toastOnUi("$name click error\n${it.localizedMessage}")
        }
    }
}