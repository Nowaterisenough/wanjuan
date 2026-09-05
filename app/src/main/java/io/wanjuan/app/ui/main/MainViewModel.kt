package io.wanjuan.app.ui.main

import android.app.Application
import android.os.Build
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool
import io.wanjuan.app.base.BaseViewModel
import io.wanjuan.app.constant.AppConst
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.constant.EventBus
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.help.AppWebDav
import io.wanjuan.app.help.DefaultData
import io.wanjuan.app.help.book.BookHelp
import io.wanjuan.app.help.book.BookCatalogUpdate
import io.wanjuan.app.help.book.isLocal
import io.wanjuan.app.help.book.isUpError
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.config.LocalConfig
import io.wanjuan.app.help.book.BookTagHelper
import io.wanjuan.app.R
import io.wanjuan.app.model.CacheBook
import io.wanjuan.app.model.ReadBook
import io.wanjuan.app.model.ReadManga
import io.wanjuan.app.model.webBook.WebBook
import io.wanjuan.app.service.CacheBookService
import io.wanjuan.app.sync.SyncManager
import io.wanjuan.app.sync.SingleFlightSync
import io.wanjuan.app.utils.onEachParallel
import io.wanjuan.app.utils.postEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.forEach
import kotlin.math.min
import io.wanjuan.app.model.RuleUpdate
import io.wanjuan.app.model.SourceCallBack

class MainViewModel(application: Application) : BaseViewModel(application) {
    private var threadCount = AppConfig.threadCount
    private var poolSize = min(threadCount, AppConst.MAX_THREAD)
    private var upTocPool = Executors.newFixedThreadPool(poolSize).asCoroutineDispatcher()
    private val waitUpTocBooks = LinkedList<String>()
    private val waitUpTocBookSet = ConcurrentHashMap.newKeySet<String>()
    private val onUpTocBooks = ConcurrentHashMap.newKeySet<String>()
    private val pullProgressAfterTocBooks = ConcurrentHashMap.newKeySet<String>()
    private val catalogCompletions = hashMapOf<String, CompletableDeferred<Boolean>>()
    val bookshelfRefreshStatus = MutableLiveData(BookshelfRefreshStatus())
    private var bookshelfRefreshAction: suspend () -> Unit = {}
    private val bookshelfRefreshFlight = SingleFlightSync(viewModelScope) { bookshelfRefreshAction() }
    private val eventListenerSource = ConcurrentHashMap<BookSource, Boolean>()
    val onUpBooksLiveData = MutableLiveData<Int>()
    private var upTocJob: Job? = null
    private var cacheBookJob: Job? = null
    val booksListRecycledViewPool = RecycledViewPool().apply {
        setMaxRecycledViews(0, 30)
    }
    val booksGridRecycledViewPool = RecycledViewPool().apply {
        setMaxRecycledViews(0, 100)
    }
    var callback: CallBack? = null
    fun setActivityCallback(callback: CallBack) {
        this.callback = callback
    }

    init {
        deleteNotShelfBook()
    }

    override fun onCleared() {
        super.onCleared()
        upTocPool.close()
    }

    fun upPool() {
        threadCount = AppConfig.threadCount
        if (upTocJob?.isActive == true || cacheBookJob?.isActive == true) {
            return
        }
        val newPoolSize = min(threadCount, AppConst.MAX_THREAD)
        if (poolSize == newPoolSize) {
            return
        }
        poolSize = newPoolSize
        upTocPool.close()
        upTocPool = Executors.newFixedThreadPool(poolSize).asCoroutineDispatcher()
    }

    fun isUpdate(bookUrl: String): Boolean {
        return onUpTocBooks.contains(bookUrl)
    }

    fun isWaitingUpdate(bookUrl: String): Boolean {
        return waitUpTocBookSet.contains(bookUrl)
    }

    fun upAllBookToc() {
        execute {
            addToWaitUp(
                appDb.bookDao.hasUpdateBooks,
                AppConfig.onlyUpdateRead,
                pullProgressAfterUpdate = false,
                retryUpdateErrorBooks = false
            )
        }
    }

