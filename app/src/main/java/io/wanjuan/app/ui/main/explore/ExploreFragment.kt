package io.wanjuan.app.ui.main.explore

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.script.rhino.runScriptWithContext
import io.wanjuan.app.R
import io.wanjuan.app.base.VMBaseFragment
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.constant.SourceType
import io.wanjuan.app.data.AppDatabase
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.BookSourcePart
import io.wanjuan.app.data.entities.SearchBook
import io.wanjuan.app.data.entities.rule.ExploreKind
import io.wanjuan.app.data.entities.rule.RowUi
import io.wanjuan.app.databinding.FragmentExploreBinding
import io.wanjuan.app.help.book.isNotShelf
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.source.clearExploreKindsCache
import io.wanjuan.app.help.source.exploreKinds
import io.wanjuan.app.lib.dialogs.alert
import io.wanjuan.app.lib.theme.accentColor
import io.wanjuan.app.lib.theme.primaryColor
import io.wanjuan.app.lib.theme.primaryTextColor
import io.wanjuan.app.model.webBook.WebBook
import io.wanjuan.app.ui.book.explore.ExploreShowAdapter
import io.wanjuan.app.ui.book.explore.ExploreShowActivity
import io.wanjuan.app.ui.book.SearchBookOpenHelper
import io.wanjuan.app.ui.book.search.SearchActivity
import io.wanjuan.app.ui.book.source.edit.BookSourceEditActivity
import io.wanjuan.app.ui.browser.WebViewActivity
import io.wanjuan.app.ui.login.SourceLoginActivity
import io.wanjuan.app.ui.login.SourceLoginJsExtensions
import io.wanjuan.app.ui.main.MainFragmentInterface
import io.wanjuan.app.ui.widget.DetailSeekBar
import io.wanjuan.app.ui.widget.GridColumnsPopup
import io.wanjuan.app.ui.widget.RoundedTagBarView
import io.wanjuan.app.ui.widget.RowUiDialog
import io.wanjuan.app.ui.widget.SourceSelectDialog
import io.wanjuan.app.utils.applyMainBottomBarPadding
import io.wanjuan.app.utils.applyStatusBarPadding
import io.wanjuan.app.utils.applyTint
import io.wanjuan.app.utils.dpToPx
import io.wanjuan.app.utils.flowWithLifecycleAndDatabaseChange
import io.wanjuan.app.utils.gone
import io.wanjuan.app.utils.InfoMap
import io.wanjuan.app.utils.NetworkUtils
import io.wanjuan.app.utils.setEdgeEffectColor
import io.wanjuan.app.utils.startActivity
import io.wanjuan.app.utils.toastOnUi
import io.wanjuan.app.utils.transaction
import io.wanjuan.app.utils.visible
import io.wanjuan.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 发现界面
 */
