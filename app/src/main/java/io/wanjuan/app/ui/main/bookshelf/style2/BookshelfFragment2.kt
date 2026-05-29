package io.wanjuan.app.ui.main.bookshelf.style2

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.widget.PopupWindow
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.wanjuan.app.R
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.constant.EventBus
import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookGroup
import io.wanjuan.app.databinding.FragmentBookshelf2Binding
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.lib.theme.accentColor
import io.wanjuan.app.lib.theme.primaryColor
import io.wanjuan.app.lib.theme.primaryTextColor
import io.wanjuan.app.ui.book.group.GroupEditDialog
import io.wanjuan.app.ui.book.info.BookInfoActivity
import io.wanjuan.app.ui.book.search.SearchActivity
import io.wanjuan.app.ui.main.MainActivity
import io.wanjuan.app.ui.main.bookshelf.BaseBookshelfFragment
import io.wanjuan.app.ui.widget.GridColumnsPopup
import io.wanjuan.app.utils.applyMainBottomBarPadding
import io.wanjuan.app.utils.cnCompare
import io.wanjuan.app.utils.dpToPx
import io.wanjuan.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.wanjuan.app.utils.observeEvent
import io.wanjuan.app.utils.setEdgeEffectColor
import io.wanjuan.app.utils.showDialogFragment
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
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * 书架界面
 */