    fun ruleSubsUp() {
        execute {
            val ruleSubs = appDb.ruleSubDao.all
            for (ruleSub in ruleSubs) {
                if (ruleSub.autoUpdate) {
                    val checkResult = RuleUpdate.cacheSource(ruleSub)
                    if(checkResult) {
                        callback?.openImportUi(ruleSub.type, ruleSub.url)
                    }
                }
            }
        }
    }

    fun upToc(
        books: List<Book>,
        onlyUpdateRead: Boolean,
        pullProgressAfterUpdate: Boolean = false
    ) {
        execute(context = upTocPool) {
            books.filter {
                !it.isLocal && it.canUpdate
            }.let {
                addToWaitUp(
                    it,
                    onlyUpdateRead,
                    pullProgressAfterUpdate,
                    retryUpdateErrorBooks = true
                )
            }
        }
    }

    data class BookshelfRefreshStatus(val running: Boolean = false, val message: String = "")

    fun refreshBookshelf(
        groupId: Long,
        displayedBooks: List<Book>,
        onlyUpdateRead: Boolean,
        tag: String = "",
        updateCatalog: Boolean = true
    ) {
        val displayedSnapshot = displayedBooks.toList()
        bookshelfRefreshAction = {
            refreshBookshelfOnce(groupId, displayedSnapshot, onlyUpdateRead, tag, updateCatalog)
        }
        bookshelfRefreshFlight.start(rerunIfActive = true)
    }