class ExploreFragment() : VMBaseFragment<ExploreViewModel>(R.layout.fragment_explore),
    MainFragmentInterface,
    ExploreAdapter.CallBack,
    ExploreShowAdapter.CallBack {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    override val viewModel by viewModels<ExploreViewModel>()
    private val binding by viewBinding(FragmentExploreBinding::bind)
    private val adapter by lazy { ExploreAdapter(requireContext(), this) }
    private val discoverBookAdapter by lazy { ExploreShowAdapter(requireContext(), this) }
    private val linearLayoutManager by lazy { LinearLayoutManager(context) }
    private val searchView: SearchView? by lazy {
        binding.titleBar.findViewById<SearchView?>(R.id.search_view)
    }
    private val diffItemCallBack = ExploreDiffItemCallBack()
    private val groups = linkedSetOf<String>()
    private var exploreFlowJob: Job? = null
    private var groupsMenu: SubMenu? = null
    private var oldModeInitialized = false
    private var modernModeInitialized = false
    private var usingModernDiscovery = false
    private var sourceMenuPopup: PopupWindow? = null
    private var tagFilterPopup: PopupWindow? = null
    private var discoverGridColumnsPopup: PopupWindow? = null
    private var discoverSourceFlowJob: Job? = null
    private var discoverBookshelfFlowJob: Job? = null
    private var discoverLoadJob: Job? = null
    private var discoverActionJob: Job? = null
    private val discoverSources get() = viewModel.sources
    private val discoverAllTagItems get() = viewModel.allTagItems
    private val discoverTagItems get() = viewModel.tagItems
    private val discoverSelectItems get() = viewModel.selectItems
    private val discoverSettingItems get() = viewModel.settingItems
    private val discoverMajorGroups get() = viewModel.majorGroups
    private val discoverBookshelf get() = viewModel.bookshelf
    private val discoverBooks get() = viewModel.books
    private val blockedButtonActions = hashMapOf<String, MutableSet<String>>()
    private var selectedDiscoverSourcePart: BookSourcePart?
        get() = viewModel.sourcePart
        set(value) { viewModel.sourcePart = value }
    private var selectedDiscoverSource: BookSource?
        get() = viewModel.source
        set(value) { viewModel.source = value }
    private var discoverCurrentUrl: String?
        get() = viewModel.currentUrl
        set(value) { viewModel.currentUrl = value }
    private var discoverPage: Int
        get() = viewModel.page
        set(value) { viewModel.page = value }
    private var discoverHasMore: Boolean
        get() = viewModel.hasMore
        set(value) { viewModel.hasMore = value }
    private var discoverLoading = false
    private var selectedDiscoverMajorGroup: String?
        get() = viewModel.majorGroup
        set(value) { viewModel.majorGroup = value }
    private var selectedDiscoverTagIndex: Int
        get() = viewModel.tagIndex
        set(value) { viewModel.tagIndex = value }
    private var selectedDiscoverUrlIndex: Int
        get() = viewModel.urlIndex
        set(value) { viewModel.urlIndex = value }
    private var discoverRequestVersion = 0L
    private var discoverSourceVersion = 0L
    private var discoverLoadingSignals = 0
    private var discoverLoadingGeneration = 0L
    private var discoveryModeLoaded: Boolean
        get() = viewModel.modeLoaded
        set(value) { viewModel.modeLoaded = value }

    private companion object {
        const val DISCOVER_LAYOUT_LIST = 0
        const val DISCOVER_LAYOUT_GRID = 1
        private const val DISCOVER_GRID_COLUMNS_MIN = 2
        private const val DISCOVER_GRID_COLUMNS_MAX = 7
    }

    private data class DiscoverScrollAnchor(
        val position: Int,
        val offset: Int
    )

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        usingModernDiscovery = AppConfig.modernDiscoveryPage
        binding.swipeRefreshLayout.setColorSchemeColors(accentColor)
        binding.swipeRefreshLayout.setProgressViewOffset(true, (-28).dpToPx(), 56.dpToPx())
        binding.swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            currentDiscoverScrollTarget()?.canScrollVertically(-1) == true
        }
        binding.swipeRefreshLayout.setOnRefreshListener {
            if (usingModernDiscovery) {
                loadDiscoverBooks(reset = true)
            } else {
                upExploreData(searchView?.query?.toString())
            }
        }
        binding.llDiscoverSourceRow.applyStatusBarPadding(withInitialPadding = true)
        binding.rvFind.clipToPadding = false
        binding.rvFind.applyMainBottomBarPadding()
        binding.rvDiscoverBooks.clipToPadding = false
        binding.rvDiscoverBooks.applyMainBottomBarPadding(withInitialPadding = true)
        applyDiscoveryMode(loadData = false)
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        super.onCompatCreateOptionsMenu(menu)
        if (usingModernDiscovery) {
            groupsMenu = null
            return
        }
        menuInflater.inflate(R.menu.main_explore, menu)
        groupsMenu = menu.findItem(R.id.menu_group)?.subMenu
        upGroupsMenu()
    }

    private fun applyDiscoveryMode(loadData: Boolean = true) {
        val modern = AppConfig.modernDiscoveryPage
        usingModernDiscovery = modern
        binding.titleBar.isGone = modern
        binding.llModernDiscovery.isVisible = modern
        binding.rvFind.isGone = modern
        binding.tvEmptyMsg.isGone = modern
        searchView?.isGone = modern
        if (!loadData) {
            activity?.invalidateOptionsMenu()
            return
        }
        if (modern) {
            exploreFlowJob?.cancel()
            initModernMode()
        } else {
            stopModernMode()
            initClassicMode()
        }
        activity?.invalidateOptionsMenu()
    }

    private fun currentDiscoverScrollTarget(): View? {
        return when {
            usingModernDiscovery -> binding.rvDiscoverBooks
            else -> binding.rvFind
        }
    }

    private fun showDiscoverLoading(): Long {
        discoverLoadingSignals += 1
        binding.tvDiscoverEmpty.gone()
        binding.tvDiscoverLoading.setText(R.string.data_loading)
        binding.llDiscoverLoading.visible()
        return discoverLoadingGeneration
    }

    private fun hideDiscoverLoading(generation: Long) {
        if (generation != discoverLoadingGeneration) return
        discoverLoadingSignals = (discoverLoadingSignals - 1).coerceAtLeast(0)
        if (discoverLoadingSignals == 0) {
            binding.llDiscoverLoading.gone()
        }
    }

    private fun clearDiscoverLoading() {
        discoverLoadingGeneration += 1
        discoverLoadingSignals = 0
        binding.llDiscoverLoading.gone()
    }

    private fun resetExplore() {
        discoverRequestVersion += 1
        discoverLoadJob?.cancel()
        discoverLoadJob = null
        discoverLoading = false
        binding.swipeRefreshLayout.isRefreshing = false
        discoverTagItems.clear()
        discoverSelectItems.clear()
        selectedDiscoverTagIndex = -1
        selectedDiscoverUrlIndex = -1
        discoverCurrentUrl = null
        discoverHasMore = true
        discoverPage = 1
        discoverBooks.clear()
        discoverBookAdapter.clearItems()
        binding.rvDiscoverSelects.gone()
        binding.rvDiscoverSelects.submitItems(emptyList(), -1)
        binding.rvDiscoverTags.submitItems(emptyList(), -1)
        binding.tvDiscoverEmpty.gone()
    }

    private inner class DiscoverRefreshController {
        private val loadingActive = AtomicBoolean(false)
        private val loadingGeneration = AtomicLong(Long.MIN_VALUE)
        private val refreshRequested = AtomicBoolean(false)

        val requested: Boolean
            get() = refreshRequested.get()

        fun showLoading() {
            refreshRequested.set(true)
            if (!loadingActive.compareAndSet(false, true)) return
            binding.root.post {
                if (!isAdded || !loadingActive.get()) return@post
                loadingGeneration.set(showDiscoverLoading())
                resetExplore()
            }
        }

        fun finish() {
            loadingActive.set(false)
            if (!isAdded) return
            val generation = loadingGeneration.get()
            if (generation != Long.MIN_VALUE) {
                hideDiscoverLoading(generation)
            }
        }
    }

    private fun discoverRefreshCallback(
        controller: DiscoverRefreshController,
        onOpen: (
            name: String,
            url: String?,
            title: String?,
            origin: String?
        ) -> Boolean = { _, _, _, _ -> false }
    ): SourceLoginJsExtensions.Callback {
        return object : SourceLoginJsExtensions.Callback {
            override fun upUiData(data: Map<String, Any?>?) = Unit

            override fun reUiView(deltaUp: Boolean) {
                controller.showLoading()
            }

            override fun open(
                name: String,
                url: String?,
                title: String?,
                origin: String?
            ): Boolean {
                return onOpen(name, url, title, origin)
            }
        }
    }

    private fun discoverJsExtensions(
        source: BookSource,
        controller: DiscoverRefreshController,
        onOpen: (
            name: String,
            url: String?,
            title: String?,
            origin: String?
        ) -> Boolean = { _, _, _, _ -> false }
    ): SourceLoginJsExtensions {
        return SourceLoginJsExtensions(
            activity as? AppCompatActivity,
            source,
            callback = discoverRefreshCallback(controller, onOpen)
        )
    }

    private fun initClassicMode() {
        if (!oldModeInitialized) {
            oldModeInitialized = true
            initSearchView()
            initRecyclerView()
            initGroupData()
        }
        if (exploreFlowJob?.isActive != true) {
            upExploreData(searchView?.query?.toString())
        }
    }

    private fun initModernMode() {
        if (!modernModeInitialized) {
            modernModeInitialized = true
            initDiscoverRecycler()
            bindDiscoverSourceSelector()
            updateDiscoverLoginButtonState()
        }
        observeDiscoverSources()
        observeDiscoverBookshelf()
    }

    private fun stopModernMode() {
        sourceMenuPopup?.dismiss()
        sourceMenuPopup = null
        tagFilterPopup?.dismiss()
        tagFilterPopup = null
        discoverGridColumnsPopup?.dismiss()
        discoverGridColumnsPopup = null
        discoverSourceFlowJob?.cancel()
        discoverSourceFlowJob = null
        discoverBookshelfFlowJob?.cancel()
        discoverBookshelfFlowJob = null
        discoverActionJob?.cancel()
        discoverActionJob = null
        discoverLoadJob?.cancel()
        discoverLoadJob = null
        discoverSourceVersion += 1
        discoverRequestVersion += 1
        discoverLoading = false
        clearDiscoverLoading()
        discoverAllTagItems.clear()
        discoverMajorGroups.clear()
        discoverTagItems.clear()
        discoverSelectItems.clear()
        discoverSettingItems.clear()
        selectedDiscoverMajorGroup = null
        selectedDiscoverTagIndex = -1
        selectedDiscoverUrlIndex = -1
    }

    private fun initSearchView() {
        val view = searchView ?: return
        view.applyTint(primaryTextColor)
        view.isSubmitButtonEnabled = true
        view.queryHint = getString(R.string.screen_find)
        view.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                upExploreData(newText)
                return false
            }
        })
    }

    private fun initDiscoverRecycler() {
        binding.rvDiscoverTags.setOnTagClickListener { index ->
            val item = discoverTagItems.getOrNull(index) ?: return@setOnTagClickListener
            if (item.isButton) {
                handleDiscoverButtonTag(item)
                return@setOnTagClickListener
            }
            selectDiscoverTag(index, item, selectTab = true, recordUse = true)
        }
        binding.rvDiscoverSelects.setOnTagClickListener { index ->
            val group = discoverMajorGroups.getOrNull(index) ?: return@setOnTagClickListener
            selectedDiscoverMajorGroup = group
            applyDiscoverTagFilterAndSelect(preferredUrl = discoverCurrentUrl)
        }
        applyDiscoverBookLayout()
        binding.rvDiscoverBooks.adapter = discoverBookAdapter
        if (discoverBooks.isNotEmpty()) {
            discoverBookAdapter.setItems(discoverBooks.toList())
            binding.tvDiscoverEmpty.gone()
        }
        binding.rvDiscoverBooks.setEdgeEffectColor(primaryColor)
        binding.rvDiscoverBooks.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0 && !recyclerView.canScrollVertically(1)) {
                    loadDiscoverBooks(reset = false)
                }
            }
        })
    }

    private fun bindDiscoverSourceSelector() {
        val updateSourceNameWidth = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            binding.llDiscoverSourceRow.post(::updateDiscoverSourceNameWidth)
        }
        binding.llDiscoverSourceRow.addOnLayoutChangeListener(updateSourceNameWidth)
        binding.llDiscoverSourceRow.post(::updateDiscoverSourceNameWidth)
        binding.llDiscoverSourceSelect.setOnClickListener {
            showDiscoverSourceMenu()
        }
        binding.btnDiscoverLayoutToggle.setOnClickListener {
            switchDiscoverBookLayout()
        }
        binding.btnDiscoverLayoutToggle.setOnLongClickListener {
            showDiscoverGridColumnsPopup(it)
            true
        }
        binding.btnDiscoverTagFilter.setOnClickListener {
            showDiscoverSettingsDialog()
        }
        binding.btnDiscoverMore.setOnClickListener {
            openSelectedSourceLogin()
        }
        updateDiscoverTagFilterButtonState()
    }

    private fun updateDiscoverSourceNameWidth() {
        val rowWidth = binding.llDiscoverSourceRow.width
        if (rowWidth <= 0) return
        val actionsWidth = listOf(
            binding.btnDiscoverLayoutToggle,
            binding.btnDiscoverTagFilter,
            binding.btnDiscoverMore
        ).filter { it.isVisible }.sumOf { it.measuredWidth.takeIf { width -> width > 0 } ?: it.layoutParams.width }
        val visibleActionCount = listOf(
            binding.btnDiscoverLayoutToggle,
            binding.btnDiscoverTagFilter,
            binding.btnDiscoverMore
        ).count { it.isVisible }
        val actionGap = resources.getDimensionPixelSize(R.dimen.main_top_action_button_gap)
        val spacing = 16.dpToPx() + (visibleActionCount - 1).coerceAtLeast(0) * actionGap
        val maxWidth = (rowWidth - actionsWidth - spacing).coerceIn(96.dpToPx(), 190.dpToPx())
        if (binding.tvDiscoverSourceSelect.maxWidth != maxWidth) {
            binding.tvDiscoverSourceSelect.maxWidth = maxWidth
        }
    }

    private fun openSelectedSourceLogin() {
        val source = selectedDiscoverSourcePart ?: return
        if (!source.hasLoginUrl) {
            openSelectedDiscoverPageInWebView(source)
            return
        }
        startActivity<SourceLoginActivity> {
            putExtra("type", "bookSource")
            putExtra("key", source.bookSourceUrl)
        }
    }

    private fun openSelectedDiscoverPageInWebView(source: BookSourcePart) {
        val currentUrl = discoverCurrentUrl
            ?.takeIf { it.isNotBlank() }
            ?: discoverTagItems.getOrNull(selectedDiscoverUrlIndex)
                ?.kind
                ?.url
                ?.takeIf { it.isNotBlank() }
            ?: source.bookSourceUrl
        val url = currentUrl.replace(Regex(""",\s*\{[\s\S]*\}\s*$"""), "")
        val absoluteUrl = NetworkUtils.getAbsoluteURL(source.bookSourceUrl, url)
        startActivity<WebViewActivity> {
            putExtra("url", absoluteUrl)
            putExtra("title", source.bookSourceName)
            putExtra("sourceName", source.bookSourceName)
            putExtra("sourceOrigin", source.bookSourceUrl)
            putExtra("sourceType", SourceType.book)
            putExtra("refetchAfterSuccess", false)
        }
    }

    private fun updateDiscoverLoginButtonState() {
        val hasLoginUrl = selectedDiscoverSourcePart?.hasLoginUrl == true
        binding.btnDiscoverMore.setImageResource(
            if (hasLoginUrl) R.drawable.ic_lucide_user else R.drawable.ic_lucide_link_2
        )
        binding.btnDiscoverMore.contentDescription = getString(
            if (hasLoginUrl) R.string.login else R.string.open_in_app_webview
        )
        binding.btnDiscoverMore.alpha = 1f
        binding.llDiscoverSourceRow.post(::updateDiscoverSourceNameWidth)
    }

    private fun switchDiscoverBookLayout() {
        val currentStyle = normalizeDiscoverBookLayout(AppConfig.modernDiscoveryLayout)
        val nextStyle = if (currentStyle == DISCOVER_LAYOUT_LIST) {
            DISCOVER_LAYOUT_GRID
        } else {
            DISCOVER_LAYOUT_LIST
        }
        AppConfig.modernDiscoveryLayout = nextStyle
        applyDiscoverBookLayout()
    }

    private fun showDiscoverGridColumnsPopup(anchor: View) {
        discoverGridColumnsPopup = GridColumnsPopup.show(
            anchor = anchor,
            titleRes = R.string.discover_grid_columns,
            minColumns = DISCOVER_GRID_COLUMNS_MIN,
            maxColumns = DISCOVER_GRID_COLUMNS_MAX,
            initialColumns = AppConfig.modernDiscoveryGridColumns,
            previousPopup = discoverGridColumnsPopup,
            onColumnsChanging = ::updateDiscoverGridColumns,
            onColumnsChanged = ::updateDiscoverGridColumns
        )
    }

    private fun updateDiscoverGridColumns(columns: Int) {
        val gridColumns = columns.coerceIn(DISCOVER_GRID_COLUMNS_MIN, DISCOVER_GRID_COLUMNS_MAX)
        val currentStyle = normalizeDiscoverBookLayout(AppConfig.modernDiscoveryLayout)
        if (AppConfig.modernDiscoveryGridColumns == gridColumns && currentStyle == DISCOVER_LAYOUT_GRID) {
            return
        }
        AppConfig.modernDiscoveryGridColumns = gridColumns
        if (currentStyle != DISCOVER_LAYOUT_GRID) {
            AppConfig.modernDiscoveryLayout = DISCOVER_LAYOUT_GRID
        }
        applyDiscoverBookLayout()
    }

    private fun applyDiscoverBookLayout() {
        val style = normalizeDiscoverBookLayout(AppConfig.modernDiscoveryLayout)
        if (AppConfig.modernDiscoveryLayout != style) {
            AppConfig.modernDiscoveryLayout = style
        }
        discoverBookAdapter.layoutStyle = style
        discoverBookAdapter.gridColumns = AppConfig.modernDiscoveryGridColumns
        binding.rvDiscoverBooks.layoutManager = when (style) {
            DISCOVER_LAYOUT_GRID -> GridLayoutManager(requireContext(), AppConfig.modernDiscoveryGridColumns)
            else -> LinearLayoutManager(requireContext())
        }
        updateDiscoverLayoutToggleIcon(style)
        updateDiscoverTagFilterButtonState()
        discoverBookAdapter.notifyDataSetChanged()
    }

    private fun normalizeDiscoverBookLayout(style: Int): Int {
        return if (style == DISCOVER_LAYOUT_LIST) {
            DISCOVER_LAYOUT_LIST
        } else {
            DISCOVER_LAYOUT_GRID
        }
    }

    private fun updateDiscoverLayoutToggleIcon(style: Int) {
        binding.btnDiscoverLayoutToggle.setImageResource(
            if (style == DISCOVER_LAYOUT_LIST) {
                R.drawable.ic_lucide_layout_grid
            } else {
                R.drawable.ic_lucide_layout_list
            }
        )
    }

    fun openSearchFromMain(): Boolean {
        if (!usingModernDiscovery) return false
        val source = selectedDiscoverSource ?: return false
        if (source.searchUrl.isNullOrBlank()) {
            context?.toastOnUi(R.string.search_book_key)
            return true
        }
        SearchActivity.start(requireContext(), source)
        return true
    }

    private fun observeDiscoverSources() {
        if (discoverSourceFlowJob?.isActive == true) return
        discoverSourceFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookSourceDao.flowExplore()
                .flowWithLifecycleAndDatabaseChange(
                    viewLifecycleOwner.lifecycle,
                    Lifecycle.State.STARTED,
                    AppDatabase.BOOK_SOURCE_TABLE_NAME
                )
                .conflate()
                .distinctUntilChanged()
                .collect { list ->
                    val sortedList = viewModel.sortDiscoverSources(list)
                    discoverSources.clear()
                    discoverSources.addAll(sortedList)
                    if (discoverSources.isEmpty()) {
                        selectedDiscoverSourcePart = null
                        selectedDiscoverSource = null
                        AppConfig.modernDiscoverySourceUrl = null
                        discoverCurrentUrl = null
                        discoverAllTagItems.clear()
                        discoverMajorGroups.clear()
                        discoverSettingItems.clear()
                        selectedDiscoverMajorGroup = null
                        clearDiscoverBooksToEmpty(getString(R.string.explore_empty))
                        renderDiscoverTags(emptyList(), -1)
                        renderDiscoverMajorGroups()
                        binding.tvDiscoverSourceSelect.text = getString(R.string.explore_empty)
                        updateDiscoverLoginButtonState()
                        updateDiscoverTagFilterButtonState()
                        clearDiscoverLoading()
                        return@collect
                    }
                    val keepSource = selectedDiscoverSourcePart?.bookSourceUrl
                        ?: AppConfig.modernDiscoverySourceUrl
                    val selected = discoverSources.firstOrNull { it.bookSourceUrl == keepSource }
                        ?: discoverSources.first()
                    val needsInitialLoad = selectedDiscoverSourcePart?.bookSourceUrl != selected.bookSourceUrl
                            || (discoverTagItems.isEmpty() && discoverBooks.isEmpty())
                    if (needsInitialLoad) {
                        selectDiscoverSource(selected)
                    } else {
                        updateDiscoverSourceTitle()
                        updateDiscoverLoginButtonState()
                    }
                }
        }
    }

    private fun observeDiscoverBookshelf() {
        if (discoverBookshelfFlowJob?.isActive == true) return
        discoverBookshelfFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowAll()
                .flowWithLifecycleAndDatabaseChange(
                    viewLifecycleOwner.lifecycle,
                    Lifecycle.State.STARTED,
                    AppDatabase.BOOK_TABLE_NAME
                )
                .conflate()
                .distinctUntilChanged()
                .collect { books ->
                    discoverBookshelf.clear()
                    books.filterNot { it.isNotShelf }
                        .forEach {
                            discoverBookshelf.add("${it.name}-${it.author}")
                            discoverBookshelf.add(it.name)
                            discoverBookshelf.add(it.bookUrl)
                        }
                    if (discoverBookAdapter.itemCount > 0) {
                        discoverBookAdapter.notifyItemRangeChanged(
                            0,
                            discoverBookAdapter.itemCount,
                            bundleOf("isInBookshelf" to null)
                        )
                    }
                }
        }
    }

    private fun showDiscoverSourceMenu() {
        if (discoverSources.isEmpty()) return
        SourceSelectDialog.show(
            context = requireContext(),
            title = getString(R.string.book_source),
            items = discoverSources,
            selectedKey = selectedDiscoverSourcePart?.bookSourceUrl,
            searchHint = getString(R.string.screen_find),
            displayName = { it.getDisPlayNameGroup() },
            searchTexts = {
                listOfNotNull(it.bookSourceName, it.bookSourceUrl, it.bookSourceGroup)
            },
            itemKey = { it.bookSourceUrl }
        ) {
            recordDiscoverSourceUse(it.bookSourceUrl)
            selectDiscoverSource(it)
        }
    }

    private fun recordDiscoverSourceUse(sourceUrl: String?, increment: Int = 1) {
        viewModel.recordDiscoverSourceUse(sourceUrl, increment)
    }

    private fun selectDiscoverSource(source: BookSourcePart) {
        selectedDiscoverSourcePart = source
        AppConfig.modernDiscoverySourceUrl = source.bookSourceUrl
        updateDiscoverLoginButtonState()
        tagFilterPopup?.dismiss()
        tagFilterPopup = null
        discoverSourceVersion += 1
        val currentSourceVersion = discoverSourceVersion
        discoverRequestVersion += 1
        discoverLoadJob?.cancel()
        discoverLoadJob = null
        discoverLoading = false
        clearDiscoverLoading()
        discoverCurrentUrl = null
        discoverBooks.clear()
        discoverBookAdapter.clearItems()
        binding.tvDiscoverEmpty.gone()
        discoverAllTagItems.clear()
        discoverMajorGroups.clear()
        discoverSelectItems.clear()
        discoverSettingItems.clear()
        selectedDiscoverMajorGroup = null
        renderDiscoverTags(emptyList(), -1)
        renderDiscoverMajorGroups()
        updateDiscoverTagFilterButtonState()
        viewLifecycleOwner.lifecycleScope.launch {
            val loadingGeneration = showDiscoverLoading()
            try {
                val fullSource = withContext(IO) {
                    appDb.bookSourceDao.getBookSource(source.bookSourceUrl)
                }
                if (currentSourceVersion != discoverSourceVersion || !isAdded) {
                    return@launch
                }
                selectedDiscoverSource = fullSource
                updateDiscoverSourceTitle()
                loadDiscoverKindsAndDefault()
            } finally {
                if (isAdded && currentSourceVersion == discoverSourceVersion) {
                    hideDiscoverLoading(loadingGeneration)
                }
            }
        }
    }

    private fun updateDiscoverSourceTitle() {
        val name = selectedDiscoverSourcePart?.bookSourceName
            ?: getString(R.string.discovery)
        binding.tvDiscoverSourceSelect.text = name
        binding.llDiscoverSourceRow.post(::updateDiscoverSourceNameWidth)
    }

    private suspend fun loadDiscoverKindsAndDefault() {
        val source = selectedDiscoverSource ?: return
        val kinds = withContext(IO) {
            source.exploreKinds()
        }
        val items = buildDiscoverTagItems(source, kinds)
        discoverAllTagItems.clear()
        discoverAllTagItems.addAll(items)
        if (items.isEmpty()) {
            discoverMajorGroups.clear()
            selectedDiscoverMajorGroup = null
            renderDiscoverTags(emptyList(), -1)
            renderDiscoverMajorGroups()
            updateDiscoverTagFilterButtonState()
            clearDiscoverBooksToEmpty(getString(R.string.explore_empty))
            return
        }
        applyDiscoverTagFilterAndSelect(preferredUrl = discoverCurrentUrl)
    }

    private fun buildDiscoverTagItems(
        source: BookSource,
        kinds: List<ExploreKind>
    ): List<DiscoverTagItem> {
        val blocked = blockedButtonActions[source.bookSourceUrl]
        var currentGroup: String? = null
        val result = mutableListOf<DiscoverTagItem>()
        kinds.forEach { kind ->
            val action = kind.action?.takeIf { it.isNotBlank() }
            val url = kind.url?.takeIf { it.isNotBlank() }
            val isSelect = kind.type == ExploreKind.Type.select
            val isButton = kind.type == ExploreKind.Type.button && !action.isNullOrBlank()

            if (isDiscoverMajorGroupKind(kind)) {
                currentGroup = resolveDiscoverGroupTitle(kind)
                return@forEach
            }

            if (!url.isNullOrBlank() && !isButton && !isSelect) {
                result += DiscoverTagItem(
                    kind = kind.copy(url = url),
                    text = resolveDiscoverTagText(kind),
                    isButton = false,
                    group = currentGroup
                )
                return@forEach
            }

            if (isSelect) {
                result += DiscoverTagItem(
                    kind = kind.copy(type = ExploreKind.Type.select),
                    text = resolveDiscoverTagText(kind),
                    isButton = false,
                    group = currentGroup
                )
                return@forEach
            }

            if (!action.isNullOrBlank()) {
                if (blocked?.contains(action) == true) return@forEach
                result += DiscoverTagItem(
                    kind = kind.copy(type = ExploreKind.Type.button),
                    text = resolveDiscoverTagText(kind),
                    isButton = true,
                    group = currentGroup
                )
            }
        }
        val hasMajorGroup = result.any { !it.group.isNullOrBlank() }
        val normalized = if (hasMajorGroup) {
            result
        } else {
            result.map { it.copy(group = getString(R.string.discover_group_other)) }
        }
        return normalized.distinctBy { "${it.group}|${it.kind.type}|${it.kind.title}|${it.kind.url}|${it.kind.action}" }
    }

    private fun isDiscoverMajorGroupKind(kind: ExploreKind): Boolean {
        if (!kind.action.isNullOrBlank() || !kind.url.isNullOrBlank()) return false
        if (kind.type == ExploreKind.Type.button || kind.type == ExploreKind.Type.select) return false
        val style = kind.style()
        if (style.layout_flexBasisPercent >= 0.95f) return true
        if (style.layout_flexGrow >= 1f && style.layout_flexBasisPercent < 0f) return true
        return false
    }

    private fun resolveDiscoverGroupTitle(kind: ExploreKind): String {
        val raw = resolveDiscoverTagText(kind).trim()
        if (raw.isBlank()) return getString(R.string.discovery)
        val normalized = raw
            .replace(Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        return normalized.ifBlank { raw }
    }

    private fun resolveDiscoverTagText(kind: ExploreKind): String {
        val viewName = kind.viewName
        if (!viewName.isNullOrBlank()
            && viewName.length in 3..28
            && viewName.first() == '\''
            && viewName.last() == '\''
        ) {
            return viewName.substring(1, viewName.length - 1)
        }
        return kind.title.ifBlank { kind.type }
    }

    private fun applyDiscoverTagFilterAndSelect(preferredUrl: String?) {
        val groupList = discoverAllTagItems
            .mapNotNull { it.group?.takeIf { name -> name.isNotBlank() } }
            .distinct()
        discoverMajorGroups.clear()
        discoverMajorGroups.addAll(groupList)

        if (discoverMajorGroups.isEmpty()) {
            selectedDiscoverMajorGroup = null
        } else {
            if (selectedDiscoverMajorGroup !in discoverMajorGroups) {
                selectedDiscoverMajorGroup = discoverMajorGroups.first()
            }
        }

        val filtered = if (discoverMajorGroups.isEmpty()) {
            discoverAllTagItems.toList()
        } else {
            discoverAllTagItems.filter { it.group == selectedDiscoverMajorGroup }
        }

        discoverSettingItems.clear()
        discoverSettingItems.addAll(buildDiscoverSettingItems())
        renderDiscoverMajorGroups()
        updateDiscoverTagFilterButtonState()
        val tagItems = filtered.filter { it.kind.type != ExploreKind.Type.select && !it.isButton }
        val targetIndexByUrl = preferredUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { url ->
                tagItems.indexOfFirst { !it.isButton && it.kind.url == url }
                    .takeIf { idx -> idx >= 0 }
            }
        val targetIndex = targetIndexByUrl
            ?: tagItems.indexOfFirst { !it.isButton && !it.kind.url.isNullOrBlank() }
        renderDiscoverTags(tagItems, targetIndex)
        if (targetIndex >= 0) {
            selectDiscoverTag(targetIndex, tagItems[targetIndex], selectTab = true)
        } else {
            clearDiscoverBooksToEmpty(getString(R.string.explore_empty))
        }
    }

    private fun updateDiscoverTagFilterButtonState() {
        val enabled = discoverSettingItems.isNotEmpty()
        binding.btnDiscoverTagFilter.isVisible = enabled
        binding.btnDiscoverTagFilter.isEnabled = enabled
        binding.btnDiscoverTagFilter.alpha = if (enabled) 1f else 0.45f
        binding.llDiscoverSourceRow.post(::updateDiscoverSourceNameWidth)
    }

    private fun buildDiscoverSettingItems(): List<DiscoverTagItem> {
        val hasMajorGroup = discoverMajorGroups.isNotEmpty()
        return discoverAllTagItems.filter {
            it.kind.type == ExploreKind.Type.select
                || it.isButton
                || (hasMajorGroup && it.group.isNullOrBlank())
        }
    }

    private fun showDiscoverSettingsDialog() {
        val rows = buildDiscoverSettingsRows()
        val showGridColumns = normalizeDiscoverBookLayout(AppConfig.modernDiscoveryLayout) == DISCOVER_LAYOUT_GRID
        if (rows.isEmpty() && !showGridColumns) return
        val itemMap = discoverSettingItems.associateBy { it.toDiscoverRowUi().name }
        RowUiDialog.show(
            requireContext(),
            RowUiDialog.Config(
                title = getString(R.string.discovery_settings_title),
                rows = rows,
                values = buildDiscoverSettingsValues(),
                dismissOnAction = true,
                dismissOnSelect = true,
                dismissOnToggle = false
            ),
            object : RowUiDialog.Callback {
                override fun onValueChanged(rowUi: RowUi, value: String) {
                    val item = itemMap[rowUi.name] ?: return
                    when (rowUi.type) {
                        RowUi.Type.select -> handleDiscoverSelectValue(item, value)
                    }
                }

                override fun onAction(rowUi: RowUi, isLongClick: Boolean) {
                    val item = itemMap[rowUi.name] ?: return
                    if (item.isButton) {
                        handleDiscoverButtonTag(item)
                    } else {
                        selectDiscoverSettingUrl(item)
                    }
                }
            }
        ).also { dialog ->
            if (showGridColumns) {
                addDiscoverGridColumnsSeekBar(dialog.findViewById(android.R.id.content))
            }
        }
    }

    private fun buildDiscoverSettingsRows(): List<RowUi> {
        return discoverSettingItems.map { it.toDiscoverRowUi() }
    }

    private fun buildDiscoverSettingsValues(): Map<String, String> {
        return buildMap {
            putAll(discoverSettingItems.associate {
                it.toDiscoverRowUi().name to currentDiscoverSelectValue(it)
            })
        }
    }

    private fun addDiscoverGridColumnsSeekBar(content: View?) {
        val root = content as? ViewGroup ?: return
        val dialogView = root.getChildAt(0) as? ViewGroup ?: return
        val panelRoot = dialogView.getChildAt(0) as? LinearLayout ?: return
        val scrollView = panelRoot.getChildAt(1) as? ScrollView ?: return
        val form = scrollView.getChildAt(0) as? FlexboxLayout ?: return
        val seekBar = DetailSeekBar(requireContext()).apply {
            max = DISCOVER_GRID_COLUMNS_MAX - DISCOVER_GRID_COLUMNS_MIN
            progress = (AppConfig.modernDiscoveryGridColumns - DISCOVER_GRID_COLUMNS_MIN)
                .coerceIn(0, max)
            valueFormat = { (it + DISCOVER_GRID_COLUMNS_MIN).toString() }
            onChanged = { progress ->
                AppConfig.modernDiscoveryGridColumns = progress + DISCOVER_GRID_COLUMNS_MIN
                applyDiscoverBookLayout()
            }
        }
        seekBar.findViewById<android.widget.TextView>(R.id.tv_seek_title)
            ?.setText(R.string.discover_grid_columns)
        form.addView(
            seekBar,
            0,
            FlexboxLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(4.dpToPx(), 6.dpToPx(), 4.dpToPx(), 6.dpToPx())
            }
        )
    }

    private fun DiscoverTagItem.toDiscoverRowUi(): RowUi {
        val type = when {
            kind.type == ExploreKind.Type.select -> RowUi.Type.select
            isButton || isDefaultUrlKind -> RowUi.Type.button
            else -> RowUi.Type.text
        }
        return RowUi(
            name = if (type == RowUi.Type.select) kind.title else text,
            type = type,
            action = kind.action,
            chars = kind.chars,
            default = kind.default,
            viewName = kind.viewName,
            style = kind.style
        )
    }

    private val DiscoverTagItem.isDefaultUrlKind: Boolean
        get() = kind.type == ExploreKind.Type.url && !kind.url.isNullOrBlank()

    private fun selectDiscoverSettingUrl(item: DiscoverTagItem) {
        val url = item.kind.url?.takeIf { it.isNotBlank() } ?: return
        if (executeDiscoverUrlScriptIfNeeded(item, url)) {
            return
        }
        selectedDiscoverTagIndex = -1
        selectedDiscoverUrlIndex = -1
        binding.rvDiscoverTags.setSelectedIndex(-1, smooth = false)
        if (discoverCurrentUrl == url && discoverBooks.isNotEmpty()) {
            return
        }
        discoverCurrentUrl = url
        loadDiscoverBooks(reset = true)
    }

    private fun renderDiscoverTags(items: List<DiscoverTagItem>, selectedIndex: Int) {
        discoverTagItems.clear()
        discoverTagItems.addAll(items)
        selectedDiscoverTagIndex = selectedIndex.coerceIn(-1, items.lastIndex)
        selectedDiscoverUrlIndex = if (selectedDiscoverTagIndex in items.indices && !items[selectedDiscoverTagIndex].isButton) {
            selectedDiscoverTagIndex
        } else {
            items.indexOfFirst { !it.isButton && !it.kind.url.isNullOrBlank() }
        }
        binding.rvDiscoverTags.submitItems(
            items.map { RoundedTagBarView.Item(it.text, if (it.isButton) 0.9f else 1f, showFullText = true) },
            selectedDiscoverTagIndex
        )
    }

    private fun renderDiscoverMajorGroups() {
        discoverSelectItems.clear()
        if (discoverMajorGroups.isEmpty()) {
            binding.rvDiscoverSelects.gone()
            binding.rvDiscoverSelects.submitItems(emptyList(), -1)
            return
        }
        binding.rvDiscoverSelects.visible()
        binding.rvDiscoverSelects.submitItems(
            discoverMajorGroups.map { RoundedTagBarView.Item(it, 1f, showFullText = true) },
            discoverMajorGroups.indexOf(selectedDiscoverMajorGroup)
        )
    }

    private fun currentDiscoverSelectValue(item: DiscoverTagItem): String {
        val source = selectedDiscoverSource ?: return item.kind.default ?: ""
        val key = item.kind.title
        if (key.isBlank()) return item.kind.default ?: ""
        val info = getDiscoverInfoMap(source.bookSourceUrl)
        return info[key]?.takeIf { it.isNotBlank() }
            ?: item.kind.default?.takeIf { it.isNotBlank() }
            ?: item.kind.chars?.firstOrNull()?.orEmpty()
            ?: ""
    }

    private fun handleDiscoverSelectValue(item: DiscoverTagItem, value: String) {
        val source = selectedDiscoverSource ?: return
        val key = item.kind.title
        if (key.isBlank()) return
        val infoMap = getDiscoverInfoMap(source.bookSourceUrl)
        infoMap[key] = value
        val refreshController = DiscoverRefreshController()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(IO) {
                    source.clearExploreKindsCache()
                    val action = item.kind.action?.takeIf { it.isNotBlank() }
                    if (!action.isNullOrBlank()) {
                        runScriptWithContext {
                            source.evalJS(action) {
                                put(
                                    "java",
                                    discoverJsExtensions(source, refreshController)
                                )
                                put("infoMap", infoMap)
                            }
                        }
                    }
                }
                loadDiscoverKindsAndDefault()
            } catch (_: CancellationException) {
                return@launch
            } catch (e: Throwable) {
                AppLog.put("发现选项执行失败", e)
                if (isAdded) {
                    showDiscoverRuleExecutionError(item.text)
                }
            } finally {
                refreshController.finish()
            }
        }
    }

    private fun selectDiscoverTabByCode(index: Int, smooth: Boolean) {
        if (index !in discoverTagItems.indices) return
        binding.rvDiscoverTags.setSelectedIndex(index, smooth)
    }

    private fun selectDiscoverTag(
        index: Int,
        item: DiscoverTagItem,
        selectTab: Boolean,
        recordUse: Boolean = false
    ) {
        val url = item.kind.url?.takeIf { it.isNotBlank() } ?: return
        if (executeDiscoverUrlScriptIfNeeded(item, url)) {
            return
        }
        selectedDiscoverTagIndex = index
        selectedDiscoverUrlIndex = index
        if (selectTab) {
            selectDiscoverTabByCode(index, smooth = true)
        }
        if (discoverCurrentUrl == url && discoverBooks.isNotEmpty()) {
            return
        }
        discoverCurrentUrl = url
        if (recordUse) {
            recordDiscoverSourceUse(selectedDiscoverSource?.bookSourceUrl)
        }
        loadDiscoverBooks(reset = true)
    }

    private fun executeDiscoverUrlScriptIfNeeded(item: DiscoverTagItem, url: String): Boolean {
        val script = extractDiscoverUrlScript(url) ?: return false
        val source = selectedDiscoverSource ?: return true
        val infoMap = getDiscoverInfoMap(source.bookSourceUrl)
        val refreshController = DiscoverRefreshController()
        discoverActionJob?.cancel()
        discoverActionJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
            val result = withContext(IO) {
                kotlin.runCatching {
                    runScriptWithContext {
                        source.evalJS(script) {
                            put(
                                "java",
                                discoverJsExtensions(source, refreshController)
                            )
                            put("infoMap", infoMap)
                        }
                    }
                }
            }
            if (refreshController.requested && isAdded) {
                withContext(IO) {
                    source.clearExploreKindsCache()
                }
                loadDiscoverKindsAndDefault()
            }
            result.onFailure {
                AppLog.put("发现 URL 脚本执行失败: ${item.text}", it)
                showDiscoverRuleExecutionError(item.text)
            }
            } finally {
                refreshController.finish()
            }
        }
        return true
    }

    private fun extractDiscoverUrlScript(url: String): String? {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("{{") && trimmed.endsWith("}}") -> {
                trimmed.substring(2, trimmed.length - 2).trim()
            }
            trimmed.startsWith("{\\{") && trimmed.endsWith("}}") -> {
                trimmed.substring(3, trimmed.length - 2).trim()
            }
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    private fun showDiscoverRuleExecutionError(label: String) {
        if (!isAdded) return
        val safeLabel = label
            .takeIf { it.isNotBlank() }
            ?.limitDiscoverText(18)
            ?: getString(R.string.discovery)
        binding.tvDiscoverEmpty.text = getString(R.string.discover_rule_error, safeLabel)
        binding.tvDiscoverEmpty.visible()
    }

    private fun handleDiscoverButtonTag(item: DiscoverTagItem) {
        val source = selectedDiscoverSource ?: return
        val action = item.kind.action?.takeIf { it.isNotBlank() } ?: return
        val infoMap = getDiscoverInfoMap(source.bookSourceUrl)
        val actionLower = action.lowercase()
        val isNavigationAction = actionLower.contains("showbrowser(")
            || actionLower.contains("open(\"explore\"")
            || actionLower.contains("open('explore'")
        val refreshController = DiscoverRefreshController()
        discoverActionJob?.cancel()
        discoverActionJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
            val result = withContext(IO) {
                kotlin.runCatching {
                    var handledByAction = false
                    val java = discoverJsExtensions(
                        source,
                        refreshController
                    ) { name, url, title, origin ->
                            if (!isAdded) return@discoverJsExtensions false
                            if (name != "explore") return@discoverJsExtensions false
                            handledByAction = true
                            val targetUrl = url?.takeIf { it.isNotBlank() } ?: return@discoverJsExtensions true
                            val targetSourceUrl = origin
                                ?.takeIf { it.isNotBlank() }
                                ?: selectedDiscoverSource?.bookSourceUrl
                                ?: source.bookSourceUrl
                            val targetTitle = title ?: item.text
                            binding.root.post {
                                openExplore(targetSourceUrl, targetTitle, targetUrl)
                            }
                            true
                    }
                    runScriptWithContext {
                        source.evalJS(action) {
                            put("java", java)
                            put("infoMap", infoMap)
                        }
                    }
                    when {
                        handledByAction || isNavigationAction -> null
                        else -> {
                            source.clearExploreKindsCache()
                            source.exploreKinds()
                        }
                    }
                }
            }
            if (!isAdded) return@launch
            result.onSuccess { kinds ->
                if (kinds == null) {
                    return@onSuccess
                }
                applyDiscoverButtonResult(source, action, kinds)
            }.onFailure {
                AppLog.put("发现标签按钮执行失败", it)
                showDiscoverRuleExecutionError(item.text)
            }
            } finally {
                refreshController.finish()
            }
        }
    }

    private fun applyDiscoverButtonResult(
        source: BookSource,
        action: String,
        kinds: List<ExploreKind>
    ) {
        val items = buildDiscoverTagItems(source, kinds)
        val firstUrlIndex = items.indexOfFirst { !it.isButton && !it.kind.url.isNullOrBlank() }
        if (firstUrlIndex >= 0) {
            discoverAllTagItems.clear()
            discoverAllTagItems.addAll(items)
            applyDiscoverTagFilterAndSelect(preferredUrl = discoverCurrentUrl)
            return
        }
        if (items.isNotEmpty()) {
            discoverAllTagItems.clear()
            discoverAllTagItems.addAll(items)
            applyDiscoverTagFilterAndSelect(preferredUrl = discoverCurrentUrl)
        }
        context?.toastOnUi("该按钮未返回可用列表，保留当前标签")
    }

    private fun getDiscoverInfoMap(sourceUrl: String): InfoMap {
        return ExploreAdapter.exploreInfoMapList[sourceUrl] ?: InfoMap(sourceUrl).also {
            ExploreAdapter.exploreInfoMapList.put(sourceUrl, it)
        }
    }

    private fun clearDiscoverBooksToEmpty(message: String) {
        discoverRequestVersion += 1
        discoverLoadJob?.cancel()
        discoverLoadJob = null
        discoverLoading = false
        binding.swipeRefreshLayout.isRefreshing = false
        clearDiscoverLoading()
        discoverCurrentUrl = null
        discoverHasMore = false
        discoverPage = 1
        discoverBooks.clear()
        discoverBookAdapter.clearItems()
        binding.tvDiscoverEmpty.text = message
        binding.tvDiscoverEmpty.visible()
    }

    private fun loadDiscoverBooks(reset: Boolean) {
        if (!usingModernDiscovery) return
        val source = selectedDiscoverSource ?: return
        val url = discoverCurrentUrl?.takeIf { it.isNotBlank() } ?: return
        if (!reset && !discoverHasMore) return
        if (reset) {
            discoverLoadJob?.cancel()
        } else if (discoverLoading) {
            return
        }
        val requestVersion = if (reset) {
            discoverRequestVersion += 1
            discoverRequestVersion
        } else {
            discoverRequestVersion
        }
        discoverLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            if (reset) {
                discoverPage = 1
                discoverHasMore = true
                discoverBooks.clear()
                discoverBookAdapter.clearItems()
                binding.tvDiscoverEmpty.gone()
            }
            discoverLoading = true
            val loadingGeneration = showDiscoverLoading()
            try {
                val newBooks = withContext(IO) {
                    WebBook.exploreBookAwait(source, url, discoverPage)
                }
                if (!isAdded || requestVersion != discoverRequestVersion || url != discoverCurrentUrl) {
                    return@launch
                }
                val appendBooks = filterDiscoverBooksForAppend(newBooks)
                if (appendBooks.isEmpty()) {
                    discoverHasMore = false
                    if (discoverBooks.isEmpty()) {
                        binding.tvDiscoverEmpty.text = getString(R.string.explore_empty)
                        binding.tvDiscoverEmpty.visible()
                    }
                } else {
                    withContext(IO) {
                        appDb.searchBookDao.insert(*appendBooks.toTypedArray())
                    }
                    val oldBookCount = discoverBooks.size
                    discoverPage += 1
                    appendDiscoverBooks(reset, oldBookCount, appendBooks)
                    binding.tvDiscoverEmpty.gone()
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (e: Throwable) {
                if (!isAdded || requestVersion != discoverRequestVersion || url != discoverCurrentUrl) {
                    return@launch
                }
                AppLog.put("新版发现页加载失败", e)
                if (discoverBooks.isEmpty()) {
                    binding.tvDiscoverEmpty.text = e.localizedMessage ?: getString(R.string.unknown_error)
                    binding.tvDiscoverEmpty.visible()
                }
            } finally {
                if (isAdded) {
                    hideDiscoverLoading(loadingGeneration)
                }
                if (isAdded && requestVersion == discoverRequestVersion && url == discoverCurrentUrl) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    discoverLoading = false
                }
            }
        }
    }

    private fun appendDiscoverBooks(reset: Boolean, oldBookCount: Int, appendBooks: List<SearchBook>) {
        val oldAdapterItemCount = discoverBookAdapter.getActualItemCount()
        discoverBooks.addAll(appendBooks)
        when {
            reset || oldAdapterItemCount != oldBookCount || oldAdapterItemCount <= 0 -> {
                discoverBookAdapter.setItems(discoverBooks.toList())
                restoreDiscoverScrollIfNeeded()
            }

            shouldRefreshDiscoverAppendForGrid(reset, oldBookCount) -> {
                val anchor = captureDiscoverScrollAnchor()
                discoverBookAdapter.setItems(discoverBooks.toList())
                restoreDiscoverScrollAnchor(anchor)
            }

            else -> {
                discoverBookAdapter.addItems(appendBooks)
            }
        }
    }

    private fun shouldRefreshDiscoverAppendForGrid(reset: Boolean, oldBookCount: Int): Boolean {
        return !reset &&
            oldBookCount > 0 &&
            AppConfig.modernDiscoveryGridColumns > 1 &&
            normalizeDiscoverBookLayout(AppConfig.modernDiscoveryLayout) == DISCOVER_LAYOUT_GRID &&
            oldBookCount % AppConfig.modernDiscoveryGridColumns != 0
    }

    private fun filterDiscoverBooksForAppend(newBooks: List<SearchBook>): List<SearchBook> {
        val seenBookUrls = discoverBooks.mapTo(hashSetOf()) { it.bookUrl }
        return newBooks.filter { book ->
            book.bookUrl.isNotBlank()
                    && book.name.isNotBlank()
                    && !isDiscoverNavigationBook(book)
                    && seenBookUrls.add(book.bookUrl)
        }
    }

    private fun isDiscoverNavigationBook(book: SearchBook): Boolean {
        val normalizedName = book.name.trim().lowercase()
        val navigationNames = setOf(
            "next",
            "next page",
            "more",
            ">",
            ">>",
            "\u00bb",
            "\u4e0b\u4e00\u9875",
            "\u4e0b\u4e00\u9801",
            "\u4e0b\u9875",
            "\u4e0b\u9801"
        )
        return normalizedName in navigationNames
    }

    private fun initRecyclerView() {
        binding.rvFind.setEdgeEffectColor(primaryColor)
        binding.rvFind.layoutManager = linearLayoutManager
        binding.rvFind.adapter = adapter
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                if (positionStart == 0) {
                    binding.rvFind.scrollToPosition(0)
                }
            }
        })
    }

    private fun initGroupData() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookSourceDao.flowExploreGroups()
                .flowWithLifecycleAndDatabaseChange(
                    viewLifecycleOwner.lifecycle,
                    Lifecycle.State.RESUMED,
                    AppDatabase.BOOK_SOURCE_TABLE_NAME
                )
                .conflate()
                .distinctUntilChanged()
                .collect {
                    groups.clear()
                    groups.addAll(it)
                    upGroupsMenu()
                    delay(500)
                }
        }
    }

    private fun upExploreData(searchKey: String? = null) {
        exploreFlowJob?.cancel()
        exploreFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            when {
                searchKey.isNullOrBlank() -> {
                    appDb.bookSourceDao.flowExplore()
                }

                searchKey.startsWith("group:") -> {
                    val key = searchKey.substringAfter("group:")
                    appDb.bookSourceDao.flowGroupExplore(key)
                }

                else -> {
                    appDb.bookSourceDao.flowExplore(searchKey)
                }
            }.flowWithLifecycleAndDatabaseChange(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.BOOK_SOURCE_TABLE_NAME
            ).catch {
                AppLog.put("发现界面更新数据出错", it)
            }.conflate().flowOn(IO).collect {
                val sortedList = viewModel.sortDiscoverSources(it)
                binding.swipeRefreshLayout.isRefreshing = false
                binding.tvEmptyMsg.isGone =
                    sortedList.isNotEmpty() || (searchView?.query?.isNotEmpty() == true)
                adapter.setItems(sortedList, diffItemCallBack)
                delay(500)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val modernMode = AppConfig.modernDiscoveryPage
        val needsViewInitialization = if (modernMode) {
            !modernModeInitialized
        } else {
            !oldModeInitialized
        }
        if (usingModernDiscovery != modernMode || !discoveryModeLoaded || needsViewInitialization) {
            applyDiscoveryMode(loadData = true)
            discoveryModeLoaded = true
        }
        if (!usingModernDiscovery) {
            adapter.upResumed(true)
        }
    }

    override fun onPause() {
        if (usingModernDiscovery) {
            saveDiscoverScrollPosition()
        } else {
            adapter.upResumed(false)
            searchView?.clearFocus()
            adapter.onPause()
        }
        super.onPause()
    }

    private fun saveDiscoverScrollPosition() {
        val layoutManager = binding.rvDiscoverBooks.layoutManager as? LinearLayoutManager ?: return
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position == RecyclerView.NO_POSITION) return
        val view = layoutManager.findViewByPosition(position)
        viewModel.scrollPosition = position
        viewModel.scrollOffset = view?.top ?: 0
    }

    private fun restoreDiscoverScrollIfNeeded() {
        val position = viewModel.scrollPosition
        if (position == RecyclerView.NO_POSITION || position >= discoverBooks.size) return
        binding.rvDiscoverBooks.post {
            (binding.rvDiscoverBooks.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(position, viewModel.scrollOffset)
            viewModel.scrollPosition = RecyclerView.NO_POSITION
            viewModel.scrollOffset = 0
        }
    }

    private fun captureDiscoverScrollAnchor(): DiscoverScrollAnchor? {
        val layoutManager = binding.rvDiscoverBooks.layoutManager as? LinearLayoutManager ?: return null
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position == RecyclerView.NO_POSITION) {
            return null
        }
        return DiscoverScrollAnchor(
            position = position,
            offset = layoutManager.findViewByPosition(position)?.top ?: 0
        )
    }

    private fun restoreDiscoverScrollAnchor(anchor: DiscoverScrollAnchor?) {
        anchor ?: return
        binding.rvDiscoverBooks.post {
            (binding.rvDiscoverBooks.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(anchor.position, anchor.offset)
        }
    }

    override fun onDestroyView() {
        if (usingModernDiscovery) {
            saveDiscoverScrollPosition()
        }
        sourceMenuPopup?.dismiss()
        sourceMenuPopup = null
        tagFilterPopup?.dismiss()
        tagFilterPopup = null
        discoverGridColumnsPopup?.dismiss()
        discoverGridColumnsPopup = null
        discoverSourceFlowJob?.cancel()
        discoverSourceFlowJob = null
        discoverBookshelfFlowJob?.cancel()
        discoverBookshelfFlowJob = null
        discoverActionJob?.cancel()
        discoverActionJob = null
        discoverLoadJob?.cancel()
        discoverLoadJob = null
        discoverLoading = false
        clearDiscoverLoading()
        oldModeInitialized = false
        modernModeInitialized = false
        groupsMenu = null
        super.onDestroyView()
    }

    private fun upGroupsMenu() = groupsMenu?.transaction { subMenu ->
        subMenu.removeGroup(R.id.menu_group_text)
        groups.forEach {
            subMenu.add(R.id.menu_group_text, Menu.NONE, Menu.NONE, it)
        }
    }

    override val scope: CoroutineScope
        get() = viewLifecycleOwner.lifecycleScope

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        if (usingModernDiscovery) return
        if (item.groupId == R.id.menu_group_text) {
            searchView?.setQuery("group:${item.title}", true) ?: upExploreData("group:${item.title}")
        }
    }

    override fun scrollTo(pos: Int) {
        (binding.rvFind.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0)
    }

    override fun openExplore(sourceUrl: String, title: String, exploreUrl: String?) {
        if (exploreUrl.isNullOrBlank()) return
        recordDiscoverSourceUse(sourceUrl, increment = 2)
        startActivity<ExploreShowActivity> {
            putExtra("exploreName", title)
            putExtra("sourceUrl", sourceUrl)
            putExtra("exploreUrl", exploreUrl)
        }
    }

    override fun editSource(sourceUrl: String) {
        startActivity<BookSourceEditActivity> {
            putExtra("sourceUrl", sourceUrl)
        }
    }

    override fun toTop(source: BookSourcePart) {
        viewModel.topSource(source)
    }

    override fun recordSourceUse(source: BookSourcePart, increment: Int) {
        recordDiscoverSourceUse(source.bookSourceUrl, increment)
    }

    override fun deleteSource(source: BookSourcePart) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + source.bookSourceName)
            noButton()
            yesButton {
                viewModel.deleteSource(source)
            }
        }
    }

    override fun searchBook(bookSource: BookSourcePart) {
        SearchActivity.start(requireContext(), bookSource)
    }

    override fun isInBookshelf(book: SearchBook): Boolean {
        val key = if (book.author.isNotBlank()) "${book.name}-${book.author}" else book.name
        return discoverBookshelf.contains(key) || discoverBookshelf.contains(book.bookUrl)
    }

    override fun showBookInfo(book: SearchBook) {
        recordDiscoverSourceUse(
            book.origin.takeIf { it.isNotBlank() }
                ?: selectedDiscoverSource?.bookSourceUrl
                ?: selectedDiscoverSourcePart?.bookSourceUrl,
            increment = 3
        )
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(IO) {
                appDb.searchBookDao.insert(book)
            }
            val isVideo = withContext(IO) {
                SearchBookOpenHelper.isVideoResult(
                    book,
                    selectedDiscoverSourcePart?.bookSourceType ?: selectedDiscoverSource?.bookSourceType
                )
            }
            SearchBookOpenHelper.open(requireContext(), book, isVideo)
        }
    }

    fun compressExplore() {
        if (usingModernDiscovery) {
            if (binding.rvDiscoverBooks.canScrollVertically(-1)) {
                if (AppConfig.isEInkMode) {
                    binding.rvDiscoverBooks.scrollToPosition(0)
                } else {
                    binding.rvDiscoverBooks.smoothScrollToPosition(0)
                }
            }
            return
        }
        if (!adapter.compressExplore()) {
            if (AppConfig.isEInkMode) {
                binding.rvFind.scrollToPosition(0)
            } else {
                binding.rvFind.smoothScrollToPosition(0)
            }
        }
    }

}

private fun String.limitDiscoverText(max: Int): String {
    return if (length <= max) this else "${take(max.coerceAtLeast(2) - 1)}…"
}