class BookshelfFragment2() : BaseBookshelfFragment(R.layout.fragment_bookshelf2),
    SearchView.OnQueryTextListener,
    BaseBooksAdapter.CallBack {

    private companion object {
        private const val BOOKSHELF_GRID_COLUMNS_MIN = 2
        private const val BOOKSHELF_GRID_COLUMNS_MAX = 7
        private const val BOOKSHELF_GRID_SPACING_MIN = 0
        private const val BOOKSHELF_GRID_SPACING_MAX = 60
    }

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBookshelf2Binding::bind)
    private var gridColumnsPopup: PopupWindow? = null
    private var bookshelfLayout = AppConfig.bookshelfLayout
    private lateinit var booksAdapter: BaseBooksAdapter<*>
    private var bookGroups: List<BookGroup> = emptyList()
    private var booksFlowJob: Job? = null
    override var groupId = BookGroup.IdRoot
    override var books: List<Book> = emptyList()
    private var enableRefresh = true
    override var onlyUpdateRead = false
    private var bookshelfMargin = AppConfig.bookshelfMargin
    private var itemCount = 0
    private var totalRows = 0

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        binding.titleBar.setColorFilter(primaryTextColor)
        binding.btnBookshelfReadRecord.setColorFilter(primaryTextColor)
        installModernBookshelfOverflow(binding.titleBar.toolbar)
        binding.btnBookshelfReadRecord.setOnClickListener {
            (activity as? MainActivity)?.openReadRecordPage()
        }
        binding.btnBookshelfLayoutToggle.setOnClickListener {
            switchBookshelfLayout()
        }
        binding.btnBookshelfLayoutToggle.setOnLongClickListener {
            showBookshelfGridColumnsPopup(it)
            true
        }
        updateBookshelfLayoutToggleIcon()
        initRecyclerView()
        initBookGroupData()
        initBooksData()
    }

    private fun initRecyclerView() {
        booksAdapter = createBooksAdapter(bookshelfLayout)
        binding.rvBookshelf.setEdgeEffectColor(primaryColor)
        binding.rvBookshelf.clipToPadding = true
        binding.rvBookshelf.applyMainBottomBarPadding()
        binding.refreshLayout.setColorSchemeColors(accentColor)
        binding.refreshLayout.setProgressViewOffset(true, (-28).dpToPx(), 56.dpToPx())
        binding.refreshLayout.setOnRefreshListener {
            binding.refreshLayout.isRefreshing = false
            activityViewModel.upToc(currentUpdateBooks(), onlyUpdateRead)
        }
        updateLayoutManager()
        binding.rvBookshelf.adapter = booksAdapter
        /**
         * 采用 layoutManager?.onRestoreInstanceState(layoutState)
         * 恢复滚动位置
         * **/
        binding.rvBookshelf.itemAnimator =  null
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
    }

    private fun createBooksAdapter(layout: Int): BaseBooksAdapter<*> {
        return if (layout >= 2) {
            BooksAdapterGrid(requireContext(), this)
        } else {
            BooksAdapterList(requireContext(), this)
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
        } else if (binding.rvBookshelf.layoutManager !is LinearLayoutManager ||
            binding.rvBookshelf.layoutManager is GridLayoutManager
        ) {
            binding.rvBookshelf.layoutManager = LinearLayoutManager(context)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateBookshelfLayout(layout: Int) {
        val newLayout = layout.coerceAtLeast(0)
        if (bookshelfLayout == newLayout) return
        val oldLayout = bookshelfLayout
        bookshelfLayout = newLayout
        updateTotalRows()
        updateLayoutManager()
        val adapterTypeChanged = oldLayout < 2 || newLayout < 2
        if (adapterTypeChanged && ::booksAdapter.isInitialized) {
            booksAdapter = createBooksAdapter(newLayout)
            binding.rvBookshelf.adapter = booksAdapter
            booksAdapter.updateItems(groupId)
        } else if (::booksAdapter.isInitialized) {
            booksAdapter.notifyDataSetChanged()
        }
        binding.rvBookshelf.invalidateItemDecorations()
    }

    fun updateBookshelfSpacing(spacing: Int) {
        val newSpacing = spacing.coerceIn(BOOKSHELF_GRID_SPACING_MIN, BOOKSHELF_GRID_SPACING_MAX)
        if (bookshelfMargin == newSpacing) return
        bookshelfMargin = newSpacing
        binding.rvBookshelf.invalidateItemDecorations()
    }

    private fun switchBookshelfLayout() {
        val currentLayout = AppConfig.bookshelfLayout
        val targetLayout = if (currentLayout >= 2) {
            AppConfig.bookshelfGridColumns = currentLayout
            0
        } else AppConfig.bookshelfGridColumns
        if (currentLayout == targetLayout) return
        AppConfig.bookshelfLayout = targetLayout
        updateBookshelfLayoutToggleIcon()
        updateBookshelfLayout(targetLayout)
    }

    private fun showBookshelfGridColumnsPopup(anchor: View) {
        gridColumnsPopup = GridColumnsPopup.show(
            anchor = anchor,
            titleRes = R.string.discover_grid_columns,
            minColumns = BOOKSHELF_GRID_COLUMNS_MIN,
            maxColumns = BOOKSHELF_GRID_COLUMNS_MAX,
            initialColumns = AppConfig.bookshelfGridColumns,
            previousPopup = gridColumnsPopup,
            onColumnsChanging = ::setBookshelfGridColumns,
            onColumnsChanged = ::setBookshelfGridColumns,
            initialSpacing = AppConfig.bookshelfMargin,
            spacingTitleRes = R.string.margin,
            minSpacing = BOOKSHELF_GRID_SPACING_MIN,
            maxSpacing = BOOKSHELF_GRID_SPACING_MAX,
            onSpacingChanging = ::setBookshelfGridSpacing,
            onSpacingChanged = ::setBookshelfGridSpacing
        )
    }

    private fun setBookshelfGridColumns(columns: Int) {
        val gridColumns = columns.coerceIn(BOOKSHELF_GRID_COLUMNS_MIN, BOOKSHELF_GRID_COLUMNS_MAX)
        AppConfig.bookshelfGridColumns = gridColumns
        if (AppConfig.bookshelfLayout == gridColumns) return
        AppConfig.bookshelfLayout = gridColumns
        updateBookshelfLayoutToggleIcon()
        updateBookshelfLayout(gridColumns)
    }

    private fun setBookshelfGridSpacing(spacing: Int) {
        val gridSpacing = spacing.coerceIn(BOOKSHELF_GRID_SPACING_MIN, BOOKSHELF_GRID_SPACING_MAX)
        if (AppConfig.bookshelfMargin == gridSpacing) return
        AppConfig.bookshelfMargin = gridSpacing
        updateBookshelfSpacing(gridSpacing)
    }

    private fun updateBookshelfLayoutToggleIcon() {
        binding.btnBookshelfLayoutToggle.setImageResource(
            if (AppConfig.bookshelfLayout >= 2) {
                R.drawable.ic_lucide_layout_list
            } else {
                R.drawable.ic_lucide_layout_grid
            }
        )
    }

    override fun upGroup(data: List<BookGroup>) {
        if (data != bookGroups) {
            bookGroups = data
            booksAdapter.updateItems(groupId)
            itemCount = getItemCount()
            updateTotalRows()
            binding.tvEmptyMsg.isGone = itemCount > 0
            binding.refreshLayout.isEnabled = enableRefresh && itemCount > 0
        }
    }

    override fun upSort() {
        initBooksData()
    }

    private fun initBooksData() {
        if (groupId == BookGroup.IdRoot) {
            if (isAdded) {
                binding.titleBar.title = getString(R.string.bookshelf)
                binding.refreshLayout.isEnabled = true
                enableRefresh = true
            }
        } else {
            bookGroups.firstOrNull {
                groupId == it.groupId
            }?.let {
                binding.titleBar.title = "${getString(R.string.bookshelf)}(${it.groupName})"
                binding.refreshLayout.isEnabled = it.enableRefresh
                enableRefresh = it.enableRefresh
                onlyUpdateRead = it.onlyUpdateRead
            }
        }
        booksFlowJob?.cancel()
        booksFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowByGroup(groupId).map { list ->
                //排序
                when (AppConfig.getBookSortByGroupId(groupId)) {
                    1 -> list.sortedByDescending {
                        it.latestChapterTime
                    }

                    2 -> list.sortedWith { o1, o2 ->
                        o1.name.cnCompare(o2.name)
                    }

                    3 -> list.sortedBy {
                        it.order
                    }

                    4 -> list.sortedByDescending {
                        max(it.latestChapterTime, it.durChapterTime)
                    }

                    else -> list.sortedByDescending {
                        it.durChapterTime
                    }
                }
            }.flowWithLifecycleAndDatabaseChangeFirst(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.BOOK_TABLE_NAME
            ).catch {
                AppLog.put("书架更新出错", it)
            }.conflate().flowOn(Dispatchers.Default).collect { list ->
                books = list
                booksAdapter.updateItems(groupId)
                itemCount = getItemCount()
                updateTotalRows()
                binding.tvEmptyMsg.isGone = itemCount > 0
                binding.refreshLayout.isEnabled = enableRefresh && itemCount > 0
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

    fun back(): Boolean {
        if (groupId != BookGroup.IdRoot) {
            groupId = BookGroup.IdRoot
            initBooksData()
            return true
        }
        return false
    }

    fun switchToGroupId(targetGroupId: Long) {
        groupId = targetGroupId
        initBooksData()
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        SearchActivity.start(requireContext(), query)
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return false
    }

    override fun gotoTop() {
        if (AppConfig.isEInkMode) {
            binding.rvBookshelf.scrollToPosition(0)
        } else {
            binding.rvBookshelf.smoothScrollToPosition(0)
        }
    }

    override fun onItemClick(item: Any) {
        when (item) {
            is Book -> startActivityForBook(item)

            is BookGroup -> {
                groupId = item.groupId
                initBooksData()
            }
        }
    }

    override fun onItemLongClick(item: Any) {
        when (item) {
            is Book -> startActivity<BookInfoActivity> {
                putExtra("name", item.name)
                putExtra("author", item.author)
            }

            is BookGroup -> showDialogFragment(GroupEditDialog(item))
        }
    }

    override fun isUpdate(bookUrl: String): Boolean {
        return activityViewModel.isUpdate(bookUrl)
    }

    fun getItemCount(): Int {
        return if (groupId == BookGroup.IdRoot) {
            bookGroups.size + books.size
        } else {
            books.size
        }
    }

    override fun getItems(): List<Any> {
        if (groupId != BookGroup.IdRoot) {
            return books
        }
        return bookGroups + books
    }

    override fun currentUpdateBooks(): List<Book> {
        return if (::booksAdapter.isInitialized) {
            booksAdapter.getItems().filterIsInstance<Book>()
        } else {
            books
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.UP_BOOKSHELF) {
            booksAdapter.notification(it)
        }
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) {
            booksAdapter.notifyDataSetChanged()
        }
    }
}