    private suspend fun refreshBookshelfOnce(
        groupId: Long,
        displayedBooks: List<Book>,
        onlyUpdateRead: Boolean,
        tag: String,
        updateCatalog: Boolean
    ) {
        LocalConfig.bookshelfLastRefreshTime = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            var catalogFailures = 0
            var syncFailure: String? = null
            try {
                bookshelfRefreshStatus.postValue(BookshelfRefreshStatus(true, context.getString(R.string.bookshelf_sync_pulling)))
                val pull = SyncManager.syncAwait()
                if (pull.failed > 0) syncFailure = pull.errorMessage ?: context.getString(R.string.bookshelf_sync_pending, pull.pending)
                val positions = displayedBooks.mapIndexed { index, book -> book.bookUrl to index }.toMap()
                val books = appDb.bookDao.flowByGroup(groupId).first()
                    .filter { !it.isLocal && it.canUpdate && (tag.isBlank() || BookTagHelper.has(it.customTag, tag)) }
                    .sortedBy { positions[it.bookUrl] ?: Int.MAX_VALUE }
                val completions = if (updateCatalog) addToWaitUp(books, onlyUpdateRead, false, true) else emptyList()
                bookshelfRefreshStatus.postValue(BookshelfRefreshStatus(
                    true, context.getString(R.string.bookshelf_sync_catalog, 0, completions.size)
                ))
                val completed = AtomicInteger()
                for (completion in completions) {
                    completion.invokeOnCompletion {
                        bookshelfRefreshStatus.postValue(BookshelfRefreshStatus(
                            true, context.getString(R.string.bookshelf_sync_catalog, completed.incrementAndGet(), completions.size)
                        ))
                    }
                }
                catalogFailures = completions.awaitAll().count { !it }
                bookshelfRefreshStatus.postValue(BookshelfRefreshStatus(true, context.getString(R.string.bookshelf_sync_uploading)))
                var flush = SyncManager.flushPending()
                // Include writes that arrived while the first upload batch was in flight.
                if (flush.failed == 0 && flush.pending > 0) flush = SyncManager.flushPending()
                if (!flush.isSuccess) syncFailure = flush.errorMessage ?: context.getString(R.string.bookshelf_sync_pending, flush.pending)
                val message = when {
                    syncFailure != null -> context.getString(R.string.bookshelf_sync_failed, syncFailure) +
                        if (catalogFailures > 0) " · " + context.getString(R.string.bookshelf_sync_catalog_failed, catalogFailures) else ""
                    catalogFailures > 0 -> context.getString(R.string.bookshelf_sync_catalog_failed, catalogFailures)
                    else -> {
                        LocalConfig.bookshelfLastSuccessTime = System.currentTimeMillis()
                        context.getString(R.string.bookshelf_sync_complete)
                    }
                }
                bookshelfRefreshStatus.postValue(BookshelfRefreshStatus(false, message))
            } catch (e: CancellationException) {
                bookshelfRefreshStatus.postValue(BookshelfRefreshStatus())
                throw e
            } catch (e: Exception) {
                AppLog.put("书架刷新失败", e)
                bookshelfRefreshStatus.postValue(BookshelfRefreshStatus(
                    false, context.getString(R.string.bookshelf_sync_failed, e.localizedMessage.orEmpty())
                ))
            }
        }
    }

    @Synchronized
    private fun addToWaitUp(
        books: List<Book>,
        onlyUpdateRead: Boolean,
        pullProgressAfterUpdate: Boolean,
        retryUpdateErrorBooks: Boolean
    ): List<CompletableDeferred<Boolean>> {
        val completions = arrayListOf<CompletableDeferred<Boolean>>()
        books.forEach { book ->
            val skipUnreadBook = onlyUpdateRead &&
                    book.getUnreadChapterNum() > 0 &&
                    !(retryUpdateErrorBooks && book.isUpError)
            if (skipUnreadBook) return@forEach
            completions += catalogCompletions.getOrPut(book.bookUrl) { CompletableDeferred() }
            if (pullProgressAfterUpdate) {
                pullProgressAfterTocBooks.add(book.bookUrl)
            }
            if (!waitUpTocBooks.contains(book.bookUrl) && !onUpTocBooks.contains(book.bookUrl)) {
                waitUpTocBooks.add(book.bookUrl)
                waitUpTocBookSet.add(book.bookUrl)
                postEvent(EventBus.UP_BOOKSHELF, book.bookUrl)
            }
        }
        if (upTocJob == null) {
            startUpTocJob()
        }
        return completions
    }

    @Synchronized
    private fun startUpTocJob() {
        upPool()
        postUpBooksLiveData()
        upTocJob = viewModelScope.launch(upTocPool, start = CoroutineStart.LAZY) {
            flow<String> {
                while (true) {
                    val bookUrl = synchronized(this@MainViewModel) {
                        waitUpTocBooks.poll()?.also {
                            onUpTocBooks.add(it)
                            waitUpTocBookSet.remove(it)
                        }
                    } ?: break
                    emit(bookUrl)
                }
            }.onEachParallel(threadCount) {
                postEvent(EventBus.UP_BOOKSHELF, it)
                var success = false
                try {
                    success = updateToc(it)
                } finally {
                    synchronized(this@MainViewModel) {
                        onUpTocBooks.remove(it)
                        catalogCompletions.remove(it)?.complete(success)
                        pullProgressAfterTocBooks.remove(it)
                    }
                    postEvent(EventBus.UP_BOOKSHELF, it)
                    postUpBooksLiveData()
                }
            }.onCompletion {
                synchronized(this@MainViewModel) {
                    upTocJob = null
                    if (it != null) {
                        catalogCompletions.values.forEach { completion -> completion.complete(false) }
                        catalogCompletions.clear()
                        waitUpTocBooks.clear()
                        waitUpTocBookSet.clear()
                        onUpTocBooks.clear()
                        pullProgressAfterTocBooks.clear()
                    } else if (waitUpTocBooks.isNotEmpty()) {
                        startUpTocJob()
                    }
                }
                if (it == null && cacheBookJob == null && !CacheBookService.isRun) {
                    //所有目录更新完再开始缓存章节
                    cacheBook()
                }
            }.catch {
                AppLog.put("更新目录出错\n${it.localizedMessage}", it)
            }.collect()
        }
        upTocJob?.start()
    }

    private suspend fun updateToc(bookUrl: String): Boolean {
        var book = appDb.bookDao.getBook(bookUrl) ?: run {
            pullProgressAfterTocBooks.remove(bookUrl)
            return false
        }
        val source = appDb.bookSourceDao.getBookSource(book.origin)
        if (source == null) {
            pullProgressAfterTocBooks.remove(bookUrl)
            BookCatalogUpdate.markFailed(appDb, bookUrl)
            return false
        }
        if (source.eventListener) {
            // 使用 putIfAbsent 确保只添加一次
            if (eventListenerSource.putIfAbsent(source, true) == null) {
                // 通知监听事件的书源，书架刷新开始
                SourceCallBack.callBackSource(viewModelScope, SourceCallBack.START_SHELF_REFRESH, source)
            }
        }
        var success = false
        kotlin.runCatching {
            val oldBook = book.copy()
            if (book.tocUrl.isBlank()) {
                WebBook.getBookInfoAwait(source, book)
            } else {
                WebBook.runPreUpdateJs(source, book)
            }
            val toc = WebBook.getChapterListAwait(source, book).getOrThrow()
            book = BookCatalogUpdate.save(appDb, oldBook, book, toc) ?: return false
            if (book.bookUrl != bookUrl) {
                BookHelp.updateCacheFolder(oldBook, book)
            }
            ReadBook.onChapterListUpdated(book)
            ReadManga.onChapterListUpdated(book)
            if (pullProgressAfterTocBooks.remove(bookUrl)) {
                pullRemoteProgressAfterToc(book)
            }
            addDownload(source, book)
            success = true
        }.onFailure {
            pullProgressAfterTocBooks.remove(bookUrl)
            currentCoroutineContext().ensureActive()
            AppLog.put("${book.name} 更新目录失败\n${it.localizedMessage}", it)
            //这里可能因为时间太长书籍信息已经更改,所以重新获取
            BookCatalogUpdate.markFailed(appDb, book.bookUrl)
        }
        return success
    }

    private suspend fun pullRemoteProgressAfterToc(book: Book) {
        if (!AppConfig.webDavObjectSync) return
        kotlin.runCatching {
            SyncManager.bookshelf.pullProgress(book)
        }.onSuccess { progress ->
            progress ?: return@onSuccess
            if (progress.durChapterIndex in 0..book.lastChapterIndex) {
                SyncManager.bookshelf.applyProgress(book, progress)
            }
        }.onFailure {
            currentCoroutineContext().ensureActive()
            AppLog.put("拉取阅读进度失败《${book.name}》\n${it.localizedMessage}", it)
        }
    }

    @Synchronized
    fun postUpBooksLiveData(reset: Boolean = false) {
        if (AppConfig.showWaitUpCount) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                onUpBooksLiveData.postValue(waitUpTocBooks.size + onUpTocBooks.size)
            } else {
                var count = 0
                onUpTocBooks.forEach { _ -> count++ }
                onUpBooksLiveData.postValue(waitUpTocBooks.size + count)
            }
        } else if (reset) {
            onUpBooksLiveData.postValue(0)
        }
    }

    @Synchronized
    private fun addDownload(source: BookSource, book: Book) {
        val preDownloadNum = source.effectivePreDownloadNum(AppConfig.preDownloadNum)
        if (preDownloadNum == 0) return
        val endIndex = min(
            book.totalChapterNum - 1,
            book.durChapterIndex.plus(preDownloadNum)
        )
        val cacheBook = CacheBook.getOrCreate(source, book)
        cacheBook.addDownload(book.durChapterIndex, endIndex)
    }

    /**
     * 缓存书籍
     */
    private fun cacheBook() {
        //开始缓存前，通知监听事件的书源，书架刷新已完成
        eventListenerSource.toList().forEach {
            SourceCallBack.callBackSource(viewModelScope, SourceCallBack.END_SHELF_REFRESH, it.first)
        }
        eventListenerSource.clear()
        cacheBookJob?.cancel()
        cacheBookJob = viewModelScope.launch(upTocPool) {
            launch {
                while (isActive && CacheBook.isRun) {
                    val isOnUpTocBooksEmpty = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        onUpTocBooks.isEmpty()
                    } else {
                        var isEmpty = true
                        onUpTocBooks.forEach { _ ->
                            isEmpty = false
                            return@forEach
                        }
                        isEmpty
                    }
                    //有目录更新是不缓存,优先更新目录,现在更多网站限制并发
                    CacheBook.setWorkingState(waitUpTocBooks.isEmpty() && isOnUpTocBooksEmpty)
                    delay(1000)
                }
            }
            CacheBook.startProcessJob(upTocPool)
        }
    }

    fun postLoad() {
        execute {
            if (appDb.httpTTSDao.count == 0) {
                DefaultData.httpTTS.let {
                    appDb.httpTTSDao.insert(*it.toTypedArray())
                }
            }
        }
    }

    fun restoreWebDav(name: String) {
        execute {
            AppWebDav.restoreWebDav(name)
        }
    }

    private fun deleteNotShelfBook() {
        execute {
            appDb.bookDao.deleteNotShelfBook()
        }
    }

    interface CallBack {
        fun openImportUi(type: Int, source: String)
    }

}
