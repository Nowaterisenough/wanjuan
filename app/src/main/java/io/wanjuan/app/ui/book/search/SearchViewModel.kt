package io.wanjuan.app.ui.book.search

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.wanjuan.app.base.BaseViewModel
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.BookSourcePart
import io.wanjuan.app.data.entities.SearchBook
import io.wanjuan.app.data.entities.SearchKeyword
import io.wanjuan.app.help.book.isNotShelf
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.model.webBook.SearchModel
import io.wanjuan.app.utils.ConflateLiveData
import io.wanjuan.app.utils.toastOnUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapLatest
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(application: Application) : BaseViewModel(application) {
    val handler = Handler(Looper.getMainLooper())
    val bookshelf: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val upAdapterLiveData = MutableLiveData<String>()
    var searchBookLiveData = ConflateLiveData<List<SearchBook>>(1000)
    val searchScope: SearchScope = SearchScope(AppConfig.searchScope)
    var searchFinishLiveData = MutableLiveData<Boolean>()
    var isSearchLiveData = MutableLiveData<Boolean>()
    val searchSourceStatusLiveData = ConflateLiveData<List<SearchSourceStatus>>(250)
    var searchKey: String = ""
    var hasMore = true
    private var searchID = 0L
    private val sourceStatusLock = Any()
    private val sourceStatuses = linkedMapOf<String, SearchSourceStatus>()
    private val searchModel = SearchModel(viewModelScope, object : SearchModel.CallBack {

        override fun getSearchScope(): SearchScope {
            return searchScope
        }

        override fun onSearchStart() {
            isSearchLiveData.postValue(true)
        }

        override fun onSearchSourcesReset(sources: List<BookSourcePart>) {
            val snapshot = synchronized(sourceStatusLock) {
                sourceStatuses.clear()
                sources.forEach { source ->
                    sourceStatuses[source.bookSourceUrl] =
                        source.toSearchSourceStatus(SearchSourceState.PENDING)
                }
                sourceStatuses.values.toList()
            }
            searchSourceStatusLiveData.postValue(snapshot)
        }

        override fun onSearchSourceFound(source: BookSourcePart, resultCount: Int) {
            val snapshot = synchronized(sourceStatusLock) {
                sourceStatuses[source.bookSourceUrl] = source.toSearchSourceStatus(SearchSourceState.FOUND, resultCount)
                sourceStatuses.values.toList()
            }
            searchSourceStatusLiveData.postValue(snapshot)
        }

        override fun onSearchSourceEmpty(source: BookSourcePart) {
            val snapshot = synchronized(sourceStatusLock) {
                sourceStatuses[source.bookSourceUrl] =
                    source.toSearchSourceStatus(SearchSourceState.EMPTY)
                sourceStatuses.values.toList()
            }
            searchSourceStatusLiveData.postValue(snapshot)
        }

        override fun onSearchSourceFailed(source: BookSourcePart, error: Throwable) {
            val snapshot = synchronized(sourceStatusLock) {
                sourceStatuses[source.bookSourceUrl] = source.toSearchSourceStatus(
                    SearchSourceState.FAILED,
                    errorMessage = error.localizedMessage
                )
                sourceStatuses.values.toList()
            }
            searchSourceStatusLiveData.postValue(snapshot)
        }

        override fun onSearchSuccess(searchBooks: List<SearchBook>) {
            searchBookLiveData.postValue(searchBooks)
        }

        override fun onSearchFinish(isEmpty: Boolean, hasMore: Boolean) {
            this@SearchViewModel.hasMore = hasMore
            isSearchLiveData.postValue(false)
            searchFinishLiveData.postValue(isEmpty)
        }

        override fun onSearchCancel(exception: Throwable?) {
            isSearchLiveData.postValue(false)
            exception?.let {
                context.toastOnUi(it.localizedMessage)
            }
        }

    })

    init {
        execute {
            appDb.bookDao.flowAll().mapLatest { books ->
                val keys = arrayListOf<String>()
                books.filterNot { it.isNotShelf }
                    .forEach {
                        keys.add("${it.name}-${it.author}")
                        keys.add(it.name)
                        keys.add(it.bookUrl)
                    }
                keys
            }.catch {
                AppLog.put("搜索界面获取书籍列表失败\n${it.localizedMessage}", it)
            }.collect {
                bookshelf.clear()
                bookshelf.addAll(it)
                upAdapterLiveData.postValue("isInBookshelf")
            }
        }.onError {
            AppLog.put("加载书架数据失败", it)
        }
    }

    fun isInBookShelf(book: SearchBook): Boolean {
        val name = book.name
        val author = book.author
        val bookUrl = book.bookUrl
        val key = if (author.isNotBlank()) "$name-$author" else name
        return bookshelf.contains(key) || bookshelf.contains(bookUrl)
    }

    /**
     * 开始搜索
     */
    fun search(key: String) {
        execute {
            if (key.isNotEmpty()) {
                searchModel.cancelSearch()
                searchID = System.currentTimeMillis()
                searchBookLiveData.postValue(emptyList())
                searchKey = key
                hasMore = true
            }
            if (searchKey.isEmpty()) {
                return@execute
            }
            searchModel.search(searchID, searchKey)
        }
    }

    /**
     * 停止搜索
     */
    fun stop() {
        searchModel.cancelSearch()
    }

    fun clearSearchSourceStatuses() {
        synchronized(sourceStatusLock) {
            sourceStatuses.clear()
        }
        searchSourceStatusLiveData.postValue(emptyList())
    }

    fun pause() {
        searchModel.pause()
    }

    fun resume() {
        searchModel.resume()
    }

    /**
     * 保存搜索关键字
     */
    fun saveSearchKey(key: String) {
        execute {
            appDb.searchKeywordDao.get(key)?.let {
                it.usage += 1
                it.lastUseTime = System.currentTimeMillis()
                appDb.searchKeywordDao.update(it)
            } ?: appDb.searchKeywordDao.insert(SearchKeyword(key, 1))
        }
    }

    /**
     * 清楚搜索关键字
     */
    fun clearHistory() {
        execute {
            appDb.searchKeywordDao.deleteAll()
        }
    }

    fun deleteHistory(searchKeyword: SearchKeyword) {
        execute {
            appDb.searchKeywordDao.delete(searchKeyword)
        }
    }

    override fun onCleared() {
        super.onCleared()
        searchModel.close()
    }

}
