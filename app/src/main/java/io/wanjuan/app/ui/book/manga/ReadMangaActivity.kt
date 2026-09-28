package io.wanjuan.app.ui.book.manga

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import io.wanjuan.app.BuildConfig
import io.wanjuan.app.R
import io.wanjuan.app.base.VMBaseActivity
import io.wanjuan.app.constant.BookType
import io.wanjuan.app.constant.EventBus
import io.wanjuan.app.constant.PageAnim
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookChapter
import io.wanjuan.app.data.entities.BookProgress
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.databinding.ActivityMangaBinding
import io.wanjuan.app.databinding.ViewLoadMoreBinding
import io.wanjuan.app.help.book.isImage
import io.wanjuan.app.help.book.removeType
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.source.getSourceType
import io.wanjuan.app.lib.dialogs.alert
import io.wanjuan.app.model.ReadManga
import io.wanjuan.app.receiver.NetworkChangedListener
import io.wanjuan.app.ui.book.changesource.ChangeBookSourceDialog
import io.wanjuan.app.ui.book.info.BookInfoActivity
import io.wanjuan.app.ui.book.manga.config.MangaColorFilterConfig
import io.wanjuan.app.ui.book.manga.config.MangaColorFilterDialog
import io.wanjuan.app.ui.book.manga.config.MangaEpaperDialog
import io.wanjuan.app.ui.book.manga.config.MangaFooterConfig
import io.wanjuan.app.ui.book.manga.config.MangaFooterSettingDialog
import io.wanjuan.app.ui.book.manga.entities.BaseMangaPage
import io.wanjuan.app.ui.book.manga.entities.MangaPage
import io.wanjuan.app.ui.book.manga.recyclerview.MangaAdapter
import io.wanjuan.app.ui.book.manga.recyclerview.MangaLayoutManager
import io.wanjuan.app.ui.book.manga.recyclerview.ScrollTimer
import io.wanjuan.app.ui.book.read.MangaMenu
import io.wanjuan.app.ui.book.read.ReadBookActivity.Companion.RESULT_DELETED
import io.wanjuan.app.ui.book.read.applyMinimapChapterNavigationStyle
import io.wanjuan.app.ui.browser.WebViewActivity
import io.wanjuan.app.ui.widget.number.NumberPickerDialog
import io.wanjuan.app.ui.widget.recycler.LoadMoreView
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.NetworkUtils
import io.wanjuan.app.utils.StartActivityContract
import io.wanjuan.app.utils.canScroll
import io.wanjuan.app.utils.dpToPx
import io.wanjuan.app.utils.fastBinarySearch
import io.wanjuan.app.utils.findCenterViewPosition
import io.wanjuan.app.utils.fromJsonObject
import io.wanjuan.app.utils.getCompatColor
import io.wanjuan.app.utils.gone
import io.wanjuan.app.utils.isPad
import io.wanjuan.app.utils.observeEvent
import io.wanjuan.app.utils.showDialogFragment
import io.wanjuan.app.utils.startActivity
import io.wanjuan.app.utils.toastOnUi
import io.wanjuan.app.utils.toggleSystemBar
import io.wanjuan.app.utils.viewbindingdelegate.viewBinding
import io.wanjuan.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

