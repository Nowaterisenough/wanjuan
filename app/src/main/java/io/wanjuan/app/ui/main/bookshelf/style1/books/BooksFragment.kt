package io.wanjuan.app.ui.main.bookshelf.style1.books

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.isGone
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import io.wanjuan.app.R
import io.wanjuan.app.base.BaseFragment
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.constant.EventBus
import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookGroup
import io.wanjuan.app.databinding.FragmentBooksBinding
import io.wanjuan.app.help.book.BookTagHelper
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.lib.theme.accentColor
import io.wanjuan.app.lib.theme.primaryColor
import io.wanjuan.app.sync.SyncManager
import io.wanjuan.app.ui.book.info.BookInfoActivity
import io.wanjuan.app.ui.main.MainViewModel
import io.wanjuan.app.utils.cnCompare
import io.wanjuan.app.utils.applyMainBottomBarPadding
import io.wanjuan.app.utils.dpToPx
import io.wanjuan.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.wanjuan.app.utils.observeEvent
import io.wanjuan.app.utils.setEdgeEffectColor
import io.wanjuan.app.utils.startActivity
import io.wanjuan.app.utils.startActivityForBook
import io.wanjuan.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * 书架界面
 */
class BooksFragment() : BaseFragment(R.layout.fragment_books),
    BaseBooksAdapter.CallBack {

    constructor(position: Int, group: BookGroup) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        bundle.putLong("groupId", group.groupId)
        bundle.putInt("bookSort", group.getRealBookSort())
        bundle.putBoolean("enableRefresh", group.enableRefresh)
        bundle.putBoolean("onlyUpdateRead", group.onlyUpdateRead)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBooksBinding::bind)
    private val activityViewModel by activityViewModels<MainViewModel>()
    private var bookshelfLayout = AppConfig.bookshelfLayout
    private lateinit var booksAdapter: BaseBooksAdapter<*>
    private var booksFlowJob: Job? = null
    var position = 0
        private set
    var groupId = -1L
        private set
    var bookSort = 0
        private set
    private var upLastUpdateTimeJob: Job? = null
    private var enableRefresh = true
    private var onlyUpdateRead = false
    private var bookTagFilter = ""
    private var bookshelfMargin = AppConfig.bookshelfMargin
    private var itemCount = 0
    private var totalRows = 0

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        arguments?.let {
            position = it.getInt("position", 0)
            groupId = it.getLong("groupId", -1)
            bookSort = it.getInt("bookSort", 0)
            enableRefresh = it.getBoolean("enableRefresh", true)
            onlyUpdateRead = it.getBoolean("onlyUpdateRead", false)
            binding.refreshLayout.isEnabled = enableRefresh
        }
        initRecyclerView()
        upRecyclerData()
    }

    private fun initRecyclerView() {
        booksAdapter = createBooksAdapter(bookshelfLayout)
        binding.rvBookshelf.setEdgeEffectColor(primaryColor)
        binding.rvBookshelf.clipToPadding = true
        binding.rvBookshelf.applyMainBottomBarPadding()
        upFastScrollerBar()
        binding.refreshLayout.setColorSchemeColors(accentColor)
        binding.refreshLayout.setProgressViewOffset(true, (-28).dpToPx(), 56.dpToPx())
        binding.refreshLayout.setOnRefreshListener {
            binding.refreshLayout.isRefreshing = false
            SyncManager.syncNow()
            activityViewModel.upToc(
                booksAdapter.getItems(),
                onlyUpdateRead,
                pullProgressAfterUpdate = true
            )
        }
        updateLayoutManager()
        booksAdapter.stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
        binding.rvBookshelf.adapter = booksAdapter
        /**
         * 应该是当初没有使用override val keepScrollPosition = true 加的代码
         * 最近阅读插入顶部时会造成滚动
         * 但是采用keepScrollPosition = true复原滚动后,代码就多余了
         * 采用下面代码反而会向上多滚动一个行
         * 再加上2025/12/19代码,因为下面的代码会出现很奇怪的自动滚动到顶部现象,没理出原因,注释掉下面代码
         * **/
//        booksAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
//            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
//                val layoutManager = binding.rvBookshelf.layoutManager
//                if (positionStart == 0 && itemCount == 1 && layoutManager is LinearLayoutManager) {
//                    val scrollTo = layoutManager.findFirstVisibleItemPosition() - itemCount
//                    binding.rvBookshelf.scrollToPosition(max(0, scrollTo))
//                }
//            }
//
//            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
//                val layoutManager = binding.rvBookshelf.layoutManager
//                if (toPosition == 0 && itemCount == 1 && layoutManager is LinearLayoutManager) {
//                    val scrollTo = layoutManager.findFirstVisibleItemPosition() - itemCount
//                    binding.rvBookshelf.scrollToPosition(max(0, scrollTo))
//                }
//            }
//        })
        binding.rvBookshelf.addItemDecoration( object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val position = parent.getChildAdapterPosition(view)
                if (bookshelfLayout >= 2) {
                    val spanCount = bookshelfLayout
                    val rowIndex = position / spanCount
                    when (rowIndex) {
                        0 -> { //第一行加额外上边距
                            outRect.set(bookshelfMargin, bookshelfMargin + 24, bookshelfMargin, bookshelfMargin)
                        }
                        totalRows - 1 -> { //最后一行加额外下边距
                            outRect.set(bookshelfMargin, bookshelfMargin, bookshelfMargin, bookshelfMargin)
                        }
                        else -> {
                            outRect.set(bookshelfMargin, bookshelfMargin, bookshelfMargin, bookshelfMargin)
                        }
                    }
                } else {
                    when (position) {
                        0 -> {
                            outRect.set(0, bookshelfMargin + 24, 0, bookshelfMargin)
                        }
                        itemCount - 1 -> {
                            outRect.set(0, bookshelfMargin, 0, bookshelfMargin)
                        }
                        else -> {
                            outRect.set(0, bookshelfMargin, 0, bookshelfMargin)
                        }
                    }
                }
            }
        })
        startLastUpdateTimeJob()
    }

    private fun createBooksAdapter(layout: Int): BaseBooksAdapter<*> {
        return when (layout) {
            0 -> BooksAdapterList(requireContext(), this, this, viewLifecycleOwner.lifecycle)
            1 -> BooksAdapterList2(requireContext(), this, this, viewLifecycleOwner.lifecycle)
            else -> BooksAdapterGrid(requireContext(), this)
        }
    }

    private fun updateLayoutManager() {
        if (bookshelfLayout >= 2) {
            val layoutManager = binding.rvBookshelf.layoutManager
            if (layoutManager is GridLayoutManager) {
                layoutManager.spanCount = bookshelfLayout
            } else {
                binding.rvBookshelf.layoutManager = GridLayoutManager(context, bookshelfLayout)
            }
            binding.rvBookshelf.setRecycledViewPool(activityViewModel.booksGridRecycledViewPool)
        } else {
            if (binding.rvBookshelf.layoutManager !is LinearLayoutManager ||
                binding.rvBookshelf.layoutManager is GridLayoutManager
            ) {
                binding.rvBookshelf.layoutManager = LinearLayoutManager(context)
            }
            binding.rvBookshelf.setRecycledViewPool(activityViewModel.booksListRecycledViewPool)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateBookshelfLayout(layout: Int) {
        val newLayout = layout.coerceAtLeast(0)
        if (bookshelfLayout == newLayout) return
        val oldLayout = bookshelfLayout
        val oldItems = if (::booksAdapter.isInitialized) {
            booksAdapter.getItems()
        } else {
            emptyList()
        }
        bookshelfLayout = newLayout
        if (view == null) return
        updateTotalRows()
        updateLayoutManager()
        val adapterTypeChanged = oldLayout < 2 || newLayout < 2
        if (adapterTypeChanged && ::booksAdapter.isInitialized) {
            booksAdapter = createBooksAdapter(newLayout).apply {
                stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            binding.rvBookshelf.adapter = booksAdapter
            booksAdapter.setItems(oldItems)
        } else if (::booksAdapter.isInitialized) {
            booksAdapter.notifyDataSetChanged()
        }
        binding.rvBookshelf.invalidateItemDecorations()
        startLastUpdateTimeJob()
    }

    fun updateBookshelfSpacing(spacing: Int) {
        val newSpacing = spacing.coerceIn(0, 60)
        if (bookshelfMargin == newSpacing) return
        bookshelfMargin = newSpacing
        if (view == null) return
        binding.rvBookshelf.invalidateItemDecorations()
    }

    private fun upFastScrollerBar() {
        val showBookshelfFastScroller = AppConfig.showBookshelfFastScroller
        binding.rvBookshelf.setFastScrollEnabled(showBookshelfFastScroller)
        if (showBookshelfFastScroller) {
            binding.rvBookshelf.scrollBarSize = 0
        } else {
            binding.rvBookshelf.scrollBarSize =
                ViewConfiguration.get(requireContext()).scaledScrollBarSize
        }
    }

    fun upBookSort(sort: Int) {
        binding.root.post {
            arguments?.putInt("bookSort", sort)
            bookSort = sort
            upRecyclerData()
        }
    }

    fun setEnableRefresh(enable: Boolean) {
        enableRefresh = enable
        binding.refreshLayout.isEnabled = enable
    }

    fun setBookTagFilter(tag: String) {
        val normalized = tag.trim()
        if (bookTagFilter == normalized) return
        bookTagFilter = normalized
        upRecyclerData()
    }

    /**
     * 更新书籍列表信息
     */
    private fun upRecyclerData() {
        booksFlowJob?.cancel()
        booksFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowByGroup(groupId).map { list ->
                //排序
                when (bookSort) {
                    1 -> list.sortedByDescending { it.latestChapterTime }
                    2 -> list.sortedWith { o1, o2 ->
                        o1.name.cnCompare(o2.name)
                    }

                    3 -> list.sortedBy { it.order }

                    // 综合排序 issue #3192
                    4 -> list.sortedByDescending {
                        max(it.latestChapterTime, it.durChapterTime)
                    }
                    // 按作者排序
                    5 -> list.sortedWith { o1, o2 ->
                        o1.author.cnCompare(o2.author)
                    }

                    else -> list.sortedByDescending { it.durChapterTime }
                }
            }.map { list ->
                val filteredList = if (bookTagFilter.isBlank()) {
                    list
                } else {
                    list.filter { it.hasCustomTag(bookTagFilter) }
                }
                list to filteredList
            }.flowWithLifecycleAndDatabaseChangeFirst(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.BOOK_TABLE_NAME
            ).catch {
                AppLog.put("书架更新出错", it)
            }.conflate().flowOn(Dispatchers.Default).collect { (allBooks, list) ->
                (parentFragment as? io.wanjuan.app.ui.main.bookshelf.style1.BookshelfFragment1)
                    ?.onBooksChanged(groupId, allBooks)
                itemCount = list.size
                updateTotalRows()
                binding.tvEmptyMsg.isGone = itemCount > 0
                binding.refreshLayout.isEnabled = enableRefresh && itemCount > 0
                booksAdapter.setItems(list)
                delay(100)
            }
        }
    }

    private fun updateTotalRows() {
        val spanCount = bookshelfLayout
        totalRows = if (spanCount >= 2 && itemCount > 0) {
            if (itemCount % spanCount == 0) itemCount / spanCount else itemCount / spanCount + 1
        } else {
            0
        }
    }

    private fun startLastUpdateTimeJob() {
        upLastUpdateTimeJob?.cancel()
        if (!AppConfig.showLastUpdateTime || bookshelfLayout >= 2) {
            return
        }
        upLastUpdateTimeJob = viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    booksAdapter.upLastUpdateTime()
                    delay(30 * 1000)
                }
            }
        }
    }

    fun getBooks(): List<Book> {
        return if (::booksAdapter.isInitialized) booksAdapter.getItems() else emptyList()
    }

    private fun Book.hasCustomTag(tag: String): Boolean {
        return BookTagHelper.has(customTag, tag)
    }

    fun gotoTop() {
        if (AppConfig.isEInkMode) {
            binding.rvBookshelf.scrollToPosition(0)
        } else {
            binding.rvBookshelf.smoothScrollToPosition(0)
        }
    }

    fun getBooksCount(): Int {
        return if (::booksAdapter.isInitialized) booksAdapter.itemCount else 0
    }

    override fun onDestroyView() {
        super.onDestroyView()
        /**
         * 将 RecyclerView 中的视图全部回收到 RecycledViewPool 中
         */
        binding.rvBookshelf.setItemViewCacheSize(0)
        binding.rvBookshelf.adapter = null
    }

    override fun open(book: Book) {
        startActivityForBook(book)
    }

    override fun openBookInfo(book: Book) {
        startActivity<BookInfoActivity> {
            putExtra("name", book.name)
            putExtra("author", book.author)
        }
    }

    override fun isUpdate(bookUrl: String): Boolean {
        return activityViewModel.isUpdate(bookUrl)
    }

    override fun isWaitingUpdate(bookUrl: String): Boolean {
        return activityViewModel.isWaitingUpdate(bookUrl)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.UP_BOOKSHELF) {
            booksAdapter.notification(it)
        }
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) {
            booksAdapter.notifyDataSetChanged()
            startLastUpdateTimeJob()
            upFastScrollerBar()
        }
    }
}
