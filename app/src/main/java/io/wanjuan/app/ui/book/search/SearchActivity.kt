package io.wanjuan.app.ui.book.search

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.FlexboxLayout
import io.wanjuan.app.R
import io.wanjuan.app.base.VMBaseActivity
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.BookSourcePart
import io.wanjuan.app.data.entities.SearchBook
import io.wanjuan.app.data.entities.SearchKeyword
import io.wanjuan.app.databinding.ActivityBookSearchBinding
import io.wanjuan.app.help.book.isVideo
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.lib.dialogs.alert
import io.wanjuan.app.lib.dialogs.selector
import io.wanjuan.app.lib.theme.Selector
import io.wanjuan.app.lib.theme.UiCorner
import io.wanjuan.app.lib.theme.accentColor
import io.wanjuan.app.lib.theme.primaryColor
import io.wanjuan.app.lib.theme.primaryTextColor
import io.wanjuan.app.lib.theme.secondaryTextColor
import io.wanjuan.app.ui.about.AppLogDialog
import io.wanjuan.app.ui.book.SearchBookOpenHelper
import io.wanjuan.app.ui.book.info.BookInfoActivity
import io.wanjuan.app.ui.book.source.manage.BookSourceActivity
import io.wanjuan.app.ui.widget.ModernActionPopup
import io.wanjuan.app.utils.ColorUtils
import io.wanjuan.app.utils.applyNavigationBarMargin
import io.wanjuan.app.utils.applyNavigationBarPadding
import io.wanjuan.app.utils.applyStatusBarPadding
import io.wanjuan.app.utils.applyTint
import io.wanjuan.app.utils.dpToPx
import io.wanjuan.app.utils.getPrefBoolean
import io.wanjuan.app.utils.gone
import io.wanjuan.app.utils.invisible
import io.wanjuan.app.utils.putPrefBoolean
import io.wanjuan.app.utils.setEdgeEffectColor
import io.wanjuan.app.utils.showDialogFragment
import io.wanjuan.app.utils.startActivity
import io.wanjuan.app.utils.startActivityForBook
import io.wanjuan.app.utils.viewbindingdelegate.viewBinding
import io.wanjuan.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class SearchActivity : VMBaseActivity<ActivityBookSearchBinding, SearchViewModel>(),
    BookAdapter.CallBack,
    HistoryKeyAdapter.CallBack,
    SearchScopeDialog.Callback,
    SearchAdapter.CallBack {

    override val binding by viewBinding(ActivityBookSearchBinding::inflate)
    override val viewModel by viewModels<SearchViewModel>()

    private val adapter by lazy { SearchAdapter(this, this) }
    private val bookAdapter by lazy {
        BookAdapter(this, this).apply {
            setHasStableIds(true)
        }
    }
    private val historyKeyAdapter by lazy {
        HistoryKeyAdapter(this, this).apply {
            setHasStableIds(true)
        }
    }
    private val searchView: SearchView by lazy { binding.searchView }
    private var groups: List<String>? = null
    private var historyFlowJob: Job? = null
    private var booksFlowJob: Job? = null
    private var modernMenuPopup: PopupWindow? = null
    private var isManualStopSearch = false
    private var searchSourceStatusExpanded = false
    private var lastSearchSourceStatuses: List<SearchSourceStatus> = emptyList()
    private var pendingSourceResultJump: String? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initTopBar()
        initRecyclerView()
        initSearchView()
        initOtherView()
        initData()
        receiptIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiptIntent(intent)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_precision_search -> {
                putPrefBoolean(
                    PreferKey.precisionSearch,
                    !getPrefBoolean(PreferKey.precisionSearch)
                )
                searchView.query?.toString()?.trim()?.let {
                    searchView.setQuery(it, true)
                }
            }

            R.id.menu_search_scope -> alertSearchScope()
            R.id.menu_source_manage -> startActivity<BookSourceActivity>()
            R.id.menu_log -> showDialogFragment(AppLogDialog())
            R.id.menu_1 -> viewModel.searchScope.update("")
            else -> {
                if (item.groupId == R.id.menu_group_1) {
                    viewModel.searchScope.remove(item.title.toString())
                } else if (item.groupId == R.id.menu_group_2) {
                    viewModel.searchScope.update(item.title.toString())
                }
            }
        }
        return true
    }

    private fun initTopBar() {
        binding.root.applyStatusBarPadding()
        binding.btnMenu.setColorFilter(secondaryTextColor)
        val isNight = AppConfig.isNightTheme
        val searchSurfaceColor = if (isNight) {
            ColorUtils.adjustAlpha(Color.rgb(52, 52, 56), 0.42f)
        } else {
            ColorUtils.adjustAlpha(Color.rgb(120, 120, 128), 0.18f)
        }
        val cardColor = if (isNight) {
            ColorUtils.adjustAlpha(Color.rgb(44, 44, 46), 0.45f)
        } else {
            ContextCompat.getColor(this, R.color.background_menu)
        }
        val chipColor = if (isNight) {
            ColorUtils.adjustAlpha(Color.rgb(58, 58, 62), 0.32f)
        } else {
            ContextCompat.getColor(this, R.color.background_card)
        }
        val chipPressedColor = if (isNight) {
            ColorUtils.adjustAlpha(Color.rgb(82, 82, 86), 0.45f)
        } else {
            ContextCompat.getColor(this, R.color.background_menu)
        }
        val strokeColor = ColorUtils.adjustAlpha(primaryTextColor, if (isNight) 0.10f else 0.08f)
        binding.searchView.background = GradientDrawable().apply {
            cornerRadius = UiCorner.searchRadius(18f)
            setColor(searchSurfaceColor)
            setStroke(1.dpToPx(), strokeColor)
        }
        binding.llBookshelfHintCard.background = UiCorner.roundedStroke(
            cardColor,
            UiCorner.searchRadius(20f),
            1.dpToPx(),
            strokeColor
        )
        binding.llHistoryCard.background = UiCorner.roundedStroke(
            cardColor,
            UiCorner.searchRadius(20f),
            1.dpToPx(),
            strokeColor
        )
        binding.llSourceStatusPanel.background = UiCorner.roundedStroke(
            cardColor,
            UiCorner.searchRadius(20f),
            1.dpToPx(),
            strokeColor
        )
        binding.tvClearHistory.background = UiCorner.actionSelector(
            chipColor,
            chipPressedColor,
            UiCorner.searchRadius(14f)
        )
        binding.tvSearchSourceStatusExpand.background = UiCorner.actionSelector(
            Color.TRANSPARENT,
            ColorUtils.adjustAlpha(accentColor, 0.16f),
            UiCorner.searchRadius(12f)
        )
        binding.tvSearchSourceStatusTitle.setTextColor(primaryTextColor)
        binding.tvSearchSourceStatusSummary.setTextColor(secondaryTextColor)
        binding.tvSearchSourceStatusProgress.setTextColor(accentColor)
        binding.tvSearchSourceStatusExpand.setTextColor(accentColor)
        binding.tvSearchSourceStatusExpand.setOnClickListener {
            searchSourceStatusExpanded = !searchSourceStatusExpanded
            renderSearchSourceStatuses(lastSearchSourceStatuses)
        }
        binding.btnMenu.setOnClickListener {
            showSearchMenu(it)
        }
    }

    private fun showSearchMenu(anchor: View) {
        modernMenuPopup = ModernActionPopup.showFromMenu(
            anchor = anchor,
            menuRes = R.menu.book_search,
            previousPopup = modernMenuPopup,
            prepare = {
                prepareSearchMenu(this)
            }
        ) {
            onCompatOptionsItemSelected(it)
        }
    }

    private fun prepareSearchMenu(menu: Menu) {
        menu.findItem(R.id.menu_precision_search)?.isChecked = getPrefBoolean(PreferKey.precisionSearch)
        menu.removeGroup(R.id.menu_group_1)
        menu.removeGroup(R.id.menu_group_2)
        var hasChecked = false
        val searchScopeNames = viewModel.searchScope.displayNames
        if (viewModel.searchScope.isSource()) {
            menu.add(R.id.menu_group_1, Menu.NONE, Menu.NONE, searchScopeNames.first()).apply {
                isChecked = true
                hasChecked = true
            }
        }
        val allSourceMenu =
            menu.add(R.id.menu_group_2, R.id.menu_1, Menu.NONE, getString(R.string.all_source))
                .apply {
                    if (searchScopeNames.isEmpty()) {
                        isChecked = true
                        hasChecked = true
                    }
                }
        groups?.forEach {
            if (searchScopeNames.contains(it)) {
                menu.add(R.id.menu_group_1, Menu.NONE, Menu.NONE, it).apply {
                    isChecked = true
                    hasChecked = true
                }
            } else {
                menu.add(R.id.menu_group_2, Menu.NONE, Menu.NONE, it)
            }
        }
        if (!hasChecked) {
            viewModel.searchScope.update("")
            allSourceMenu.isChecked = true
        }
        menu.setGroupCheckable(R.id.menu_group_1, true, false)
        menu.setGroupCheckable(R.id.menu_group_2, true, true)
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
            ?.apply {
                setBackgroundColor(Color.TRANSPARENT)
                setTextColor(primaryTextColor)
                setHintTextColor(secondaryTextColor)
            }
        searchView.findViewById<android.view.View>(androidx.appcompat.R.id.search_plate)
            ?.setBackgroundColor(Color.TRANSPARENT)
        searchView.findViewById<android.view.View>(androidx.appcompat.R.id.search_edit_frame)
            ?.setBackgroundColor(Color.TRANSPARENT)
        searchView.findViewById<android.view.View>(androidx.appcompat.R.id.submit_area)
            ?.setBackgroundColor(Color.TRANSPARENT)
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = getString(R.string.search_book_key)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                searchView.clearFocus()
                query.trim().let { searchKey ->
                    isManualStopSearch = false
                    viewModel.saveSearchKey(searchKey)
                    viewModel.searchKey = ""
                    viewModel.search(searchKey)
                }
                visibleInputHelp(false)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                viewModel.stop()
                viewModel.clearSearchSourceStatuses()
                binding.fbStartStop.invisible()
                searchView.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)?.apply {
                    setTextColor(primaryTextColor)
                    setHintTextColor(secondaryTextColor)
                }
                upHistory(newText.trim())
                return false
            }
        })
        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (binding.refreshProgressBar.isAutoLoading || (!hasFocus && adapter.isNotEmpty() && searchView.query.isNotBlank())) {
                visibleInputHelp(false)
            } else {
                visibleInputHelp(true)
            }
        }
        visibleInputHelp(true)
    }

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.rvBookshelfSearch.setEdgeEffectColor(primaryColor)
        binding.rvHistoryKey.setEdgeEffectColor(primaryColor)
        binding.rvBookshelfSearch.layoutManager = FlexboxLayoutManager(this)
        binding.rvBookshelfSearch.adapter = bookAdapter
        binding.rvHistoryKey.layoutManager = FlexboxLayoutManager(this)
        binding.rvHistoryKey.adapter = historyKeyAdapter
        binding.llHistoryCard.applyNavigationBarMargin()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null
        binding.recyclerView.applyNavigationBarPadding()
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                if (positionStart == 0) {
                    binding.recyclerView.scrollToPosition(0)
                }
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                super.onItemRangeMoved(fromPosition, toPosition, itemCount)
                if (toPosition == 0) {
                    binding.recyclerView.scrollToPosition(0)
                }
            }
        })
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy < 0) {
                    return
                }
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastPosition = layoutManager.findLastVisibleItemPosition()
                if (lastPosition == RecyclerView.NO_POSITION) {
                    return
                }
                if (adapter.itemCount - lastPosition <= 3 || !recyclerView.canScrollVertically(1)) {
                    scrollToBottom()
                }
            }
        })
    }

    private fun initOtherView() {
        binding.fbStartStop.backgroundTintList =
            Selector.colorBuild()
                .setDefaultColor(accentColor)
                .setPressedColor(ColorUtils.darkenColor(accentColor))
                .create()
        binding.fbStartStop.setOnClickListener {
            if (viewModel.isSearchLiveData.value == true) {
                isManualStopSearch = true
                viewModel.stop()
                binding.refreshProgressBar.isAutoLoading = false
            } else {
                viewModel.search("")
            }
        }
        binding.fbStartStop.applyNavigationBarMargin(true)
        binding.tvClearHistory.setOnClickListener { alertClearHistory() }
    }

    private fun initData() {
        viewModel.searchScope.stateLiveData.observe(this) {
            if (!binding.llInputHelp.isVisible) {
                searchView.query?.toString()?.trim()?.let {
                    searchView.setQuery(it, true)
                }
            }
        }
        viewModel.isSearchLiveData.observe(this) {
            if (it) {
                startSearch()
            } else {
                searchFinally()
            }
        }
        viewModel.searchBookLiveData.observe(this) {
            adapter.setItems(it)
            pendingSourceResultJump?.let { sourceUrl ->
                binding.recyclerView.post {
                    scrollToSearchSourceResult(sourceUrl)
                }
            }
        }
        viewModel.searchSourceStatusLiveData.observe(this) {
            renderSearchSourceStatuses(it)
        }
        lifecycleScope.launch {
            appDb.bookSourceDao.flowEnabledGroups().collect {
                groups = it
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.resume()
                try {
                    awaitCancellation()
                } finally {
                    viewModel.pause()
                }
            }
        }
    }

    /**
     * 处理传入数据
     */
    private fun receiptIntent(intent: Intent? = null) {
        val searchScope = intent?.getStringExtra("searchScope")
        searchScope?.let {
            viewModel.searchScope.update(searchScope, postValue = false, save = false)
        }
        val key = intent?.getStringExtra("key")
        if (key.isNullOrBlank()) {
            searchView.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
                .requestFocus()
        } else {
            searchView.setQuery(key, true)
        }
    }

    /**
     * 滚动到底部事件
     */
    private fun scrollToBottom() {
        if (isManualStopSearch) {
            return
        }
        if (viewModel.isSearchLiveData.value == false
            && viewModel.searchKey.isNotEmpty()
            && viewModel.hasMore
        ) {
            viewModel.search("")
        }
    }

    /**
     * 打开关闭输入帮助
     */
    private fun visibleInputHelp(visible: Boolean) {
        if (visible) {
            upHistory(searchView.query.toString())
            binding.llInputHelp.visibility = VISIBLE
            binding.llSourceStatusPanel.gone()
        } else {
            binding.llInputHelp.visibility = GONE
            renderSearchSourceStatuses(lastSearchSourceStatuses)
        }
    }

    private fun renderSearchSourceStatuses(statuses: List<SearchSourceStatus>) {
        if (lastSearchSourceStatuses.map { it.sourceUrl } != statuses.map { it.sourceUrl }) {
            searchSourceStatusExpanded = false
        }
        lastSearchSourceStatuses = statuses
        if (statuses.isEmpty() || binding.llInputHelp.isVisible) {
            binding.llSourceStatusPanel.gone()
            return
        }
        val completedCount = statuses.count { it.state != SearchSourceState.PENDING }
        val foundResultCount = statuses.sumOf { if (it.state == SearchSourceState.FOUND) it.resultCount else 0 }
        val emptyCount = statuses.count { it.state == SearchSourceState.EMPTY }
        val failedCount = statuses.count { it.state == SearchSourceState.FAILED }
        val isSearching = viewModel.isSearchLiveData.value == true
        binding.tvSearchSourceStatusTitle.text = if (isSearching) {
            "正在检索书源..."
        } else {
            "书源搜索情况"
        }
        binding.tvSearchSourceStatusSummary.text = "找到 ${foundResultCount} 条 · 未搜到 ${emptyCount} 个 · 失败 ${failedCount} 个"
        binding.tvSearchSourceStatusProgress.text = "$completedCount/${statuses.size}"
        binding.tvSearchSourceStatusExpand.text = if (searchSourceStatusExpanded) "收起" else "展开"
        binding.tvSearchSourceStatusExpand.isVisible = statuses.size > COLLAPSED_SOURCE_STATUS_CHIP_COUNT
        binding.flSearchSourceStatuses.removeAllViews()

        val visibleStatuses = if (searchSourceStatusExpanded) {
            statuses
        } else {
            statuses.take(COLLAPSED_SOURCE_STATUS_CHIP_COUNT)
        }
        visibleStatuses.forEach { status ->
            binding.flSearchSourceStatuses.addView(createSearchSourceStatusChip(status))
        }
        if (!searchSourceStatusExpanded && statuses.size > COLLAPSED_SOURCE_STATUS_CHIP_COUNT) {
            val overflowCount = statuses.size - COLLAPSED_SOURCE_STATUS_CHIP_COUNT
            binding.flSearchSourceStatuses.addView(
                createSearchSourceStatusChip(statuses.last(), overflowCount)
            )
        }
        binding.llSourceStatusPanel.visible()
    }

    private fun createSearchSourceStatusChip(status: SearchSourceStatus, overflowCount: Int? = null): TextView {
        val chipColor = if (overflowCount != null) {
            accentColor
        } else {
            searchSourceStatusColor(status.state)
        }
        val defaultAlpha = if (status.state == SearchSourceState.PENDING && overflowCount == null) 0.07f else 0.16f
        val pressedAlpha = if (status.state == SearchSourceState.PENDING && overflowCount == null) 0.11f else 0.24f
        return TextView(this).apply {
            text = if (overflowCount != null) "+$overflowCount" else status.sourceName
            contentDescription = if (overflowCount != null) {
                "展开更多书源状态"
            } else {
                "${status.sourceName}，${searchSourceStatusText(status)}"
            }
            gravity = Gravity.CENTER
            includeFontPadding = false
            minHeight = 30.dpToPx()
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(10.dpToPx(), 0, 10.dpToPx(), 0)
            setTextColor(chipColor)
            textSize = 13f
            background = UiCorner.actionSelector(
                ColorUtils.adjustAlpha(chipColor, defaultAlpha),
                ColorUtils.adjustAlpha(chipColor, pressedAlpha),
                UiCorner.searchRadius(8f)
            )
            layoutParams = FlexboxLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                30.dpToPx()
            ).apply {
                setMargins(0, 0, 8.dpToPx(), 8.dpToPx())
            }
            when {
                overflowCount != null -> setOnClickListener {
                    searchSourceStatusExpanded = true
                    renderSearchSourceStatuses(lastSearchSourceStatuses)
                }

                status.state == SearchSourceState.FOUND -> setOnClickListener {
                    scrollToSearchSourceResult(status.sourceUrl)
                }
            }
        }
    }

    private fun searchSourceStatusColor(state: SearchSourceState): Int {
        return when (state) {
            SearchSourceState.FOUND -> accentColor
            SearchSourceState.FAILED -> Color.rgb(229, 91, 91)
            SearchSourceState.EMPTY -> secondaryTextColor
            SearchSourceState.PENDING -> ColorUtils.adjustAlpha(primaryTextColor, 0.42f)
        }
    }

    private fun searchSourceStatusText(status: SearchSourceStatus): String {
        return when (status.state) {
            SearchSourceState.FOUND -> "搜索到 ${status.resultCount} 条"
            SearchSourceState.FAILED -> "搜索失败"
            SearchSourceState.EMPTY -> "未搜索到"
            SearchSourceState.PENDING -> "还没搜索"
        }
    }

    private fun scrollToSearchSourceResult(sourceUrl: String) {
        lastSearchSourceStatuses.firstOrNull { it.sourceUrl == sourceUrl } ?: return
        val index = adapter.getItems().indexOfFirst { book ->
            book.origin == sourceUrl || book.origins.any { it.substringBefore('\t') == sourceUrl }
        }
        if (index < 0) {
            pendingSourceResultJump = sourceUrl
            return
        }
        pendingSourceResultJump = null
        binding.recyclerView.smoothScrollToPosition(index)
    }

    /**
     * 更新搜索历史
     */
    private fun upHistory(key: String? = null) {
        booksFlowJob?.cancel()
        booksFlowJob = lifecycleScope.launch {
            if (key.isNullOrBlank()) {
                binding.llBookshelfHintCard.gone()
                binding.tvBookShow.gone()
                binding.rvBookshelfSearch.gone()
            } else {
                appDb.bookDao.flowSearch(key).conflate().collect {
                    if (it.isEmpty()) {
                        binding.llBookshelfHintCard.gone()
                        binding.tvBookShow.gone()
                        binding.rvBookshelfSearch.gone()
                    } else {
                        binding.llBookshelfHintCard.visible()
                        binding.tvBookShow.visible()
                        binding.rvBookshelfSearch.visible()
                    }
                    bookAdapter.setItems(it)
                }
            }
        }
        historyFlowJob?.cancel()
        historyFlowJob = lifecycleScope.launch {
            when {
                key.isNullOrBlank() -> appDb.searchKeywordDao.flowByTime()
                else -> appDb.searchKeywordDao.flowSearch(key)
            }.catch {
                AppLog.put("搜索界面获取搜索历史数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect {
                historyKeyAdapter.setItems(it)
                if (it.isEmpty()) {
                    binding.tvClearHistory.invisible()
                    binding.tvHistoryEmpty.visible()
                    binding.rvHistoryKey.gone()
                } else {
                    binding.tvClearHistory.visible()
                    binding.tvHistoryEmpty.gone()
                    binding.rvHistoryKey.visible()
                }
            }
        }
    }

    /**
     * 开始搜索
     */
    private fun startSearch() {
        binding.refreshProgressBar.visible()
        binding.refreshProgressBar.isAutoLoading = true
        binding.fbStartStop.setImageResource(R.drawable.ic_stop_black_24dp)
        binding.fbStartStop.visible()
    }

    /**
     * 搜索结束
     */
    private fun searchFinally() {
        binding.refreshProgressBar.isAutoLoading = false
        binding.refreshProgressBar.gone()
        if (!isManualStopSearch && viewModel.hasMore) {
            binding.fbStartStop.setImageResource(R.drawable.ic_play_24dp)
        } else {
            binding.fbStartStop.invisible()
        }
    }

    override fun observeLiveBus() {
        viewModel.upAdapterLiveData.observe(this) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount, bundleOf(it to null))
        }
        viewModel.searchFinishLiveData.observe(this) { isEmpty ->
            if (!isEmpty || viewModel.searchScope.isAll()) return@observe
            alert("搜索结果为空") {
                val precisionSearch = appCtx.getPrefBoolean(PreferKey.precisionSearch)
                val displayScope = viewModel.searchScope.display
                if (precisionSearch) {
                    setMessage("${displayScope}分组搜索结果为空，是否关闭精准搜索？")
                    yesButton {
                        appCtx.putPrefBoolean(PreferKey.precisionSearch, false)
                        viewModel.searchKey = ""
                        viewModel.search(searchView.query.toString())
                    }
                } else {
                    setMessage("${displayScope}分组搜索结果为空，是否切换到全部分组？")
                    yesButton {
                        viewModel.searchScope.update("")
                    }
                }
                noButton()
            }
        }
    }

    /**
     * 显示书籍详情
     */
    override fun showBookInfo(book: SearchBook) {
        lifecycleScope.launch {
            val isVideo = withContext(IO) {
                SearchBookOpenHelper.isVideoResult(
                    book,
                    viewModel.searchScope.getSingleBookSourcePart()?.bookSourceType
                )
            }
            if (isVideo) {
                SearchBookOpenHelper.open(this@SearchActivity, book, true)
            } else {
                SearchBookOpenHelper.open(this@SearchActivity, book, false)
            }
        }
    }

    /**
     * 是否已经加入书架
     */
    override fun showBookSourceSelector(book: SearchBook) {
        val originBookUrls = book.originBookUrls()
        if (originBookUrls.size <= 1) {
            showBookInfo(book)
            return
        }
        lifecycleScope.launch {
            val sourceBooks = withContext(IO) {
                val cachedBooks = appDb.searchBookDao.getByBookUrls(originBookUrls)
                (cachedBooks + book)
                    .distinctBy { it.originBookUrl() }
                    .sortedBy {
                        originBookUrls.indexOf(it.bookUrl).let { index ->
                            if (index >= 0) index else Int.MAX_VALUE
                        }
                    }
            }
            if (sourceBooks.size <= 1) {
                showBookInfo(sourceBooks.firstOrNull() ?: book)
                return@launch
            }
            val hasSameOrigin = sourceBooks.groupingBy { it.origin }.eachCount().any {
                it.value > 1
            }
            val sourceNames = sourceBooks.map { sourceBook ->
                buildString {
                    append(sourceBook.originName.ifBlank { sourceBook.origin })
                    if (hasSameOrigin) {
                        val desc = sourceBook.latestChapterTitle?.takeIf { it.isNotBlank() }
                            ?: sourceBook.bookUrl
                        append(" - ")
                        append(desc)
                    }
                }
            }
            selector(R.string.select_update_source, sourceNames) { _, index ->
                showBookInfo(sourceBooks[index])
            }
        }
    }

    override fun isInBookshelf(book: SearchBook): Boolean {
        return viewModel.isInBookShelf(book)
    }

    /**
     * 显示书籍详情
     */
    override fun showBookInfo(book: Book) {
        if (book.isVideo) {
            startActivityForBook(book)
            return
        }
        startActivity<BookInfoActivity> {
            putExtra("name", book.name)
            putExtra("author", book.author)
            putExtra("bookUrl", book.bookUrl)
        }
    }

    /**
     * 点击历史关键字
     */
    override fun searchHistory(key: String) {
        lifecycleScope.launch {
            when {
                searchView.query.toString() == key -> {
                    searchView.setQuery(key, true)
                }

                withContext(IO) { appDb.bookDao.findByName(key).isEmpty() } -> {
                    searchView.setQuery(key, true)
                }

                else -> {
                    searchView.setQuery(key, false)
                }
            }
        }
    }

    /**
     * 删除搜索记录
     */
    override fun deleteHistory(searchKeyword: SearchKeyword) {
        viewModel.deleteHistory(searchKeyword)
    }


    override fun onSearchScopeOk(searchScope: SearchScope) {
        viewModel.searchScope.update(searchScope.toString())
    }

    private fun alertSearchScope() {
        showDialogFragment<SearchScopeDialog>()
    }

    private fun alertClearHistory() {
        alert(R.string.draw) {
            setMessage(R.string.sure_clear_search_history)
            yesButton {
                viewModel.clearHistory()
            }
            noButton()
        }
    }

    override fun finish() {
        if (searchView.hasFocus()) {
            searchView.clearFocus()
            return
        }
        super.finish()
    }

    companion object {

        private const val COLLAPSED_SOURCE_STATUS_CHIP_COUNT = 12

        fun start(context: Context, key: String?, searchScope: String? = null) {
            context.startActivity<SearchActivity> {
                putExtra("key", key)
                putExtra("searchScope", searchScope)
            }
        }

        fun start(context: Context, source: BookSource, key: String? = null) {
            context.startActivity<SearchActivity> {
                putExtra("key", key)
                putExtra("searchScope", SearchScope(source).toString())
            }
        }

        fun start(context: Context, source: BookSourcePart, key: String? = null) {
            context.startActivity<SearchActivity> {
                putExtra("key", key)
                putExtra("searchScope", SearchScope(source).toString())
            }
        }

    }
}