class ReadMangaActivity : VMBaseActivity<ActivityMangaBinding, ReadMangaViewModel>(),
    ReadManga.Callback, ChangeBookSourceDialog.CallBack, MangaMenu.CallBack,
    MangaColorFilterDialog.Callback, ScrollTimer.ScrollCallback, MangaEpaperDialog.Callback {

    private val mLayoutManager by lazy {
        MangaLayoutManager(this)
    }
    private val mAdapter: MangaAdapter by lazy {
        MangaAdapter(this)
    }
    private val mDoubleLeftAdapter: MangaAdapter by lazy {
        MangaAdapter(this)
    }
    private val mDoubleRightAdapter: MangaAdapter by lazy {
        MangaAdapter(this)
    }
    private val mDoubleRightLayoutManager by lazy {
        MangaLayoutManager(this)
    }

    private val mPagerSnapHelper: PagerSnapHelper by lazy {
        PagerSnapHelper()
    }
    private var pendingMangaProgressMinimapLayoutSync = false
    private var committedMangaProgressMinimapRatio: Float? = null
    private var committedMangaProgressMinimapChapterIndex: Int? = null

    private lateinit var mMangaFooterConfig: MangaFooterConfig
    private val mLabelBuilder by lazy { StringBuilder() }

    private var mMenu: Menu? = null

    private val networkChangedListener by lazy {
        NetworkChangedListener(this)
    }

    private var justInitData: Boolean = false
    private var doubleColumnEnabled = false
    private var doubleColumnSyncing = false
    private var doubleColumnProgressSeeking = false
    private var doubleColumnProgressSeekPending = false
    private var doubleColumnPairSyncScheduled = false
    private var doubleColumnPairSource: RecyclerView? = null
    private var mangaPreloadListener: RecyclerView.OnScrollListener? = null
    private var syncDialog: AlertDialog? = null
    private val mScrollTimer by lazy {
        ScrollTimer(this, binding.recyclerView, lifecycleScope).apply {
            setSpeed(mangaAutoPageSpeed)
        }
    }
    private var enableAutoScrollPage = false
    private var enableAutoScroll = false
    private val mLinearInterpolator by lazy {
        LinearInterpolator()
    }

    private val loadMoreView by lazy {
        LoadMoreView(this).apply {
            setBackgroundColor(getCompatColor(R.color.book_ant_10))
            setLoadingColor(R.color.white)
            setLoadingTextColor(R.color.white)
        }
    }

    private val bookInfoActivity =
        registerForActivityResult(StartActivityContract(BookInfoActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                setResult(RESULT_DELETED)
                super.finish()
            } else {
                ReadManga.loadOrUpContent()
            }
        }
    override val binding by viewBinding(ActivityMangaBinding::inflate)
    override val viewModel by viewModels<ReadMangaViewModel>()
    private val loadingViewVisible get() = binding.flLoading.isVisible
    private val df by lazy {
        DecimalFormat("0.0%")
    }
    private val mangaPageAnim: Int?
        get() = ReadManga.book?.config?.mangaPageAnim
    private val mangaHorizontalScroll: Boolean
        get() = if (doubleColumnEnabled) {
            false
        } else when (mangaPageAnim) {
            PageAnim.coverPageAnim,
            PageAnim.linkedCoverPageAnim,
            PageAnim.slidePageAnim,
            PageAnim.simulationPageAnim,
            PageAnim.noAnim -> true

            PageAnim.scrollPageAnim -> false
            else -> ReadManga.book?.config?.mangaHorizontalScroll ?: AppConfig.enableMangaHorizontalScroll
        }
    private val mangaDisablePageAnim: Boolean
        get() = when (mangaPageAnim) {
            PageAnim.coverPageAnim,
            PageAnim.linkedCoverPageAnim,
            PageAnim.slidePageAnim,
            PageAnim.simulationPageAnim,
            PageAnim.scrollPageAnim -> false

            PageAnim.noAnim -> true
            else -> ReadManga.book?.config?.mangaDisablePageAnim ?: AppConfig.disableMangaPageAnim
        }
    private val mangaDisableHorizontalPageSnap: Boolean
        get() = when (mangaPageAnim) {
            PageAnim.coverPageAnim,
            PageAnim.linkedCoverPageAnim,
            PageAnim.slidePageAnim,
            PageAnim.simulationPageAnim -> false

            PageAnim.noAnim -> true
            else -> ReadManga.book?.config?.mangaDisableHorizontalPageSnap
                ?: AppConfig.disableHorizontalPageSnap
        }
    private val mangaDisableClickScroll: Boolean
        get() = ReadManga.book?.config?.mangaDisableClickScroll ?: AppConfig.disableClickScroll
    private val mangaDisableScale: Boolean
        get() = ReadManga.book?.config?.mangaDisableScale ?: AppConfig.disableMangaScale
    private val mangaAutoPageSpeed: Int
        get() = ReadManga.book?.config?.mangaAutoPageSpeed ?: AppConfig.mangaAutoPageSpeed

    override fun onCreate(savedInstanceState: Bundle?) {
        upLayoutInDisplayCutoutMode()
        super.onCreate(savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        ReadManga.register(this)
        upSystemUiVisibility(false)
        initRecyclerView()
        bindMangaProgressMinimap()
        binding.tvRetry.setOnClickListener {
            binding.llLoading.isVisible = true
            binding.llRetry.isGone = true
            ReadManga.loadOrUpContent()
        }
        binding.pbLoading.isVisible = !AppConfig.isEInkMode
        val loadMoreFooter: (ViewGroup) -> ViewBinding = {
            ViewLoadMoreBinding.bind(loadMoreView)
        }
        mAdapter.addFooterView(loadMoreFooter)
        // The left stream owns the chapter loader while the right stream stays a pure page flow.
        mDoubleLeftAdapter.addFooterView(loadMoreFooter)
        loadMoreView.setOnClickListener {
            if (!loadMoreView.isLoading && ReadManga.hasNextChapter) {
                loadMoreView.startLoad()
                ReadManga.loadOrUpContent()
            }
        }
        loadMoreView.gone()
        mMangaFooterConfig =
            GSON.fromJsonObject<MangaFooterConfig>(AppConfig.mangaFooterConfig).getOrNull()
                ?: MangaFooterConfig()
    }

    override fun observeLiveBus() {
        observeEvent<MangaFooterConfig>(EventBus.UP_MANGA_CONFIG) {
            mMangaFooterConfig = it
            val item = activeMangaAdapter().getItem(binding.recyclerView.findCenterViewPosition())
            upInfoBar(item)
        }
    }

    private fun initRecyclerView() {
        val mangaColorFilter =
            GSON.fromJsonObject<MangaColorFilterConfig>(AppConfig.mangaColorFilter).getOrNull()
                ?: MangaColorFilterConfig()
        listOf(mAdapter, mDoubleLeftAdapter, mDoubleRightAdapter).forEach { adapter ->
            adapter.setMangaImageColorFilter(mangaColorFilter)
            adapter.enableMangaEInk(AppConfig.enableMangaEInk, AppConfig.mangaEInkThreshold)
            adapter.enableGray(AppConfig.enableMangaGray)
            adapter.onPageImageReady = ::onMangaPageImageReady
        }
        setHorizontalScroll(mangaHorizontalScroll)
        binding.recyclerView.run {
            adapter = mAdapter
            itemAnimator = null
            layoutManager = mLayoutManager
            // Each image keeps its aspect ratio, so item heights are resolved after loading.
            setHasFixedSize(false)
            setDisableClickScroll(mangaDisableClickScroll)
            setDisableMangaScale(mangaDisableScale)
            setRecyclerViewPreloader(AppConfig.mangaPreDownloadNum)
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN &&
                    !binding.mangaProgressMinimap.isDraggingProgress()
                ) {
                    clearCommittedMangaProgressMinimapRatio()
                }
                false
            }
            setPreScrollListener { _, _, _, position ->
                val activeAdapter = activeMangaAdapter()
                if (activeAdapter.isNotEmpty()) {
                    val item = if (doubleColumnEnabled) {
                        currentVisibleMangaPage() ?: activeAdapter.getItem(position)
                    } else {
                        activeAdapter.getItem(position)
                    }
                    if (item is BaseMangaPage) {
                        if ((binding.mangaProgressMinimap.isDraggingProgress() ||
                                committedMangaProgressMinimapRatio() != null) &&
                            item.chapterIndex != ReadManga.durChapterIndex
                        ) {
                            clampMangaProgressMinimapDragWithinCurrentChapter(item)
                            return@setPreScrollListener
                        }
                        if (ReadManga.durChapterIndex < item.chapterIndex) {
                            ReadManga.moveToNextChapter()
                        } else if (ReadManga.durChapterIndex > item.chapterIndex) {
                            ReadManga.moveToPrevChapter()
                        } else if (ReadManga.durChapterPos != item.index) {
                            ReadManga.durChapterPos = item.index
                            ReadManga.curPageChanged()
                        }
                        if (item is MangaPage) {
                            binding.mangaProgressMinimap.clearPinnedProgressRatio()
                            updateMangaProgressMinimap()
                            binding.mangaMenu.upBookView()
                            upInfoBar(item)
                        }
                    }
                }
            }
        }
        binding.recyclerViewDoubleRight.run {
            adapter = mDoubleRightAdapter
            itemAnimator = null
            layoutManager = mDoubleRightLayoutManager
            setHasFixedSize(false)
            setDisableClickScroll(mangaDisableClickScroll)
            disableMangaScale = mangaDisableScale
        }
        binding.recyclerView.addOnScrollListener(doubleColumnLeftScrollListener)
        binding.recyclerViewDoubleRight.addOnScrollListener(doubleColumnRightScrollListener)
        fun setupWebtoonFrame(frame: io.wanjuan.app.ui.book.manga.recyclerview.WebtoonFrame) {
            frame.onTouchMiddle {
                if (!binding.mangaMenu.isVisible && !loadingViewVisible) {
                    binding.mangaMenu.runMenuIn()
                }
            }
            frame.onNextPage {
                scrollToNext()
            }
            frame.onPrevPage {
                scrollToPrev()
            }
        }
        setupWebtoonFrame(binding.webtoonFrame)
        setupWebtoonFrame(binding.webtoonFrameDoubleRight)
    }

    private fun activeMangaAdapter(): MangaAdapter {
        return if (doubleColumnEnabled) mDoubleLeftAdapter else mAdapter
    }

    private inline fun forEachMangaAdapter(block: MangaAdapter.() -> Unit) {
        mAdapter.block()
        mDoubleLeftAdapter.block()
        mDoubleRightAdapter.block()
    }

    private fun submitDoubleColumnLists(items: List<Any>, onCommitted: (() -> Unit)? = null) {
        if (onCommitted == null) {
            mDoubleLeftAdapter.submitList(items)
            mDoubleRightAdapter.submitList(items)
            return
        }
        var committedColumns = 0
        val columnCommitted = {
            committedColumns++
            if (committedColumns == 2) onCommitted()
        }
        mDoubleLeftAdapter.submitList(items, columnCommitted)
        mDoubleRightAdapter.submitList(items, columnCommitted)
    }

    private val doubleColumnLeftScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            syncDoubleColumnScroll(recyclerView, binding.recyclerViewDoubleRight, dy)
            syncDoubleColumnPair(recyclerView)
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                doubleColumnProgressSeeking = false
                doubleColumnPairSource = recyclerView
            } else if (newState == RecyclerView.SCROLL_STATE_IDLE &&
                doubleColumnPairSource === recyclerView
            ) {
                scheduleDoubleColumnPairSync(recyclerView, allowPositionJump = true)
            }
        }
    }

    private val doubleColumnRightScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            // A follower's deferred layout must not move the column driving the seek.
            if (dy == 0 && doubleColumnPairSource !== recyclerView) return
            syncDoubleColumnScroll(recyclerView, binding.recyclerView, dy)
            syncDoubleColumnPair(recyclerView)
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                doubleColumnProgressSeeking = false
                doubleColumnPairSource = recyclerView
            } else if (newState == RecyclerView.SCROLL_STATE_IDLE &&
                doubleColumnPairSource === recyclerView
            ) {
                scheduleDoubleColumnPairSync(recyclerView, allowPositionJump = true)
            }
        }
    }

    private companion object {
        private const val DOUBLE_COLUMN_ALIGNMENT_TOLERANCE_PX = 2
    }

    private data class DoubleColumnScrollAnchor(
        val page: MangaPage,
        val adapterPosition: Int,
        val offsetPx: Int,
    )

    private fun firstVisibleDoubleColumnPage(recyclerView: RecyclerView): DoubleColumnScrollAnchor? {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return null
        val firstPosition = layoutManager.findFirstVisibleItemPosition()
        val lastPosition = layoutManager.findLastVisibleItemPosition()
        if (firstPosition == RecyclerView.NO_POSITION || lastPosition == RecyclerView.NO_POSITION) {
            return null
        }
        val adapter = if (recyclerView === binding.recyclerViewDoubleRight) {
            mDoubleRightAdapter
        } else {
            mDoubleLeftAdapter
        }
        for (position in firstPosition..lastPosition) {
            val page = adapter.getItem(position) as? MangaPage ?: continue
            val view = layoutManager.findViewByPosition(position) ?: continue
            val offsetPx = layoutManager.getDecoratedTop(view) - recyclerView.paddingTop
            return DoubleColumnScrollAnchor(page, position, offsetPx)
        }
        return null
    }

    private fun adjacentMangaPage(page: MangaPage, direction: Int): MangaPage? {
        val items = mAdapter.getItems()
        val pagePosition = items.indexOfFirst { item ->
            item is MangaPage &&
                    item.chapterIndex == page.chapterIndex &&
                    item.index == page.index
        }
        if (pagePosition < 0) {
            return null
        }
        var position = pagePosition + direction
        while (position in items.indices) {
            (items[position] as? MangaPage)?.let { return it }
            position += direction
        }
        return null
    }

    private fun leftColumnPageFor(page: MangaPage): MangaPage? {
        if (adapterPositionForDoubleColumnPage(mDoubleLeftAdapter, page) >= 0) {
            return page
        }
        return adjacentMangaPage(page, -1)?.takeIf {
            adapterPositionForDoubleColumnPage(mDoubleLeftAdapter, it) >= 0
        }
    }

    private fun adapterPositionForDoubleColumnPage(
        adapter: MangaAdapter,
        page: MangaPage,
    ): Int {
        return adapter.getItems().indexOfFirst { item ->
            item is MangaPage &&
                    item.chapterIndex == page.chapterIndex &&
                    item.index == page.index
        }
    }

    private fun syncDoubleColumnScroll(source: RecyclerView, target: RecyclerView, dy: Int) {
        if (!doubleColumnEnabled || doubleColumnSyncing || doubleColumnProgressSeekPending || dy == 0) {
            return
        }
        doubleColumnSyncing = true
        try {
            // Both columns contain the complete continuous stream. Keep their physical scroll
            // delta equal while the pair anchor below maintains the one-viewport offset.
            target.scrollBy(0, dy)
        } finally {
            doubleColumnSyncing = false
        }
    }

    private fun syncDoubleColumnPair(source: RecyclerView, allowPositionJump: Boolean = false) {
        if (!doubleColumnEnabled || doubleColumnSyncing || doubleColumnProgressSeekPending || source.height <= 0) {
            return
        }
        var sourceAnchor = firstVisibleDoubleColumnPage(source) ?: return
        if (doubleColumnProgressSeeking && source === binding.recyclerView) {
            // Anchor at the seam so unloaded pages above it cannot shift the follower.
            val manager = source.layoutManager as? LinearLayoutManager ?: return
            val position = manager.findLastVisibleItemPosition()
            val page = mDoubleLeftAdapter.getItem(position) as? MangaPage
            val child = manager.findViewByPosition(position)
            if (page != null && child != null) {
                sourceAnchor = DoubleColumnScrollAnchor(
                    page, position, manager.getDecoratedTop(child) - source.paddingTop,
                )
            }
        }
        val target = if (source === binding.recyclerView) {
            binding.recyclerViewDoubleRight
        } else {
            binding.recyclerView
        }
        val targetAdapter = if (target === binding.recyclerViewDoubleRight) {
            mDoubleRightAdapter
        } else {
            mDoubleLeftAdapter
        }
        val targetPage = sourceAnchor.page
        val targetPosition = adapterPositionForDoubleColumnPage(targetAdapter, targetPage)
        if (targetPosition < 0) {
            return
        }
        val targetLayoutManager = target.layoutManager as? LinearLayoutManager ?: return
        val viewportHeight = (source.height - source.paddingTop - source.paddingBottom)
            .coerceAtLeast(0)
        val targetOffset = sourceAnchor.offsetPx + if (source === binding.recyclerView) {
            -viewportHeight
        } else {
            viewportHeight
        }
        val targetView = targetLayoutManager.findViewByPosition(targetPosition)
        if (targetView != null) {
            val currentTargetTop = targetLayoutManager.getDecoratedTop(targetView)
            val desiredTargetTop = target.paddingTop + targetOffset
            val correction = currentTargetTop - desiredTargetTop
            if (kotlin.math.abs(correction) <= DOUBLE_COLUMN_ALIGNMENT_TOLERANCE_PX) {
                return
            }
            doubleColumnSyncing = true
            try {
                // Correct only the residual distance so an idle transition does not re-layout the column.
                target.scrollBy(0, correction)
            } finally {
                doubleColumnSyncing = false
            }
        } else if (allowPositionJump) {
            doubleColumnSyncing = true
            try {
                targetLayoutManager.scrollToPositionWithOffset(
                    targetPosition,
                    targetOffset,
                )
            } finally {
                doubleColumnSyncing = false
            }
        }
    }

    private fun scheduleDoubleColumnPairSync(
        source: RecyclerView = binding.recyclerView,
        allowPositionJump: Boolean = false,
    ) {
        if (!doubleColumnEnabled || doubleColumnPairSyncScheduled) {
            return
        }
        doubleColumnPairSyncScheduled = true
        binding.recyclerView.post {
            doubleColumnPairSyncScheduled = false
            if (doubleColumnEnabled) {
                syncDoubleColumnPair(source, allowPositionJump)
            }
        }
    }

    private fun activeAdapterPositionForPage(
        page: MangaPage,
        pageIndex: Int = page.index,
        adapter: MangaAdapter = activeMangaAdapter(),
    ): Int {
        val targetPage = if (doubleColumnEnabled && adapter === mDoubleLeftAdapter) {
            leftColumnPageFor(page.copy(index = pageIndex))
        } else {
            page.copy(index = pageIndex)
        }
        return adapter.getItems().indexOfFirst { item ->
            item is MangaPage && item.chapterIndex == page.chapterIndex && item.index == pageIndex
        }.takeIf { it >= 0 }
            ?: targetPage?.let { adapterPositionForDoubleColumnPage(adapter, it) }
            ?: -1
    }

    private fun scrollToMangaPagePosition(fullAdapterPosition: Int) {
        val page = mAdapter.getItem(fullAdapterPosition) as? MangaPage
        val targetPosition = if (doubleColumnEnabled && page != null) {
            leftColumnPageFor(page)?.let {
                adapterPositionForDoubleColumnPage(mDoubleLeftAdapter, it)
            }?.takeIf { it >= 0 } ?: 0
        } else {
            fullAdapterPosition
        }
        mLayoutManager.scrollToPositionWithOffset(targetPosition, 0)
        if (doubleColumnEnabled && page != null) {
            scheduleDoubleColumnPairSync(allowPositionJump = true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        prepareForBookIntent(intent)
        viewModel.initData(intent) {
            applyBookMangaReadConfig()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        viewModel.initData(intent) {
            applyBookMangaReadConfig()
        }
        justInitData = true
    }

    private fun prepareForBookIntent(intent: Intent) {
        val newBookUrl = intent.getStringExtra("bookUrl") ?: return
        if (newBookUrl == ReadManga.book?.bookUrl) {
            return
        }
        binding.infobar.isGone = true
        binding.llLoading.isVisible = true
        binding.llRetry.isGone = true
        binding.flLoading.isVisible = true
        loadMoreView.gone()
        clearCommittedMangaProgressMinimapRatio()
        mAdapter.submitList(emptyList())
        submitDoubleColumnLists(emptyList())
    }

    override fun upContent() {
        lifecycleScope.launch {
            binding.mangaMenu.upBookView()
            val data = withContext(IO) { ReadManga.mangaContents }
            if (!ReadManga.isCurrentContent(data)) return@launch
            val pos = data.pos
            val list = data.items
            val curFinish = data.curFinish
            val nextFinish = data.nextFinish
            mAdapter.submitList(list) {
                if (!ReadManga.isCurrentContent(data)) return@submitList
                submitDoubleColumnLists(list) {
                    if (!ReadManga.isCurrentContent(data)) return@submitDoubleColumnLists
                    if (loadingViewVisible && curFinish) {
                        val currentPage = list.getOrNull(pos) ?: return@submitDoubleColumnLists
                        binding.infobar.isVisible = true
                        upInfoBar(currentPage)
                        scrollToMangaPagePosition(pos)
                        binding.flLoading.isGone = true
                        loadMoreView.visible()
                        updateMangaProgressMinimap()
                        binding.mangaMenu.upBookView()
                    }

                    if (curFinish) {
                        if (!ReadManga.hasNextChapter) {
                            loadMoreView.noMore("暂无章节了！")
                        } else if (nextFinish) {
                            loadMoreView.stopLoad()
                        } else {
                            loadMoreView.startLoad()
                        }
                    }
                }
            }
        }
    }

    private fun upInfoBar(page: Any?) {
        if (page !is MangaPage) {
            return
        }
        val chapterIndex = page.chapterIndex
        val chapterSize = page.chapterSize
        val chapterPos = page.index
        val imageCount = page.imageCount
        val chapterName = page.mChapterName
        mMangaFooterConfig.run {
            mLabelBuilder.clear()
            binding.infobar.isGone = hideFooter
            binding.infobar.textInfoAlignment = footerOrientation

            if (!hideChapterName) {
                mLabelBuilder.append(chapterName).append(" ")
            }

            if (!hidePageNumber) {
                if (!hidePageNumberLabel) {
                    mLabelBuilder.append(getString(R.string.manga_check_page_number))
                }
                mLabelBuilder.append("${chapterPos + 1}/${imageCount}").append(" ")
            }

            if (!hideChapter) {
                if (!hideChapterLabel) {
                    mLabelBuilder.append(getString(R.string.manga_check_chapter))
                }
                mLabelBuilder.append("${chapterIndex + 1}/${chapterSize}").append(" ")
            }

            if (!hideProgressRatio) {
                if (!hideProgressRatioLabel) {
                    mLabelBuilder.append(getString(R.string.manga_check_progress))
                }
                val percent = if (chapterSize == 0 || imageCount == 0 && chapterIndex == 0) {
                    "0.0%"
                } else if (imageCount == 0) {
                    df.format((chapterIndex + 1.0f) / chapterSize.toDouble())
                } else {
                    var percent =
                        df.format(
                            chapterIndex * 1.0f / chapterSize + 1.0f /
                                    chapterSize * (chapterPos + 1) / imageCount.toDouble()
                        )
                    if (percent == "100.0%" && (chapterIndex + 1 != chapterSize || chapterPos + 1 != imageCount)) {
                        percent = "99.9%"
                    }
                    percent
                }
                mLabelBuilder.append(percent)
            }
        }
        binding.infobar.update(
            if (mLabelBuilder.isEmpty()) "" else mLabelBuilder.toString()
        )
    }

    private fun bindMangaProgressMinimap() {
        binding.mangaProgressMinimap.onProgressChanging = ::previewMangaProgressMinimap
        binding.mangaProgressMinimap.onProgressChanged = ::commitMangaProgressMinimap
        binding.mangaProgressMinimap.onThumbnailReady = ::reloadMangaProgressPageIfCurrent
        binding.btnMangaMinimapPrevious.setOnClickListener {
            clearCommittedMangaProgressMinimapRatio()
            ReadManga.moveToPrevChapter(true)
        }
        binding.btnMangaMinimapCurrent.setOnClickListener {
            clearCommittedMangaProgressMinimapRatio()
            openMangaCatalog()
        }
        binding.btnMangaMinimapNext.setOnClickListener {
            clearCommittedMangaProgressMinimapRatio()
            ReadManga.moveToNextChapter(true)
        }
        binding.btnMangaDoubleColumn.setOnClickListener {
            setDoubleColumnLayout(!doubleColumnEnabled, save = true)
        }
    }

    private fun setupMangaMinimapAppearance() = binding.run {
        mangaProgressMinimap.refreshPalette()
        btnMangaMinimapPrevious.applyMinimapChapterNavigationStyle(tvMangaMinimapPrevious)
        btnMangaMinimapCurrent.applyMinimapChapterNavigationStyle(tvMangaMinimapCurrent)
        btnMangaMinimapNext.applyMinimapChapterNavigationStyle(tvMangaMinimapNext)
        btnMangaMinimapPrevious.isEnabled = ReadManga.durChapterIndex > 0
        btnMangaMinimapNext.isEnabled = ReadManga.durChapterIndex < ReadManga.chapterSize - 1
        btnMangaMinimapPrevious.alpha = if (btnMangaMinimapPrevious.isEnabled) 1f else .45f
        btnMangaMinimapNext.alpha = if (btnMangaMinimapNext.isEnabled) 1f else .45f
        btnMangaDoubleColumn.isVisible = isPad
        if (isPad) {
            btnMangaDoubleColumn.applyMinimapChapterNavigationStyle(tvMangaDoubleColumn)
            tvMangaDoubleColumn.setText(
                if (doubleColumnEnabled) R.string.manga_single_column
                else R.string.manga_double_column
            )
            btnMangaDoubleColumn.contentDescription = getString(
                if (doubleColumnEnabled) R.string.manga_single_column
                else R.string.manga_double_column
            )
        }
    }

    private fun openMangaCatalog() {
        binding.mangaMenu.showChapterList()
    }

    override fun skipToChapter(index: Int) {
        clearCommittedMangaProgressMinimapRatio()
        viewModel.openChapter(index, 0)
    }

    override fun onExpandedPanelVisibilityChanged() {
        updateMangaProgressMinimap()
    }

    private fun updateMangaProgressMinimap(show: Boolean = binding.mangaMenu.isVisible) {
        val imageUrls = currentMangaImageUrls()
        val pageCount = imageUrls.size
        val progressRatio = currentMangaScrollProgressRatio()
        val shouldShow = show && !binding.mangaMenu.isExpandedPanelVisible
        updateMangaMinimapCurrentChapterButton()
        if (show) {
            binding.mangaProgressMinimap.updatePages(
                ReadManga.durChapterIndex,
                imageUrls,
                ReadManga.book?.origin,
                ReadManga.durChapterPos,
                progressRatio,
            )
        } else {
            binding.mangaProgressMinimap.updateProgress(pageCount, ReadManga.durChapterPos, progressRatio)
        }
        binding.mangaProgressMinimapPanel.gone(!shouldShow || pageCount <= 1)
        if (!shouldShow || pageCount <= 1) {
            return
        }
        if (!mangaProgressMinimapMenuChromeReady()) {
            binding.mangaProgressMinimapPanel.gone()
            binding.mangaProgressMinimapPanel.post {
                if (binding.mangaMenu.isVisible) {
                    updateMangaProgressMinimap(show = true)
                }
            }
            return
        }
        val preservePanelPosition = binding.mangaProgressMinimap.shouldPreservePanelPosition()
        setupMangaMinimapAppearance()
        if (!preservePanelPosition && !constrainMangaProgressMinimapPanel()) {
            binding.mangaProgressMinimapPanel.gone()
        }
    }

    private fun scheduleMangaProgressMinimapStableSync() {
        if (pendingMangaProgressMinimapLayoutSync) {
            return
        }
        pendingMangaProgressMinimapLayoutSync = true
        binding.mangaProgressMinimapPanel.post {
            binding.mangaProgressMinimapPanel.post {
                pendingMangaProgressMinimapLayoutSync = false
                if (binding.mangaMenu.isVisible &&
                    binding.mangaProgressMinimapPanel.isVisible &&
                    !binding.mangaProgressMinimap.shouldPreservePanelPosition()
                ) {
                    constrainMangaProgressMinimapPanel()
                }
            }
        }
    }

    private fun mangaProgressMinimapMenuChromeReady(): Boolean {
        val topBar = binding.mangaMenu.findViewById<View>(R.id.title_bar_shell)
        return topBar?.isVisible == true && topBar.height > 0
    }

    private fun constrainMangaProgressMinimapPanel(): Boolean {
        val root = binding.root
        if (root.height <= 0) {
            root.post {
                updateMangaProgressMinimap(show = true)
            }
            return true
        }
        val gap = 8.dpToPx()
        val topLimit = minimapTopLimit(
            root = root,
            topBar = binding.mangaMenu.findViewById(R.id.title_bar_shell),
            fallbackTop = 96.dpToPx(),
            gap = gap
        )
        val bottomLimit = minimapBottomLimit(
            root = root,
            bottomBar = binding.mangaMenu.findViewById(R.id.bottom_menu),
            fallbackBottom = ViewCompat.getRootWindowInsets(root)
                ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                ?.bottom ?: 0,
            gap = gap
        )
        val availableHeight = (bottomLimit - topLimit).coerceAtLeast(0)
        val controlsTopMargin = (binding.mangaProgressMinimapControls.layoutParams as? ViewGroup.MarginLayoutParams)
            ?.topMargin ?: 0
        binding.mangaProgressMinimapControls.measure(
            View.MeasureSpec.makeMeasureSpec(binding.mangaProgressMinimapPanel.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val controlsHeight = binding.mangaProgressMinimapControls.measuredHeight
        val maxMinimapHeight = availableHeight - controlsTopMargin - controlsHeight
        val minimumMinimapHeight = 96.dpToPx()
        if (maxMinimapHeight < minimumMinimapHeight) {
            return false
        }
        val desiredHeight = resources.getDimensionPixelSize(R.dimen.manga_minimap_max_height)
        val minimapHeight = binding.mangaProgressMinimap.desiredHeightWithin(maxMinimapHeight.coerceAtMost(desiredHeight))
        val panelHeight = minimapHeight + controlsTopMargin + controlsHeight
        binding.mangaProgressMinimap.setMaxAvailableHeight(maxMinimapHeight)
        binding.mangaProgressMinimapHost.updateLayoutParams<ViewGroup.LayoutParams> {
            height = minimapHeight
        }
        binding.mangaProgressMinimap.updateLayoutParams<ViewGroup.LayoutParams> {
            height = minimapHeight
        }
        binding.mangaProgressMinimapPanel.updateLayoutParams<FrameLayout.LayoutParams> {
            gravity = Gravity.END or Gravity.TOP
            topMargin = centeredMinimapPanelTopMargin(topLimit, availableHeight, panelHeight)
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        return true
    }

    private fun centeredMinimapPanelTopMargin(topLimit: Int, availableHeight: Int, panelHeight: Int): Int {
        return topLimit + ((availableHeight - panelHeight).coerceAtLeast(0) / 2)
    }

    private fun minimapTopLimit(root: View, topBar: View?, fallbackTop: Int, gap: Int): Int {
        val topBarBottom = topBar
            ?.takeIf { it.isVisible && it.height > 0 }
            ?.let { viewTopInRoot(root, it) + it.height }
            ?: fallbackTop
        return (topBarBottom + gap).coerceIn(0, root.height)
    }

    private fun minimapBottomLimit(root: View, bottomBar: View?, fallbackBottom: Int, gap: Int): Int {
        val bottomBarTop = bottomBar
            ?.takeIf { it.isVisible && it.height > 0 }
            ?.let { viewTopInRoot(root, it) }
            ?: (root.height - fallbackBottom)
        return (bottomBarTop - gap).coerceIn(0, root.height)
    }

    private fun viewTopInRoot(root: View, view: View): Int {
        val rootLocation = IntArray(2)
        val viewLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        view.getLocationOnScreen(viewLocation)
        return viewLocation[1] - rootLocation[1]
    }

    private fun updateMangaMinimapCurrentChapterButton() {
        val chapterTitle = ReadManga.curMangaChapter?.chapter?.title.orEmpty()
        val chapterNumber = getString(R.string.reader_chapter_number, ReadManga.durChapterIndex + 1)
        binding.tvMangaMinimapCurrent.text = chapterNumber
        binding.btnMangaMinimapCurrent.contentDescription = if (chapterTitle.isBlank()) {
            chapterNumber
        } else {
            "$chapterNumber: $chapterTitle"
        }
    }

    private fun previewMangaProgressMinimap(ratio: Float) {
        scrollToMangaProgress(ratio, commit = false)
    }

    private fun commitMangaProgressMinimap(ratio: Float) {
        rememberCommittedMangaProgressMinimapRatio(ratio)
        scrollToMangaProgress(ratio, commit = true)
        reloadCommittedMangaProgressPage()
        updateMangaProgressMinimap(show = true)
        binding.mangaProgressMinimap.pinProgressRatio(ratio)
    }

    private fun rememberCommittedMangaProgressMinimapRatio(ratio: Float) {
        committedMangaProgressMinimapChapterIndex = ReadManga.durChapterIndex
        committedMangaProgressMinimapRatio = ratio.coerceIn(0f, 1f)
    }

    private fun committedMangaProgressMinimapRatio(): Float? {
        return committedMangaProgressMinimapRatio
            ?.takeIf { committedMangaProgressMinimapChapterIndex == ReadManga.durChapterIndex }
    }

    private fun clearCommittedMangaProgressMinimapRatio() {
        committedMangaProgressMinimapRatio = null
        committedMangaProgressMinimapChapterIndex = null
    }

    private fun scrollToMangaProgress(ratio: Float, commit: Boolean) {
        if (commit || doubleColumnEnabled) {
            doubleColumnPairSource = binding.recyclerView
            binding.recyclerView.stopScroll()
            binding.recyclerViewDoubleRight.stopScroll()
        }
        val target = scrollMangaBodyToProgressRatio(ratio)
        syncMangaProgressAfterScroll(commit, target?.pageIndex)
    }

    private data class MangaChapterScrollTarget(
        val pageIndex: Int,
        val offsetPx: Int
    )

    private data class VisibleMangaPage(
        val page: MangaPage,
        val view: View
    )

    private fun targetMangaChapterScrollOffsetForProgress(ratio: Float): MangaChapterScrollTarget? {
        val pageCount = currentMangaPageCount()
        if (pageCount <= 0) {
            return null
        }
        val scrollPageCount = progressPageCount(pageCount)
        val progress = ratio.coerceIn(0f, 1f)
        val scaledProgress = (progress * scrollPageCount).coerceIn(0f, scrollPageCount.toFloat())
        val scrollPageIndex = if (scaledProgress >= scrollPageCount) {
            scrollPageCount - 1
        } else {
            floor(scaledProgress).toInt().coerceIn(0, scrollPageCount - 1)
        }
        val pageIndex = scrollPageIndex
        val pageOffsetRatio = (scaledProgress - scrollPageIndex).coerceIn(0f, 1f)
        val pageScrollSize = mangaPageScrollSizeForPage(pageIndex)
        val offsetPx = (pageScrollSize * pageOffsetRatio).roundToInt()
            .coerceIn(0, pageScrollSize)
        return MangaChapterScrollTarget(pageIndex, offsetPx)
    }

    private fun reloadCommittedMangaProgressPage() {
        val targetChapterIndex = ReadManga.durChapterIndex
        val targetPage = ReadManga.durChapterPos
        binding.recyclerView.post {
            if (ReadManga.durChapterIndex != targetChapterIndex || ReadManga.durChapterPos != targetPage) {
                return@post
            }
            reloadMangaProgressPage(targetPage)
        }
    }

    private fun reloadMangaProgressPageIfCurrent(pageIndex: Int, imageUrl: String) {
        val targetChapterIndex = ReadManga.durChapterIndex
        binding.recyclerView.post {
            if (ReadManga.durChapterIndex != targetChapterIndex || ReadManga.durChapterPos != pageIndex) {
                return@post
            }
            if (currentMangaImageUrlAt(pageIndex) != imageUrl) {
                return@post
            }
            reloadMangaProgressPage(pageIndex)
        }
    }

    private fun onMangaPageImageReady(page: MangaPage) {
        val key = page.thumbnailKeyIfCurrent(
            ReadManga.durChapterIndex,
            currentMangaImageUrls(),
        ) ?: return
        binding.mangaProgressMinimap.markBodyImageReady(key)
        if (doubleColumnEnabled) {
            // Image dimensions can settle at different times in the two streams.
            if (doubleColumnProgressSeeking) {
                scheduleDoubleColumnProgressSeekSync()
            } else {
                scheduleDoubleColumnPairSync(doubleColumnPairSource ?: binding.recyclerView)
            }
        }
    }

    private fun reloadMangaProgressPage(pageIndex: Int) {
        val itemPos = adapterPositionForMangaPage(pageIndex)
        val adapter = activeMangaAdapter()
        if (itemPos > -1 && adapter.getItem(itemPos) is MangaPage) {
            val holder = binding.recyclerView.findViewHolderForAdapterPosition(itemPos) as? MangaAdapter.PageViewHolder
            if (holder?.binding?.flProgress?.isVisible == false) {
                return
            }
            adapter.notifyItemChanged(itemPos)
        }
    }

    private fun scrollMangaBodyToProgressRatio(ratio: Float): MangaChapterScrollTarget? {
        scrollMangaBodyToChapterBoundaryIfNeeded(ratio)?.let { return it }
        val target = targetMangaChapterScrollOffsetForProgress(ratio) ?: return null
        scrollToCurrentMangaChapterOffset(target.pageIndex, target.offsetPx)
        return target
    }

    private fun scrollMangaBodyToChapterBoundaryIfNeeded(ratio: Float): MangaChapterScrollTarget? {
        val progress = ratio.coerceIn(0f, 1f)
        return when {
            progress <= 0f -> {
                scrollToCurrentMangaChapterOffset(0, 0)
                MangaChapterScrollTarget(0, 0)
            }

            progress >= 1f -> {
                val lastPage = (currentMangaPageCount() - 1).coerceAtLeast(0)
                if (scrollToMangaChapterEnd()) {
                    MangaChapterScrollTarget(lastPage, mangaPageScrollSizeForPage(lastPage))
                } else {
                    null
                }
            }

            else -> null
        }
    }

    private fun scrollToMangaChapterEnd(): Boolean {
        val endBoundaryPosition = adapterPositionAfterCurrentMangaChapter(ReadManga.durChapterIndex)
        if (endBoundaryPosition != null) {
            val activePosition = activeAdapterPositionForFullItem(endBoundaryPosition)
            if (activePosition >= 0) {
                scrollMangaProgressToPositionWithOffset(activePosition, currentMangaScrollExtent())
            }
            return true
        }
        val lastChapterPosition = adapterPositionForLastCurrentMangaChapterItem(ReadManga.durChapterIndex)
        if (lastChapterPosition <= -1) {
            return false
        }
        val activePosition = activeAdapterPositionForFullItem(lastChapterPosition)
        if (activePosition >= 0) {
            scrollMangaProgressToPositionWithOffset(activePosition, 0)
        }
        return true
    }

    private fun currentMangaScrollProgressRatio(): Float? {
        committedMangaProgressMinimapRatio()?.let { return it }
        val pageCount = currentMangaPageCount()
        if (pageCount <= 0) {
            return null
        }
        currentMangaChapterScrollProgressRatio()?.let { return it }
        return pageProgressRatio(progressPageCount(pageCount), progressPageIndex(ReadManga.durChapterPos))
    }

    private fun currentMangaChapterScrollProgressRatio(): Float? {
        val pageCount = currentMangaPageCount()
        if (pageCount <= 0) {
            return null
        }
        val visiblePage = firstVisibleCurrentMangaPage() ?: return null
        val pageScrollSize = mangaPageScrollSize(visiblePage.view)
        val pageOffset = (-mangaPageScrollStart(visiblePage.view)).coerceIn(0, pageScrollSize)
        val visiblePageCount = progressPageCount(pageCount)
        val visiblePageIndex = progressPageIndex(visiblePage.page.index)
        return ((visiblePageIndex + pageOffset / pageScrollSize.toFloat()) / visiblePageCount)
            .coerceIn(0f, 1f)
    }

    private fun pageProgressRatio(pageCount: Int, progress: Int): Float {
        return if (pageCount <= 0) {
            0f
        } else {
            progress.coerceIn(0, pageCount - 1) / pageCount.toFloat()
        }
    }

    private fun progressPageCount(pageCount: Int): Int {
        return pageCount
    }

    private fun progressPageIndex(pageIndex: Int): Int {
        return pageIndex
    }

    private fun activeAdapterPositionForFullItem(fullPosition: Int): Int {
        val item = mAdapter.getItem(fullPosition) ?: return -1
        return activeMangaAdapter().getItems().indexOfFirst { candidate ->
            when {
                item is MangaPage && candidate is MangaPage ->
                    item.chapterIndex == candidate.chapterIndex && item.index == candidate.index

                else -> item == candidate
            }
        }
    }

    private fun adapterPositionAfterCurrentMangaChapter(chapterIndex: Int): Int? {
        return mAdapter.getItems().indexOfFirst { item ->
            (item as? BaseMangaPage)?.chapterIndex?.let { it > chapterIndex } == true
        }.takeIf { it > -1 }
    }

    private fun adapterPositionForLastCurrentMangaChapterItem(chapterIndex: Int): Int {
        return mAdapter.getItems().indexOfLast { item ->
            (item as? BaseMangaPage)?.chapterIndex == chapterIndex
        }
    }

    private fun currentMangaScrollExtent(): Int {
        return if (mangaHorizontalScroll) {
            binding.recyclerView.computeHorizontalScrollExtent()
        } else {
            binding.recyclerView.computeVerticalScrollExtent()
        }
    }

    private fun scrollToCurrentMangaChapterOffset(index: Int, offsetPx: Int) {
        val pageCount = currentMangaPageCount()
        if (pageCount <= 0) {
            return
        }
        val targetIndex = index.coerceIn(0, pageCount - 1)
        val itemPos = adapterPositionForMangaPage(targetIndex)
        if (itemPos <= -1) {
            return
        }
        val pageScrollSize = mangaPageScrollSizeForAdapterPosition(itemPos)
        val targetOffset = offsetPx.coerceIn(0, pageScrollSize)
        scrollMangaProgressToPositionWithOffset(itemPos, -targetOffset)
    }

    private fun scrollMangaProgressToPositionWithOffset(position: Int, offsetPx: Int) {
        if (doubleColumnEnabled) {
            doubleColumnPairSource = binding.recyclerView
            doubleColumnProgressSeeking = true
            scheduleDoubleColumnProgressSeekSync()
            val left = binding.recyclerView
            val viewportHeight = left.height - left.paddingTop - left.paddingBottom
            // Position jumps report dy = 0; submit both offsets for the same layout pass.
            mDoubleRightLayoutManager.scrollToPositionWithOffset(position, offsetPx - viewportHeight)
        }
        mLayoutManager.scrollToPositionWithOffset(position, offsetPx)
    }

    private fun scheduleDoubleColumnProgressSeekSync() {
        if (doubleColumnProgressSeekPending) return
        doubleColumnProgressSeekPending = true
        binding.mangaColumnsContainer.doOnPreDraw {
            doubleColumnProgressSeekPending = false
            if (doubleColumnProgressSeeking) {
                syncDoubleColumnPair(binding.recyclerView, allowPositionJump = true)
            }
        }
    }

    private fun clampMangaProgressMinimapDragWithinCurrentChapter(
        item: BaseMangaPage
    ) {
        binding.recyclerView.post {
            if (binding.mangaProgressMinimap.isDraggingProgress() ||
                committedMangaProgressMinimapRatio() != null
            ) {
                if (item.chapterIndex > ReadManga.durChapterIndex && scrollToMangaChapterEnd()) {
                    return@post
                }
                scrollToCurrentMangaChapterOffset(0, 0)
            }
        }
    }

    private fun syncMangaProgressAfterScroll(commit: Boolean, targetPage: Int? = null) {
        val currentPage = targetPage?.let(::currentMangaPageAt)
            ?: currentVisibleMangaPage()
            ?: return
        upInfoBar(currentPage)
        ReadManga.durChapterPos = currentPage.index
        updateMangaProgressMinimap()
        if (commit) {
            ReadManga.curPageChanged()
            ReadManga.saveRead(true)
        }
    }

    private fun currentVisibleMangaPage(): MangaPage? {
        if (doubleColumnEnabled) {
            firstVisibleCurrentMangaPage()?.page?.let { return it }
        }
        val centerPosition = binding.recyclerView.findCenterViewPosition()
        val activeAdapter = activeMangaAdapter()
        (activeAdapter.getItem(centerPosition) as? MangaPage)
            ?.takeIf { it.chapterIndex == ReadManga.durChapterIndex }
            ?.let { return it }

        val firstVisiblePosition = mLayoutManager.findFirstVisibleItemPosition()
        val lastVisiblePosition = mLayoutManager.findLastVisibleItemPosition()
        if (firstVisiblePosition == RecyclerView.NO_POSITION ||
            lastVisiblePosition == RecyclerView.NO_POSITION ||
            firstVisiblePosition > lastVisiblePosition
        ) {
            return null
        }
        return (firstVisiblePosition..lastVisiblePosition).asSequence()
            .mapNotNull { activeAdapter.getItem(it) as? MangaPage }
            .firstOrNull { it.chapterIndex == ReadManga.durChapterIndex }
    }

    private fun firstVisibleCurrentMangaPage(): VisibleMangaPage? {
        for (childIndex in 0 until mLayoutManager.childCount) {
            val child = mLayoutManager.getChildAt(childIndex) ?: continue
            val adapterPosition = binding.recyclerView.getChildAdapterPosition(child)
            if (adapterPosition == RecyclerView.NO_POSITION) {
                continue
            }
            val page = (activeMangaAdapter().getItem(adapterPosition) as? MangaPage)
                ?.takeIf { it.chapterIndex == ReadManga.durChapterIndex }
                ?: continue
            return VisibleMangaPage(page, child)
        }
        return null
    }

    private fun mangaPageScrollSizeForPage(index: Int): Int {
        val itemPos = adapterPositionForMangaPage(index)
        return mangaPageScrollSizeForAdapterPosition(itemPos)
    }

    private fun mangaPageScrollSizeForAdapterPosition(adapterPosition: Int): Int {
        if (adapterPosition > -1) {
            mLayoutManager.findViewByPosition(adapterPosition)
                ?.let { return mangaPageScrollSize(it) }
        }
        firstVisibleCurrentMangaPage()
            ?.let { return mangaPageScrollSize(it.view) }
        return currentMangaScrollExtent().coerceAtLeast(1)
    }

    private fun mangaPageScrollSize(view: View): Int {
        val size = if (mangaHorizontalScroll) {
            mLayoutManager.getDecoratedMeasuredWidth(view)
        } else {
            mLayoutManager.getDecoratedMeasuredHeight(view)
        }
        return size.coerceAtLeast(1)
    }

    private fun mangaPageScrollStart(view: View): Int {
        return if (mangaHorizontalScroll) {
            mLayoutManager.getDecoratedLeft(view) - binding.recyclerView.paddingStart
        } else {
            mLayoutManager.getDecoratedTop(view) - binding.recyclerView.paddingTop
        }
    }

    private fun adapterPositionForMangaPage(index: Int): Int {
        val durChapterIndex = ReadManga.durChapterIndex
        if (doubleColumnEnabled) {
            val page = currentMangaPageAt(index) ?: return -1
            val leftPage = leftColumnPageFor(page) ?: return -1
            return activeMangaAdapter().getItems().indexOfFirst { item ->
                item is MangaPage &&
                        item.chapterIndex == leftPage.chapterIndex &&
                        item.index == leftPage.index
            }
        }
        return mAdapter.getItems().fastBinarySearch {
            val page = it as? BaseMangaPage ?: error("unknown item type")
            val chapterDelta = page.chapterIndex - durChapterIndex
            if (chapterDelta != 0) {
                chapterDelta
            } else {
                page.index - index
            }
        }
    }

    private fun currentMangaPageCount(): Int {
        return ReadManga.curMangaChapter?.imageCount ?: 0
    }

    private fun currentMangaImageUrls(): List<String> {
        return ReadManga.curMangaChapter?.pages?.filterIsInstance<MangaPage>()
            ?.map { it.mImageUrl }
            .orEmpty()
    }

    private fun currentMangaPageAt(pageIndex: Int): MangaPage? {
        return ReadManga.curMangaChapter?.pages?.filterIsInstance<MangaPage>()
            ?.getOrNull(pageIndex)
    }

    private fun currentMangaImageUrlAt(pageIndex: Int): String? {
        return currentMangaPageAt(pageIndex)?.mImageUrl
    }

    override fun onResume() {
        super.onResume()
        binding.mangaProgressMinimap.resumeThumbnailLoading()
        networkChangedListener.register()
        networkChangedListener.onNetworkChanged = {
            // 当网络是可用状态且无需初始化时同步进度（初始化中已有同步进度逻辑）
            if (AppConfig.webDavReadingSyncEnhancement && NetworkUtils.isAvailable() && !justInitData && ReadManga.inBookshelf) {
                ReadManga.syncProgress()
            }
        }
        if (enableAutoScrollPage) {
            mScrollTimer.isEnabledPage = true
        }
        if (enableAutoScroll) {
            mScrollTimer.isEnabled = true
        }
    }

    override fun onPause() {
        binding.mangaProgressMinimap.pauseThumbnailLoading()
        super.onPause()
        if (ReadManga.inBookshelf) {
            ReadManga.saveRead()
            if (!BuildConfig.DEBUG) {
                if (AppConfig.webDavReadingSyncEnhancement) {
                    ReadManga.syncProgress()
                } else {
                    ReadManga.uploadProgress()
                }
            }
        }
        if (!BuildConfig.DEBUG) {
        }
        ReadManga.cancelPreDownloadTask()
        networkChangedListener.unRegister()
        mScrollTimer.isEnabledPage = false
        mScrollTimer.isEnabled = false
    }

    override fun loadFail(msg: String, retry: Boolean) {
        lifecycleScope.launch {
            if (loadingViewVisible) {
                binding.llLoading.isGone = true
                binding.llRetry.isVisible = true
                binding.tvRetry.isVisible = retry
                binding.tvMsg.text = msg
            } else {
                loadMoreView.error(null, "加载失败，点击重试")
            }
        }
    }

    override fun onDestroy() {
        ReadManga.unregister(this)
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Glide.get(this).clearMemory()
    }

    override fun sureNewProgress(progress: BookProgress) {
        syncDialog?.dismiss()
        syncDialog = alert(R.string.get_book_progress) {
            setMessage(R.string.cloud_progress_exceeds_current)
            okButton {
                ReadManga.setProgress(progress)
            }
            noButton()
        }
    }

    override fun showLoading() {
        lifecycleScope.launch {
            binding.flLoading.isVisible = true
        }
    }

    override fun startLoad() {
        lifecycleScope.launch {
            loadMoreView.startLoad()
        }
    }

    override fun scrollBy(distance: Int) {
        if (!binding.recyclerView.canScroll(1)) {
            return
        }
        val time = ceil(16f / distance * 10000).toInt()
        binding.recyclerView.smoothScrollBy(10000, 10000, mLinearInterpolator, time)
    }

    override fun scrollPage() {
        scrollToNext()
    }

    override val oldBook: Book?
        get() = ReadManga.book

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        if (book.isImage) {
            binding.flLoading.isVisible = true
            viewModel.changeTo(book, toc)
        } else {
            toastOnUi("所选择的源不是漫画源")
        }
    }

    override fun updateColorFilter(config: MangaColorFilterConfig) {
        forEachMangaAdapter { setMangaImageColorFilter(config) }
        updateWindowBrightness(config.l)
    }

    @SuppressLint("StringFormatMatches")
    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_manga, menu)
        upMenu(menu)
        binding.mangaMenu.refreshMenuColorFilter()
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        upMenu(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    /**
     * 菜单
     */
    @SuppressLint("StringFormatMatches", "NotifyDataSetChanged")
    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_change_source -> {
                binding.mangaMenu.runMenuOut()
                ReadManga.book?.let {
                    showDialogFragment(ChangeBookSourceDialog(it.name, it.author))
                }
            }

            R.id.menu_catalog -> {
                openMangaCatalog()
            }

            R.id.menu_refresh -> {
                binding.flLoading.isVisible = true
                ReadManga.book?.let {
                    viewModel.refreshContentDur(it)
                }
            }

            R.id.menu_login -> {
                showLogin()
            }

            R.id.menu_pre_manga_number -> {
                showNumberPickerDialog(
                    0,
                    getString(R.string.pre_download),
                    AppConfig.mangaPreDownloadNum
                ) {
                    AppConfig.mangaPreDownloadNum = it
                    item.title = getString(R.string.pre_download_m, it)
                    setRecyclerViewPreloader(it)
                }
            }

            R.id.menu_disable_manga_scale -> {
                item.isChecked = !item.isChecked
                updateBookMangaReadConfig {
                    mangaPageAnim = null
                    mangaDisableScale = item.isChecked
                }
                setDisableMangaScale(item.isChecked)
            }

            R.id.menu_disable_click_scroll -> {
                item.isChecked = !item.isChecked
                updateBookMangaReadConfig {
                    mangaPageAnim = null
                    mangaDisableClickScroll = item.isChecked
                }
                setDisableClickScroll(item.isChecked)
            }

            R.id.menu_enable_auto_page -> {
                if (enableAutoScrollPage) {
                    setAutoPageEnabled(false)
                } else {
                    showNumberPickerDialog(
                        1, getString(R.string.setting_manga_auto_page_speed),
                        mangaAutoPageSpeed
                    ) {
                        updateBookMangaReadConfig {
                            mangaAutoPageSpeed = it
                        }
                        mScrollTimer.setSpeed(it)
                        setAutoPageEnabled(true)
                        binding.mangaMenu.runMenuOut()
                    }
                }
            }

            R.id.menu_manga_auto_page_speed -> {
                showNumberPickerDialog(
                    1, getString(R.string.setting_manga_auto_page_speed),
                    mangaAutoPageSpeed
                ) {
                    updateBookMangaReadConfig {
                        mangaAutoPageSpeed = it
                    }
                    item.title = getString(R.string.manga_auto_page_speed, it)
                    mScrollTimer.setSpeed(it)
                    if (enableAutoScrollPage) {
                        mScrollTimer.isEnabledPage = true
                    }
                    binding.mangaMenu.runMenuOut()
                }
            }

            R.id.menu_manga_footer_config -> {
                showDialogFragment(MangaFooterSettingDialog())
            }

            R.id.menu_enable_horizontal_scroll -> {
                item.isChecked = !item.isChecked
                if (doubleColumnEnabled) {
                    setDoubleColumnLayout(false, save = true)
                }
                updateBookMangaReadConfig {
                    mangaPageAnim = null
                    mangaHorizontalScroll = item.isChecked
                }
                mMenu?.findItem(R.id.menu_disable_horizontal_page_snap)?.isVisible =
                    item.isChecked && !mangaDisablePageAnim
                setHorizontalScroll(item.isChecked)
                activeMangaAdapter().notifyDataSetChanged()
            }

            R.id.menu_manga_color_filter -> {
                binding.mangaMenu.runMenuOut()
                showDialogFragment(MangaColorFilterDialog())
            }

            R.id.menu_enable_auto_scroll -> {
                if (enableAutoScroll) {
                    setAutoScrollEnabled(false)
                } else {
                    showNumberPickerDialog(
                        1, getString(R.string.setting_manga_auto_page_speed),
                        mangaAutoPageSpeed
                    ) {
                        updateBookMangaReadConfig {
                            mangaAutoPageSpeed = it
                        }
                        mScrollTimer.setSpeed(it)
                        setAutoScrollEnabled(true)
                        binding.mangaMenu.runMenuOut()
                    }
                }
            }

            R.id.menu_hide_manga_title -> {
                item.isChecked = !item.isChecked
                AppConfig.hideMangaTitle = item.isChecked
                ReadManga.loadContent()
            }

            R.id.menu_epaper_manga -> {
                if (AppConfig.enableMangaEInk) {
                    AppConfig.enableMangaEInk = false
                    forEachMangaAdapter {
                        enableMangaEInk(false, AppConfig.mangaEInkThreshold)
                    }
                    mMenu?.let { upMenu(it) }
                } else {
                    showDialogFragment(MangaEpaperDialog(enableOnConfirm = true))
                }
            }

            R.id.menu_epaper_manga_setting -> {
                showDialogFragment(MangaEpaperDialog())
            }

            R.id.menu_disable_horizontal_page_snap -> {
                item.isChecked = !item.isChecked
                updateBookMangaReadConfig {
                    mangaPageAnim = null
                    mangaDisableHorizontalPageSnap = item.isChecked
                }
                if (item.isChecked) {
                    mPagerSnapHelper.attachToRecyclerView(null)
                } else {
                    mPagerSnapHelper.attachToRecyclerView(binding.recyclerView)
                }
            }

            R.id.menu_gray_manga -> {
                item.isChecked = !item.isChecked
                AppConfig.enableMangaGray = item.isChecked
                mMenu?.findItem(R.id.menu_epaper_manga)?.isChecked = false
                AppConfig.enableMangaEInk = false
                mMenu?.findItem(R.id.menu_epaper_manga_setting)?.isVisible = false
                forEachMangaAdapter { enableGray(item.isChecked) }
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun openBookInfoActivity() {
        ReadManga.book?.let {
            bookInfoActivity.launch {
                putExtra("name", it.name)
                putExtra("author", it.author)
            }
        }
    }

    override fun showLogin() {
        val url = ReadManga.curMangaChapter?.chapter?.getAbsoluteURL()
            ?.takeIf { it.isNotBlank() }
            ?: return
        startActivity<WebViewActivity> {
            val bookSource = ReadManga.bookSource
            putExtra("title", ReadManga.curMangaChapter?.chapter?.title ?: ReadManga.book?.name)
            putExtra("url", url)
            putExtra("sourceOrigin", bookSource?.bookSourceUrl)
            putExtra("sourceName", bookSource?.bookSourceName)
            putExtra("sourceType", bookSource?.getSourceType())
        }
    }

    override fun upSystemUiVisibility(menuIsVisible: Boolean) {
        toggleSystemBar(menuIsVisible)
        if (!menuIsVisible) {
            binding.mangaProgressMinimap.clearPinnedProgressRatio()
        }
        updateMangaProgressMinimap(menuIsVisible)
        if (menuIsVisible) {
            scheduleMangaProgressMinimapStableSync()
        }
        if (enableAutoScroll) {
            mScrollTimer.isEnabled = !menuIsVisible
        }
        if (enableAutoScrollPage) {
            mScrollTimer.isEnabledPage = !menuIsVisible
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action
        val isDown = action == 0

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (isDown && !binding.mangaMenu.canShowMenu) {
                binding.mangaMenu.runMenuIn()
                return true
            }
            if (!isDown && !binding.mangaMenu.canShowMenu) {
                binding.mangaMenu.canShowMenu = true
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setRecyclerViewPreloader(maxPreload: Int) {
        mangaPreloadListener?.let { binding.recyclerView.removeOnScrollListener(it) }
        if (maxPreload <= 0) {
            mangaPreloadListener = null
            return
        }
        mangaPreloadListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager ?: return
                val (first, last) = when (layoutManager) {
                    is LinearLayoutManager -> {
                        layoutManager.findFirstVisibleItemPosition() to
                                layoutManager.findLastVisibleItemPosition()
                    }

                    is StaggeredGridLayoutManager -> {
                        val firstVisible = layoutManager.findFirstVisibleItemPositions(null)
                            .filter { it != RecyclerView.NO_POSITION }.minOrNull()
                            ?: return
                        val lastVisible = layoutManager.findLastVisibleItemPositions(null)
                            .filter { it != RecyclerView.NO_POSITION }.maxOrNull()
                            ?: return
                        firstVisible to lastVisible
                    }

                    else -> return
                }
                if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) {
                    return
                }
                val forward = if (layoutManager.canScrollVertically()) dy >= 0 else dx >= 0
                val positions = if (forward) {
                    (last + 1)..(last + maxPreload)
                } else {
                    (first - maxPreload until first).reversed()
                }
                positions.forEach { position ->
                    if (position < 0) return@forEach
                    activeMangaAdapter().getPreloadItems(position).forEach { item ->
                        activeMangaAdapter().getPreloadRequestBuilder(item)?.preload()
                    }
                }
            }
        }.also(binding.recyclerView::addOnScrollListener)
    }

    private fun setHorizontalScroll(enable: Boolean) {
        mAdapter.isHorizontal = enable
        if (enable) {
            if (!enableAutoScroll) {
                if (mangaDisableHorizontalPageSnap || mangaDisablePageAnim) {
                    mPagerSnapHelper.attachToRecyclerView(null)
                } else {
                    mPagerSnapHelper.attachToRecyclerView(binding.recyclerView)
                }
            }
            mLayoutManager.orientation = LinearLayoutManager.HORIZONTAL
        } else {
            mPagerSnapHelper.attachToRecyclerView(null)
            mLayoutManager.orientation = LinearLayoutManager.VERTICAL
        }
    }

    private fun setDoubleColumnContainer(enabled: Boolean) {
        val leftParams = binding.webtoonFrame.layoutParams as LinearLayout.LayoutParams
        val rightParams = binding.webtoonFrameDoubleRight.layoutParams as LinearLayout.LayoutParams
        if (enabled) {
            leftParams.width = 0
            leftParams.weight = 1f
            rightParams.width = 0
            rightParams.weight = 1f
            binding.webtoonFrameDoubleRight.isVisible = true
        } else {
            leftParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            leftParams.weight = 0f
            rightParams.width = 0
            rightParams.weight = 0f
            binding.webtoonFrameDoubleRight.isGone = true
        }
        binding.webtoonFrame.layoutParams = leftParams
        binding.webtoonFrameDoubleRight.layoutParams = rightParams
    }

    private fun setDoubleColumnLayout(enabled: Boolean, save: Boolean) {
        val shouldEnable = enabled && isPad
        val currentPage = currentVisibleMangaPage()
        doubleColumnEnabled = shouldEnable
        if (shouldEnable) {
            mDoubleLeftAdapter.isHorizontal = false
            mDoubleRightAdapter.isHorizontal = false
            mDoubleLeftAdapter.isDoubleColumn = true
            mDoubleRightAdapter.isDoubleColumn = true
            binding.recyclerView.adapter = mDoubleLeftAdapter
            mPagerSnapHelper.attachToRecyclerView(null)
            mLayoutManager.orientation = LinearLayoutManager.VERTICAL
            mDoubleRightLayoutManager.orientation = LinearLayoutManager.VERTICAL
            setDoubleColumnContainer(true)
        } else {
            mDoubleLeftAdapter.isDoubleColumn = false
            mDoubleRightAdapter.isDoubleColumn = false
            mAdapter.isDoubleColumn = false
            binding.recyclerView.adapter = mAdapter
            setDoubleColumnContainer(false)
            setHorizontalScroll(mangaHorizontalScroll)
        }
        activeMangaAdapter().notifyDataSetChanged()
        if (shouldEnable) {
            mDoubleRightAdapter.notifyDataSetChanged()
        } else {
            mAdapter.notifyDataSetChanged()
        }
        if (save) {
            ReadManga.book?.let { book ->
                book.config.mangaDoubleColumn = shouldEnable
                lifecycleScope.launch(IO) { book.save() }
            }
        }
        if (binding.mangaMenu.isVisible) {
            setupMangaMinimapAppearance()
        }
        val targetFullPosition = currentPage?.let { page ->
            mAdapter.getItems().indexOfFirst { item ->
                item is MangaPage && item.chapterIndex == page.chapterIndex && item.index == page.index
            }
        } ?: -1
        if (targetFullPosition >= 0) {
            binding.recyclerView.post {
                scrollToMangaPagePosition(targetFullPosition)
            }
        } else if (shouldEnable) {
            scheduleDoubleColumnPairSync()
        }
    }

    private fun setAutoPageEnabled(enable: Boolean) {
        enableAutoScrollPage = enable
        enableAutoScroll = false
        mScrollTimer.isEnabledPage = enable
        mScrollTimer.isEnabled = false
        mMenu?.let { upMenu(it) }
    }

    private fun setAutoScrollEnabled(enable: Boolean) {
        enableAutoScroll = enable
        enableAutoScrollPage = false
        mScrollTimer.isEnabled = enable
        mScrollTimer.isEnabledPage = false
        if (enable) {
            mPagerSnapHelper.attachToRecyclerView(null)
        } else if (mangaHorizontalScroll) {
            mPagerSnapHelper.attachToRecyclerView(binding.recyclerView)
        }
        mMenu?.let { upMenu(it) }
    }

    @SuppressLint("StringFormatMatches")
    private fun upMenu(menu: Menu) {
        this.mMenu = menu
        menu.findItem(R.id.menu_pre_manga_number).title =
            getString(R.string.pre_download_m, AppConfig.mangaPreDownloadNum)
        menu.findItem(R.id.menu_disable_manga_scale).isChecked = mangaDisableScale
        menu.findItem(R.id.menu_disable_click_scroll).isChecked = mangaDisableClickScroll
        menu.findItem(R.id.menu_enable_auto_page).isChecked = enableAutoScrollPage
        menu.findItem(R.id.menu_enable_auto_scroll).isChecked = enableAutoScroll
        menu.findItem(R.id.menu_manga_auto_page_speed).title =
            getString(R.string.manga_auto_page_speed, mangaAutoPageSpeed)
        menu.findItem(R.id.menu_manga_auto_page_speed).isVisible =
            enableAutoScrollPage || enableAutoScroll
        menu.findItem(R.id.menu_enable_horizontal_scroll).isChecked =
            mangaHorizontalScroll
        menu.findItem(R.id.menu_hide_manga_title).isChecked = AppConfig.hideMangaTitle
        menu.findItem(R.id.menu_epaper_manga).isChecked = AppConfig.enableMangaEInk
        menu.findItem(R.id.menu_epaper_manga_setting).isVisible = AppConfig.enableMangaEInk
        menu.findItem(R.id.menu_disable_horizontal_page_snap).run {
            isVisible = mangaHorizontalScroll && !mangaDisablePageAnim
            isChecked = mangaDisableHorizontalPageSnap || mangaDisablePageAnim
        }
        menu.findItem(R.id.menu_login)?.isVisible =
            ReadManga.bookSource != null
        updateSourceActionMenuItem(menu.findItem(R.id.menu_login), ReadManga.bookSource)
        menu.findItem(R.id.menu_gray_manga).isChecked = AppConfig.enableMangaGray
    }

    private fun updateSourceActionMenuItem(item: MenuItem?, source: BookSource?) {
        val hasLoginUrl = !source?.loginUrl.isNullOrBlank()
        item?.setIcon(
            if (hasLoginUrl) R.drawable.ic_lucide_user else R.drawable.ic_lucide_link_2
        )
        item?.title = getString(
            if (hasLoginUrl) R.string.login else R.string.open_in_app_webview
        )
    }

    private fun applyBookMangaReadConfig() {
        setDoubleColumnLayout(
            enabled = isPad && ReadManga.book?.config?.mangaDoubleColumn == true,
            save = false,
        )
        setDisableClickScroll(mangaDisableClickScroll)
        setDisableMangaScale(mangaDisableScale)
        mScrollTimer.setSpeed(mangaAutoPageSpeed)
        mMenu?.let { upMenu(it) }
    }

    private fun updateBookMangaReadConfig(block: Book.ReadConfig.() -> Unit) {
        val book = ReadManga.book ?: return
        book.config.block()
        lifecycleScope.launch(IO) {
            book.save()
        }
    }

    private fun setDisableMangaScale(disable: Boolean) {
        binding.webtoonFrame.disableMangaScale = disable
        binding.webtoonFrameDoubleRight.disableMangaScale = disable
        binding.recyclerView.disableMangaScale = disable
        binding.recyclerViewDoubleRight.disableMangaScale = disable
        if (disable) {
            binding.recyclerView.resetZoom()
            binding.recyclerViewDoubleRight.resetZoom()
        }
    }

    private fun setDisableClickScroll(disable: Boolean) {
        binding.webtoonFrame.disabledClickScroll = disable
        binding.webtoonFrameDoubleRight.disabledClickScroll = disable
    }

    private fun upLayoutInDisplayCutoutMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun scrollToNext() {
        scrollPageTo(1)
    }

    private fun scrollToPrev() {
        scrollPageTo(-1)
    }

    private fun scrollPageTo(direction: Int) {
        if (!binding.recyclerView.canScroll(direction)) {
            return
        }
        var dx = 0
        var dy = 0
        if (mangaHorizontalScroll) {
            dx = binding.recyclerView.run {
                width - paddingStart - paddingEnd
            }
        } else {
            dy = binding.recyclerView.run {
                height - paddingTop - paddingBottom
            }
        }
        dx *= direction
        dy *= direction
        if (mangaDisablePageAnim) {
            binding.recyclerView.scrollBy(dx, dy)
        } else {
            binding.recyclerView.smoothScrollBy(dx, dy)
        }
    }

    private fun showNumberPickerDialog(
        min: Int,
        title: String,
        initValue: Int,
        callback: (Int) -> Unit,
    ) {
        NumberPickerDialog(this)
            .setTitle(title)
            .setMaxValue(9999)
            .setMinValue(min)
            .setValue(initValue)
            .show {
                callback.invoke(it)
            }
    }

    override fun finish() {
        if (binding.mangaMenu.hideChapterList()) return
        val book = ReadManga.book ?: return super.finish()

        if (ReadManga.inBookshelf) {
            return super.finish()
        }

        if (!AppConfig.showAddToShelfAlert) {
            viewModel.removeFromBookshelf { super.finish() }
        } else {
            alert(title = getString(R.string.add_to_bookshelf)) {
                setMessage(getString(R.string.check_add_bookshelf, book.name))
                okButton {
                    ReadManga.book?.removeType(BookType.notShelf)
                    ReadManga.book?.save()
                    ReadManga.inBookshelf = true
                    setResult(RESULT_OK)
                    super.finish()
                }
                noButton { viewModel.removeFromBookshelf { super.finish() } }
            }
        }
    }

    fun updateWindowBrightness(brightness: Int) {
        val layoutParams = window.attributes
        val normalizedBrightness = brightness.toFloat() / 255.0f
        layoutParams.screenBrightness = normalizedBrightness.coerceIn(0f, 1f)
        window.attributes = layoutParams
        // 强制刷新屏幕
        window.decorView.postInvalidate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                scrollToPrev()
                return true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                scrollToNext()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun previewEpaper(enable: Boolean, value: Int) {
        forEachMangaAdapter { enableMangaEInk(enable, value) }
    }

    override fun restoreEpaper(enable: Boolean, value: Int) {
        forEachMangaAdapter { enableMangaEInk(enable, value) }
    }

    override fun enableEpaper(value: Int) {
        AppConfig.enableMangaEInk = true
        AppConfig.enableMangaGray = false
        forEachMangaAdapter { enableMangaEInk(true, value) }
        mMenu?.let { upMenu(it) }
        binding.mangaMenu.runMenuOut()
    }

    override fun onEpaperSettingConfirmed() {
        mMenu?.let { upMenu(it) }
        binding.mangaMenu.runMenuOut()
    }
}
