package io.wanjuan.app.ui.book.read

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.core.view.size
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.qmdeve.liquidglass.widget.LiquidGlassView
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.wanjuan.app.BuildConfig
import io.wanjuan.app.R
import io.wanjuan.app.constant.AppConst
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.constant.BookType
import io.wanjuan.app.constant.EventBus
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.constant.Status
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookChapter
import io.wanjuan.app.data.entities.BookProgress
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.help.AppWebDav
import io.wanjuan.app.help.IntentData
import io.wanjuan.app.help.TTS
import io.wanjuan.app.help.book.BookHelp
import io.wanjuan.app.help.book.ContentProcessor
import io.wanjuan.app.help.book.ParagraphRuleProcessor
import io.wanjuan.app.help.book.isAudio
import io.wanjuan.app.help.book.isEpub
import io.wanjuan.app.help.book.isLocal
import io.wanjuan.app.help.book.isLocalTxt
import io.wanjuan.app.help.book.isMobi
import io.wanjuan.app.help.book.removeType
import io.wanjuan.app.help.book.update
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.config.ReadBookConfig
import io.wanjuan.app.help.config.ReadTipConfig
import io.wanjuan.app.help.coroutine.Coroutine
import io.wanjuan.app.help.source.getSourceType
import io.wanjuan.app.help.storage.Backup
import io.wanjuan.app.lib.dialogs.SelectItem
import io.wanjuan.app.lib.dialogs.alert
import io.wanjuan.app.lib.dialogs.selector
import io.wanjuan.app.lib.theme.accentColor
import io.wanjuan.app.model.ReadAloud
import io.wanjuan.app.model.ReadBook
import io.wanjuan.app.model.analyzeRule.AnalyzeRule
import io.wanjuan.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.wanjuan.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject
import io.wanjuan.app.utils.isJsonObject
import io.wanjuan.app.model.localBook.EpubFile
import io.wanjuan.app.model.localBook.MobiFile
import io.wanjuan.app.receiver.NetworkChangedListener
import io.wanjuan.app.receiver.TimeBatteryReceiver
import io.wanjuan.app.service.BaseReadAloudService
import io.wanjuan.app.ui.about.AppLogDialog
import io.wanjuan.app.ui.book.bookmark.BookmarkDialog
import io.wanjuan.app.ui.book.changesource.ChangeBookSourceDialog
import io.wanjuan.app.ui.book.changesource.ChangeChapterSourceDialog
import io.wanjuan.app.ui.book.info.BookInfoActivity
import io.wanjuan.app.ui.book.read.config.AutoReadDialog
import io.wanjuan.app.ui.book.read.config.BgTextConfigDialog.Companion.BG_COLOR
import io.wanjuan.app.ui.book.read.config.BgTextConfigDialog.Companion.TEXT_ACCENT_COLOR
import io.wanjuan.app.ui.book.read.config.BgTextConfigDialog.Companion.TEXT_COLOR
import io.wanjuan.app.ui.book.read.config.MoreConfigDialog
import io.wanjuan.app.ui.book.read.config.ParagraphRuleManageActivity
import io.wanjuan.app.ui.book.read.config.ReadAloudDialog
import io.wanjuan.app.ui.book.read.config.TipConfigDialog.Companion.TIP_COLOR
import io.wanjuan.app.ui.book.read.config.TipConfigDialog.Companion.TIP_DIVIDER_COLOR
import io.wanjuan.app.ui.book.read.page.ContentTextView
import io.wanjuan.app.ui.book.read.page.ReadView
import io.wanjuan.app.ui.book.read.page.delegate.ScrollPageDelegate
import io.wanjuan.app.ui.book.read.page.entities.PageDirection
import io.wanjuan.app.ui.book.read.page.entities.TextPage
import io.wanjuan.app.ui.book.read.page.provider.ChapterProvider
import io.wanjuan.app.ui.book.read.page.provider.LayoutProgressListener
import io.wanjuan.app.ui.book.searchContent.SearchContentActivity
import io.wanjuan.app.ui.book.searchContent.SearchResult
import io.wanjuan.app.model.SourceCallBack
import io.wanjuan.app.ui.book.source.edit.BookSourceEditActivity
import io.wanjuan.app.ui.font.FontSelectDialog
import io.wanjuan.app.ui.book.toc.TocActivityResult
import io.wanjuan.app.ui.book.toc.rule.TxtTocRuleDialog
import io.wanjuan.app.ui.browser.WebViewActivity
import io.wanjuan.app.ui.dict.DictDialog
import io.wanjuan.app.ui.file.HandleFileContract
import io.wanjuan.app.ui.replace.ReplaceRuleActivity
import io.wanjuan.app.ui.replace.edit.ReplaceEditActivity
import io.wanjuan.app.ui.widget.ModernActionPopup
import io.wanjuan.app.ui.widget.PopupAction
import io.wanjuan.app.ui.widget.dialog.BottomWebViewDialog
import io.wanjuan.app.ui.widget.dialog.CommentWebViewSession
import io.wanjuan.app.ui.widget.dialog.PhotoDialog
import io.wanjuan.app.utils.ACache
import io.wanjuan.app.utils.Debounce
import io.wanjuan.app.utils.LogUtils
import io.wanjuan.app.utils.NetworkUtils
import io.wanjuan.app.utils.StartActivityContract
import io.wanjuan.app.utils.buildMainHandler
import io.wanjuan.app.utils.dismissDialogFragment
import io.wanjuan.app.utils.dpToPx
import io.wanjuan.app.utils.getPrefBoolean
import io.wanjuan.app.utils.getPrefString
import io.wanjuan.app.utils.gone
import io.wanjuan.app.utils.hexString
import io.wanjuan.app.utils.iconItemOnLongClick
import io.wanjuan.app.utils.invisible
import io.wanjuan.app.utils.isAbsUrl
import io.wanjuan.app.utils.isTrue
import io.wanjuan.app.utils.launch
import io.wanjuan.app.utils.navigationBarGravity
import io.wanjuan.app.utils.observeEvent
import io.wanjuan.app.utils.observeEventSticky
import io.wanjuan.app.utils.postEvent
import io.wanjuan.app.utils.showDialogFragment
import io.wanjuan.app.utils.showHelp
import io.wanjuan.app.utils.startActivity
import io.wanjuan.app.utils.startActivityForBook
import io.wanjuan.app.utils.sysScreenOffTime
import io.wanjuan.app.utils.throttle
import io.wanjuan.app.utils.toastOnUi
import io.wanjuan.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import com.script.rhino.runScriptWithContext
import io.wanjuan.app.model.analyzeRule.AnalyzeUrl.Companion.paramPattern
import io.wanjuan.app.ui.login.SourceLoginJsExtensions
import kotlin.math.roundToInt

/**
 * 闃呰鐣岄潰
 */
class ReadBookActivity : BaseReadBookActivity(),
    View.OnTouchListener,
    ReadView.CallBack,
    TextActionMenu.CallBack,
    ContentTextView.CallBack,
    MenuItem.OnMenuItemClickListener,
    ReadMenu.CallBack,
    SearchMenu.CallBack,
    ReadAloudDialog.CallBack,
    FontSelectDialog.CallBack,
    ChangeBookSourceDialog.CallBack,
    ChangeChapterSourceDialog.CallBack,
    ReadBook.CallBack,
    AutoReadDialog.CallBack,
    TxtTocRuleDialog.CallBack,
    ColorPickerDialogListener,
    LayoutProgressListener {

    private val tocActivity =
        registerForActivityResult(TocActivityResult()) {
            it?.let {
                viewModel.openChapter(it[0] as Int, it[1] as Int)
            }
        }
    private val sourceEditActivity =
        registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                viewModel.upBookSource {
                    upMenuView()
                }
            }
        }
    private val replaceActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                viewModel.replaceRuleChanged()
            }
        }
    private val searchContentActivity =
        registerForActivityResult(StartActivityContract(SearchContentActivity::class.java)) {
            val data = it.data ?: return@registerForActivityResult
            val key = data.getLongExtra("key", System.currentTimeMillis())
            val index = data.getIntExtra("index", 0)
            val searchResult = IntentData.get<SearchResult>("searchResult$key")
            val searchResultList = IntentData.get<List<SearchResult>>("searchResultList$key")
            if (searchResult != null && searchResultList != null) {
                viewModel.searchContentQuery = searchResult.query
                binding.searchMenu.upSearchResultList(searchResultList)
                isShowingSearchResult = true
                viewModel.searchResultIndex = index
                binding.searchMenu.updateSearchResultIndex(index)
                binding.searchMenu.selectedSearchResult?.let { currentResult ->
                    ReadBook.saveCurrentBookProgress() //閫€鍑哄叏鏂囨悳绱㈡仮澶嶆鏃惰繘搴?
                    skipToSearch(currentResult)
                    showActionMenu()
                }
            }
        }
    private val bookInfoActivity =
        registerForActivityResult(StartActivityContract(BookInfoActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                setResult(RESULT_DELETED)
                super.finish()
            } else {
                ReadBook.loadOrUpContent()
            }
        }
    private var lastTextMenuAnchor: ReadAiFloatingPanel.Anchor? = null
    private val selectImageDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            ACache.get().put(AppConst.imagePathKey, uri.toString())
            viewModel.saveImage(it.value, uri)
        }
    }
    private var menu: Menu? = null
    private var modernMenuPopup: PopupWindow? = null
    private var backupJob: Job? = null
    private var tts: TTS? = null
    private var commentWebViewSession: CommentWebViewSession? = null
    @Volatile
    private var commentBrowserOpening = false
    @Volatile
    private var commentBrowserShowing = false
    val textActionMenu: TextActionMenu by lazy {
        TextActionMenu(this, this)
    }
    private val popupAction: PopupAction by lazy {
        PopupAction(this)
    }
    override val isInitFinish: Boolean get() = viewModel.isInitFinish
    override val isScroll: Boolean get() = binding.readView.isScroll
    private val isAutoPage get() = binding.readView.isAutoPage
    override var isShowingSearchResult = false
    override var isSelectingSearchResult = false
        set(value) {
            field = value && isShowingSearchResult
        }
    private val timeBatteryReceiver = TimeBatteryReceiver()
    private var screenTimeOut: Long = 0
    private var loadStates: Boolean = false
    override val pageFactory get() = binding.readView.pageFactory
    override val pageDelegate get() = binding.readView.pageDelegate
    override val headerHeight: Int get() = binding.readView.curPage.headerHeight
    override val imgBgPaddingStart: Int get() = binding.readView.curPage.imgBgPaddingStart
    private val nextPageDebounce by lazy { Debounce { keyPage(PageDirection.NEXT) } }
    private val prevPageDebounce by lazy { Debounce { keyPage(PageDirection.PREV) } }
    private var bookChanged = false
    private var pageChanged = false
    private val handler by lazy { buildMainHandler() }
    private val screenOffRunnable by lazy { Runnable { keepScreenOn(false) } }
    private val executor = ReadBook.executor
    private val updateChapterProgressMinimapThrottle = throttle(200) {
        runOnUiThread {
            updateChapterProgressMinimap()
        }
    }
    private val boundChapterMinimapGlassViewIds = hashSetOf<Int>()
    private var pendingChapterProgressMinimapLayoutSync = false

    //鎭㈠璺宠浆鍓嶈繘搴﹀璇濇鐨勪氦浜掔粨鏋?
    private var confirmRestoreProcess: Boolean? = null
    private val networkChangedListener by lazy {
        NetworkChangedListener(this)
    }
    private var justInitData: Boolean = false
    private var syncDialog: AlertDialog? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        binding.cursorLeft.setColorFilter(accentColor)
        binding.cursorRight.setColorFilter(accentColor)
        binding.cursorLeft.setOnTouchListener(this)
        binding.cursorRight.setOnTouchListener(this)
        binding.readAiPanel.attach(this)
        bindChapterProgressMinimap()
        window.setBackgroundDrawable(null)
        upScreenTimeOut()
        ReadBook.register(this)
        onBackPressedDispatcher.addCallback(this) {
            if (binding.readAiPanel.isVisible) {
                binding.readAiPanel.close()
                return@addCallback
            }
            if (isShowingSearchResult) {
                exitSearchMenu()
                restoreLastBookProcess()
                return@addCallback
            }
            //鎷︽埅杩斿洖渚涙仮澶嶉槄璇昏繘搴?
            if (ReadBook.lastBookProgress != null && confirmRestoreProcess != false) {
                restoreLastBookProcess()
                return@addCallback
            }
            if (BaseReadAloudService.isPlay()) {
                ReadAloud.pause(this@ReadBookActivity)
                toastOnUi(R.string.read_aloud_pause)
                return@addCallback
            }
            if (isAutoPage) {
                autoPageStop()
                return@addCallback
            }
            if (getPrefBoolean("disableReturnKey") && !menuLayoutIsVisible) {
                return@addCallback
            }
            finish()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        viewModel.initReadBookConfig(intent)
        Looper.myQueue().addIdleHandler {
            viewModel.initData(intent)
            false
        }
        justInitData = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.initData(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        upSystemUiVisibility()
        if (hasFocus) {
            binding.readMenu.upBrightnessState()
        } else if (!menuLayoutIsVisible) {
            ReadBook.cancelPreDownloadTask()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        upSystemUiVisibility()
        binding.readView.upStatusBar()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        if (!isTopResumedActivity) {
            ReadBook.cancelPreDownloadTask()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        ReadBook.readStartTime = System.currentTimeMillis()
        if (bookChanged) {
            bookChanged = false
            ReadBook.callBack = this
            viewModel.initData(intent)
            justInitData = true
        } else {
            //web绔槄璇绘椂锛宎pp澶勪簬闃呰鐣岄潰锛屾湰鍦拌褰曚細瑕嗙洊web淇濆瓨鐨勮繘搴︼紝鍦ㄦ澶勬仮澶?
            ReadBook.webBookProgress?.let {
                ReadBook.setProgress(it)
                ReadBook.webBookProgress = null
            }
        }
        upSystemUiVisibility()
        registerReceiver(timeBatteryReceiver, timeBatteryReceiver.filter)
        binding.readView.upTime()
        screenOffTimerStart()
        // 缃戠粶鐩戝惉锛屽綋浠庢棤缃戝垏鎹㈠埌缃戠粶鐜鏃跺悓姝ヨ繘搴︼紙娉ㄦ剰娉ㄥ唽鐨勫悓鏃跺氨浼氭敹鍒扮洃鍚紝鍥犳鐣岄潰婵€娲绘椂鏃犻渶閲嶅鎵ц鍚屾鎿嶄綔锛?
        networkChangedListener.register()
        networkChangedListener.onNetworkChanged = {
            // 褰撶綉缁滄槸鍙敤鐘舵€佷笖鏃犻渶鍒濆鍖栨椂鍚屾杩涘害锛堝垵濮嬪寲涓凡鏈夊悓姝ヨ繘搴﹂€昏緫锛?
            if (AppConfig.syncBookProgressPlus && NetworkUtils.isAvailable() && !justInitData && ReadBook.inBookshelf) {
                ReadBook.syncProgress({ progress -> sureNewProgress(progress) })
            }
        }
    }

    override fun onPause() {
        super.onPause()
        autoPageStop()
        backupJob?.cancel()
        ReadBook.saveRead()
        ReadBook.cancelPreDownloadTask()
        unregisterReceiver(timeBatteryReceiver)
        upSystemUiVisibility()
        if (!BuildConfig.DEBUG && ReadBook.inBookshelf) {
            if (AppConfig.syncBookProgressPlus) {
                ReadBook.syncProgress()
            } else {
                ReadBook.uploadProgress()
            }
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
        justInitData = false
        networkChangedListener.unRegister()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_read, menu)
        menu.iconItemOnLongClick(R.id.menu_change_source) {
            modernMenuPopup = ModernActionPopup.showFromMenu(
                it,
                R.menu.book_read_change_source,
                modernMenuPopup,
                onClick = ::onMenuItemClick
            )
        }
        menu.iconItemOnLongClick(R.id.menu_refresh) {
            modernMenuPopup = ModernActionPopup.showFromMenu(
                it,
                R.menu.book_read_refresh,
                modernMenuPopup,
                onClick = ::onMenuItemClick
            )
        }
        binding.readMenu.refreshMenuColorFilter()
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        this.menu = menu
        upMenu()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_same_title_removed)?.isChecked =
            ReadBook.curTextChapter?.sameTitleRemoved == true
        return super.onMenuOpened(featureId, menu)
    }

    /**
     * 鏇存柊鑿滃崟
     */
    private fun upMenu() {
        val menu = menu ?: return
        val book = ReadBook.book ?: return
        val onLine = !book.isLocal
        for (i in 0 until menu.size) {
            val item = menu[i]
            when (item.groupId) {
                R.id.menu_group_on_line -> item.isVisible = onLine
                R.id.menu_group_local -> item.isVisible = !onLine
                R.id.menu_group_text -> item.isVisible = book.isLocalTxt
                R.id.menu_group_epub -> item.isVisible = book.isEpub
                else -> when (item.itemId) {
                    R.id.menu_enable_replace -> item.isChecked = book.getUseReplaceRule()
                    R.id.menu_re_segment -> item.isChecked = book.getReSegment()
//                    R.id.menu_enable_review -> {
//                        item.isVisible = BuildConfig.DEBUG
//                        item.isChecked = AppConfig.enableReview
//                    }

                    R.id.menu_reverse_content -> item.isVisible = onLine
                    R.id.menu_paragraph_rule_manage -> item.isVisible = !book.isEpub
                    R.id.menu_del_ruby_tag -> item.isChecked = book.getDelTag(Book.rubyTag)
                    R.id.menu_del_h_tag -> item.isChecked = book.getDelTag(Book.hTag)
                }
            }
        }
        menu.findItem(R.id.menu_login)?.isVisible =
            onLine && ReadBook.bookSource != null
        updateSourceActionMenuItem(menu.findItem(R.id.menu_login), ReadBook.bookSource)
        lifecycleScope.launch {
            val show = ReadBook.inBookshelf && withContext(IO) {
                AppWebDav.isOk
            }
            menu.findItem(R.id.menu_get_progress)?.isVisible = show
            menu.findItem(R.id.menu_cover_progress)?.isVisible = show
        }
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

    /**
     * 鑿滃崟
     */
    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_change_source,
            R.id.menu_book_change_source -> {
                binding.readMenu.runMenuOut()
                ReadBook.book?.let {
                    showDialogFragment(ChangeBookSourceDialog(it.name, it.author))
                }
            }

            R.id.menu_chapter_change_source -> lifecycleScope.launch {
                val book = ReadBook.book ?: return@launch
                val chapter =
                    appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                        ?: return@launch
                binding.readMenu.runMenuOut()
                showDialogFragment(
                    ChangeChapterSourceDialog(book.name, book.author, chapter.index, chapter.title)
                )
            }

            R.id.menu_refresh,
            R.id.menu_refresh_dur -> {
                if (ReadBook.bookSource == null) {
                    upContent()
                } else {
                    ReadBook.book?.let {
                        ReadBook.curTextChapter = null
                        binding.readView.upContent()
                        viewModel.refreshContentDur(it)
                    }
                }
            }

            R.id.menu_refresh_after -> {
                if (ReadBook.bookSource == null) {
                    upContent()
                } else {
                    ReadBook.book?.let {
                        ReadBook.clearTextChapter()
                        binding.readView.upContent()
                        viewModel.refreshContentAfter(it)
                    }
                }
            }

            R.id.menu_refresh_all -> {
                if (ReadBook.bookSource == null) {
                    upContent()
                } else {
                    ReadBook.book?.let {
                        refreshContentAll(it)
                    }
                }
            }

            R.id.menu_download -> showDownloadDialog()
            R.id.menu_login -> showLogin()
            R.id.menu_add_bookmark -> addBookmark()
            R.id.menu_simulated_reading -> showSimulatedReading()
            R.id.menu_edit_content -> showDialogFragment(ContentEditDialog())
            R.id.menu_update_toc -> ReadBook.book?.let {
                if (it.isEpub) {
                    BookHelp.clearCache(it)
                    EpubFile.clear()
                }
                if (it.isMobi) {
                    MobiFile.clear()
                }
                loadChapterList(it)
            }

            R.id.menu_enable_replace -> changeReplaceRuleState()
            R.id.menu_re_segment -> ReadBook.book?.let {
                it.setReSegment(!it.getReSegment())
                item.isChecked = it.getReSegment()
                ReadBook.loadContent(false)
            }

//            R.id.menu_enable_review -> {
//                AppConfig.enableReview = !AppConfig.enableReview
//                item.isChecked = AppConfig.enableReview
//                ReadBook.loadContent(false)
//            }

            R.id.menu_del_ruby_tag -> ReadBook.book?.let {
                item.isChecked = !item.isChecked
                if (item.isChecked) {
                    it.addDelTag(Book.rubyTag)
                } else {
                    it.removeDelTag(Book.rubyTag)
                }
                refreshContentAll(it)
            }

            R.id.menu_del_h_tag -> ReadBook.book?.let {
                item.isChecked = !item.isChecked
                if (item.isChecked) {
                    it.addDelTag(Book.hTag)
                } else {
                    it.removeDelTag(Book.hTag)
                }
                refreshContentAll(it)
            }

            R.id.menu_page_anim -> showPageAnimConfig {
                binding.readView.upPageAnim()
                ReadBook.loadContent(false)
            }

            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_toc_regex -> showDialogFragment(
                TxtTocRuleDialog(ReadBook.book?.tocUrl)
            )

            R.id.menu_reverse_content -> ReadBook.book?.let {
                viewModel.reverseContent(it)
            }

            R.id.menu_set_charset -> showCharsetConfig()
            R.id.menu_image_style -> {
                val imgStyles =
                    arrayListOf(
                        Book.imgStyleDefault, Book.imgStyleFull, Book.imgStyleText,
                        Book.imgStyleSingle
                    )
                selector(
                    R.string.image_style,
                    imgStyles
                ) { _, index ->
                    val imageStyle = imgStyles[index]
                    ReadBook.book?.setImageStyle(imageStyle)
                    if (imageStyle == Book.imgStyleSingle) {
                        ReadBook.book?.setPageAnim(0)  // 鍒囨崲鍥剧墖鏍峰紡single鍚庯紝鑷姩鍒囨崲涓鸿鐩?
                        binding.readView.upPageAnim()
                    }
                    ReadBook.loadContent(false)
                }
            }

            R.id.menu_get_progress -> ReadBook.book?.let {
                viewModel.syncBookProgress(it) { progress ->
                    sureSyncProgress(progress)
                }
            }

            R.id.menu_cover_progress -> ReadBook.book?.let {
                ReadBook.uploadProgress(true) { toastOnUi(R.string.upload_book_success) }
            }

            R.id.menu_same_title_removed -> {
                ReadBook.book?.let {
                    val contentProcessor = ContentProcessor.get(it)
                    val textChapter = ReadBook.curTextChapter
                    if (textChapter != null
                        && !textChapter.sameTitleRemoved
                        && !BookHelp.getChapterCacheFileNames(it, textChapter.chapter, "nr")
                            .any(contentProcessor.removeSameTitleCache::contains)
                    ) {
                        toastOnUi("未找到可移除的重复标题")
                    }
                }
                viewModel.reverseRemoveSameTitle()
            }

            R.id.menu_effective_replaces -> showDialogFragment<EffectiveReplacesDialog>()

            R.id.menu_paragraph_rule_manage -> ReadBook.book?.let {
                startActivity<ParagraphRuleManageActivity> {
                    putExtra("bookUrl", it.bookUrl)
                }
            }

            R.id.menu_help -> showHelp()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun refreshContentAll(book: Book) {
        ReadBook.clearTextChapter()
        binding.readView.upContent()
        viewModel.refreshContentAll(book)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return onCompatOptionsItemSelected(item)
    }

    /**
     * 鎸夐敭鎷︽埅,鏄剧ず鑿滃崟
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action
        val isDown = action == 0

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (isDown && !binding.readMenu.canShowMenu) {
                binding.readMenu.runMenuIn()
                return true
            }
            if (!isDown && !binding.readMenu.canShowMenu) {
                binding.readMenu.canShowMenu = true
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * 榧犳爣婊氳疆浜嬩欢
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (0 != (event.source and InputDevice.SOURCE_CLASS_POINTER)) {
            if (event.action == MotionEvent.ACTION_SCROLL) {
                val axisValue = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                LogUtils.d("onGenericMotionEvent", "axisValue = $axisValue")
                // 鑾峰緱鍨傜洿鍧愭爣涓婄殑婊氬姩鏂瑰悜
                if (axisValue < 0.0f) { // 婊氳疆鍚戜笅婊?
                    mouseWheelPage(PageDirection.NEXT, axisValue)
                } else { // 婊氳疆鍚戜笂婊?
                    mouseWheelPage(PageDirection.PREV, axisValue)
                }
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    /**
     * 鎸夐敭浜嬩欢
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (menuLayoutIsVisible) {
            return super.onKeyDown(keyCode, event)
        }
        val longPress = event.repeatCount > 0
        when {
            isPrevKey(keyCode) -> {
                handleKeyPage(PageDirection.PREV, longPress)
                return true
            }

            isNextKey(keyCode) -> {
                handleKeyPage(PageDirection.NEXT, longPress)
                return true
            }
        }
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> if (volumeKeyPage(PageDirection.PREV, longPress)) {
                return true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> if (volumeKeyPage(PageDirection.NEXT, longPress)) {
                return true
            }

            KeyEvent.KEYCODE_PAGE_UP -> {
                handleKeyPage(PageDirection.PREV, longPress)
                return true
            }

            KeyEvent.KEYCODE_PAGE_DOWN -> {
                handleKeyPage(PageDirection.NEXT, longPress)
                return true
            }

            KeyEvent.KEYCODE_SPACE -> {
                handleKeyPage(PageDirection.NEXT, longPress)
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    /**
     * 鏉惧紑鎸夐敭浜嬩欢
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (volumeKeyPage(PageDirection.NONE, false)) {
                    return true
                }
            }

        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * view瑙︽懜,鏂囧瓧閫夋嫨
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean = binding.run {
        if (!binding.readView.isTextSelected) {
            return false
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> textActionMenu.dismiss()
            MotionEvent.ACTION_MOVE -> {
                when (v.id) {
                    R.id.cursor_left -> if (!readView.curPage.getReverseStartCursor()) {
                        readView.curPage.selectStartMove(
                            event.rawX + cursorLeft.width,
                            event.rawY - cursorLeft.height
                        )
                    } else {
                        readView.curPage.selectEndMove(
                            event.rawX - cursorRight.width,
                            event.rawY - cursorRight.height
                        )
                    }

                    R.id.cursor_right -> if (readView.curPage.getReverseEndCursor()) {
                        readView.curPage.selectStartMove(
                            event.rawX + cursorLeft.width,
                            event.rawY - cursorLeft.height
                        )
                    } else {
                        readView.curPage.selectEndMove(
                            event.rawX - cursorRight.width,
                            event.rawY - cursorRight.height
                        )
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                readView.curPage.resetReverseCursor()
                showTextActionMenu()
            }
        }
        return true
    }

    /**
     * 鏇存柊鏂囧瓧閫夋嫨寮€濮嬩綅缃?
     */
    override fun upSelectedStart(x: Float, y: Float, top: Float) = binding.run {
        cursorLeft.x = x - cursorLeft.width
        cursorLeft.y = y
        cursorLeft.visible(true)
        textMenuPosition.x = x
        textMenuPosition.y = top
    }

    /**
     * 鏇存柊鏂囧瓧閫夋嫨缁撴潫浣嶇疆
     */
    override fun upSelectedEnd(x: Float, y: Float) = binding.run {
        cursorRight.x = x
        cursorRight.y = y
        cursorRight.visible(true)
    }

    /**
     * 鍙栨秷鏂囧瓧閫夋嫨
     */
    override fun onCancelSelect() = binding.run {
        cursorLeft.invisible()
        cursorRight.invisible()
        textActionMenu.dismiss()
    }

    override fun onLongScreenshotTouchEvent(event: MotionEvent): Boolean {
        return binding.readView.onTouchEvent(event)
    }

    /**
     * 鏄剧ず鏂囨湰鎿嶄綔鑿滃崟
     */
    override fun showTextActionMenu() {
        val navigationBarHeight =
            if (!ReadBookConfig.hideNavigationBar && navigationBarGravity == Gravity.BOTTOM)
                binding.navigationBar.height else 0
        val startX = binding.textMenuPosition.x.toInt()
        val topY = binding.textMenuPosition.y.toInt()
        val endX = binding.cursorRight.x.toInt()
        val startBottomY = binding.cursorLeft.y.toInt() + binding.cursorLeft.height
        val endBottomY = binding.cursorRight.y.toInt() + binding.cursorRight.height
        val centerX = ((startX + endX) / 2f).toInt()
        val bottomY = maxOf(startBottomY, endBottomY)
        lastTextMenuAnchor = ReadAiFloatingPanel.Anchor(
            centerX = centerX,
            topY = topY,
            bottomY = bottomY
        )
        textActionMenu.show(
            binding.root,
            binding.root.height + navigationBarHeight,
            startX,
            topY,
            startBottomY,
            endX,
            endBottomY
        )
    }

    /**
     * 褰撳墠閫夋嫨鐨勬枃鏈?
     */
    override val selectedText: String get() = binding.readView.getSelectText()

    /**
     * 鏂囨湰閫夋嫨鑿滃崟鎿嶄綔
     */
    override fun onMenuItemSelected(itemId: Int): Boolean {
        when (itemId) {
            R.id.menu_aloud -> when (AppConfig.contentSelectSpeakMod) {
                1 -> lifecycleScope.launch {
                    binding.readView.aloudStartSelect()
                }

                else -> speak(binding.readView.getSelectText())
            }

            R.id.menu_bookmark -> binding.readView.curPage.let {
                val bookmark = it.createBookmark()
                if (bookmark == null) {
                    toastOnUi(R.string.create_bookmark_error)
                } else {
                    showDialogFragment(BookmarkDialog(bookmark))
                }
                return true
            }

            R.id.menu_replace -> {
                val scopes = arrayListOf<String>()
                ReadBook.book?.name?.let {
                    scopes.add(it)
                }
                ReadBook.bookSource?.bookSourceUrl?.let {
                    scopes.add(it)
                }
                val text = selectedText.lineSequence().map { it.trim() }.joinToString("\n")
                replaceActivity.launch(
                    ReplaceEditActivity.startIntent(
                        this,
                        pattern = text,
                        scope = scopes.joinToString(";")
                    )
                )
                return true
            }

            R.id.menu_search_content -> {
                viewModel.searchContentQuery = selectedText
                openSearchActivity(selectedText)
                return true
            }

            R.id.menu_dict -> {
                showDialogFragment(DictDialog(selectedText))
                return true
            }
            R.id.menu_ask_ai -> {
                askAiBySelection()
                return true
            }
        }
        return false
    }

    private fun askAiBySelection() {
        val prompt = selectedText.trim()
        if (prompt.isEmpty()) return
        if (AppConfig.aiCurrentProvider?.baseUrl.isNullOrBlank() || AppConfig.aiCurrentModelConfig == null) {
            toastOnUi(R.string.ai_missing_config)
            return
        }
        if (!AppConfig.aiAssistantEnabled) {
            toastOnUi(R.string.ai_not_enabled)
            return
        }
        val book = ReadBook.book
        val chapter = ReadBook.curTextChapter?.chapter
        val anchor = lastTextMenuAnchor
            ?: ReadAiFloatingPanel.Anchor(
                centerX = binding.root.width / 2,
                topY = binding.root.height / 3,
                bottomY = binding.root.height / 2
            )
        binding.readAiPanel.open(
            ReadAiFloatingPanel.ReadContext(
                bookUrl = book?.bookUrl.orEmpty().ifBlank { book?.name.orEmpty() },
                bookName = book?.name.orEmpty(),
                author = book?.author.orEmpty(),
                sourceName = ReadBook.bookSource?.bookSourceName.orEmpty(),
                chapterTitle = chapter?.title.orEmpty(),
                chapterIndex = chapter?.index ?: ReadBook.durChapterIndex,
                selectedText = prompt
            ),
            anchor = anchor
        )
    }

    /**
     * 鏂囨湰閫夋嫨鑿滃崟鎿嶄綔瀹屾垚
     */
    override fun onMenuActionFinally() = binding.run {
        textActionMenu.dismiss()
        readView.cancelSelect()
    }

    private fun speak(text: String) {
        if (tts == null) {
            tts = TTS()
        }
        tts?.speak(text)
    }

    /**
     * 榧犳爣婊氳疆缈婚〉
     */
    private fun mouseWheelPage(direction: PageDirection, distance: Float) {
        if (menuLayoutIsVisible || !AppConfig.mouseWheelPage) {
            return
        }
        if (binding.readView.isScroll) {
            // 婊氬姩瑙嗗浘鏃舵粴鍔?鍚﹀垯缈婚〉
            (binding.readView.pageDelegate as? ScrollPageDelegate)?.curPage?.scroll((distance * 50).toInt())
        } else {
            keyPageDebounce(direction, mouseWheel = true, longPress = false)
        }
    }

    /**
     * 闊抽噺閿炕椤?
     */
    private fun volumeKeyPage(direction: PageDirection, longPress: Boolean): Boolean {
        if (!AppConfig.volumeKeyPage) {
            return false
        }
        if (!AppConfig.volumeKeyPageOnPlay && BaseReadAloudService.isPlay()) {
            return false
        }
        handleKeyPage(direction, longPress)
        return true
    }

    private fun handleKeyPage(direction: PageDirection, longPress: Boolean) {
        if (AppConfig.keyPageOnLongPress || direction == PageDirection.NONE) {
            keyPage(direction)
        } else {
            keyPageDebounce(direction, longPress = longPress)
        }
    }

    private fun keyPageDebounce(
        direction: PageDirection,
        mouseWheel: Boolean = false,
        longPress: Boolean
    ) {
        if (longPress) {
            return
        }
        nextPageDebounce.apply {
            wait = if (mouseWheel) 200L else 600L
            leading = !mouseWheel
            trailing = mouseWheel
        }
        prevPageDebounce.apply {
            wait = if (mouseWheel) 200L else 600L
            leading = !mouseWheel
            trailing = mouseWheel
        }
        when (direction) {
            PageDirection.NEXT -> nextPageDebounce.invoke()
            PageDirection.PREV -> prevPageDebounce.invoke()
            else -> {}
        }
    }

    private fun keyPage(direction: PageDirection) {
        binding.readView.cancelSelect()
        binding.readView.pageDelegate?.isCancel = false
        binding.readView.pageDelegate?.keyTurnPage(direction)
    }

    override fun upMenuView() {
        handler.post {
            upMenu()
            binding.readMenu.upBookView()
        }
    }

    override fun loadChapterList(book: Book) {
        ReadBook.upMsg(getString(R.string.toc_updateing))
        viewModel.loadChapterList(book)
    }

    /**
     * 鍐呭鍔犺浇瀹屾垚
     */
    override fun contentLoadFinish() {
        if (intent.getBooleanExtra("readAloud", false)) {
            intent.removeExtra("readAloud")
            ReadBook.readAloud()
        }
        loadStates = true
    }

    /**
     * 鏇存柊鍐呭
     */
    override fun upContent(
        relativePosition: Int,
        resetPageOffset: Boolean,
        success: (() -> Unit)?
    ) {
        lifecycleScope.launch {
            binding.readView.upContent(relativePosition, resetPageOffset)
            if (relativePosition == 0) {
                updateChapterProgressMinimap()
            }
            loadStates = false
            success?.invoke()
        }
    }

    override suspend fun upContentAwait(
        relativePosition: Int,
        resetPageOffset: Boolean,
        success: (() -> Unit)?
    ) = withContext(Main.immediate) {
        binding.readView.upContent(relativePosition, resetPageOffset)
        if (relativePosition == 0) {
            updateChapterProgressMinimap()
        }
        loadStates = false
    }

    override fun upPageAnim(upRecorder: Boolean) {
        lifecycleScope.launch {
            binding.readView.upPageAnim(upRecorder)
        }
    }

    override fun notifyBookChanged() {
        bookChanged = true
        if (!ReadBook.inBookshelf) {
            viewModel.removeFromBookshelf { super.finish() }
        }
    }

    override fun cancelSelect() {
        runOnUiThread {
            binding.readView.cancelSelect()
        }
    }

    /**
     * 椤甸潰鏀瑰彉
     */
    override fun pageChanged() {
        pageChanged = true
        binding.readView.onPageChange()
        handler.post {
            updateChapterProgressMinimap()
        }
        executor.execute {
            startBackupJob()
        }
    }

    private fun bindChapterProgressMinimap() {
        binding.chapterProgressMinimap.onProgressChanging = ::previewChapterProgressMinimap
        binding.chapterProgressMinimap.onProgressChanged = ::commitChapterProgressMinimap
        binding.btnChapterMinimapPrevious.setMinimapChapterNavigationClickListener(binding.tvChapterMinimapPrevious) {
            ReadBook.moveToPrevChapter(upContent = true, toLast = false)
        }
        binding.btnChapterMinimapNext.setMinimapChapterNavigationClickListener(binding.tvChapterMinimapNext) {
            ReadBook.moveToNextChapter(true)
        }
    }

    private fun setupChapterMinimapControlGlass() {
        val glassViews = listOf(
            binding.chapterMinimapPreviousGlassView,
            binding.chapterMinimapNextGlassView
        )
        if (!ViewCompat.isLaidOut(binding.readContentContainer) || glassViews.any { !ViewCompat.isLaidOut(it) }) {
            binding.chapterProgressMinimapPanel.post {
                if (binding.chapterProgressMinimapPanel.isVisible) {
                    setupChapterMinimapControlGlass()
                }
            }
            return
        }
        val glassLevel = ReaderBottomGlassStyle.glassLevel()
        val cornerRadius = 28f.dpToPx()
        setupChapterMinimapControlGlassView(
            button = binding.btnChapterMinimapPrevious,
            glassView = binding.chapterMinimapPreviousGlassView,
            shellOverlay = binding.chapterMinimapPreviousShellOverlay,
            glassLevel = glassLevel,
            cornerRadius = cornerRadius
        )
        setupChapterMinimapControlGlassView(
            button = binding.btnChapterMinimapNext,
            glassView = binding.chapterMinimapNextGlassView,
            shellOverlay = binding.chapterMinimapNextShellOverlay,
            glassLevel = glassLevel,
            cornerRadius = cornerRadius
        )
    }

    private fun setupChapterMinimapControlGlassView(
        button: View,
        glassView: LiquidGlassView,
        shellOverlay: View,
        glassLevel: Float,
        cornerRadius: Float
    ) {
        button.clipToOutline = false
        button.background = ReaderBottomGlassStyle.fallbackShell(this, glassLevel, cornerRadius)
        shellOverlay.background = ReaderBottomGlassStyle.shell(this, glassLevel, cornerRadius)
        val shouldBind = !boundChapterMinimapGlassViewIds.contains(glassView.id)
        if (ReaderBottomGlassStyle.configureLiquidGlass(
            liquidGlassView = glassView,
            target = binding.readContentContainer,
            cornerRadius = cornerRadius,
            bindTarget = shouldBind,
            glassLevel = glassLevel
        )) {
            boundChapterMinimapGlassViewIds.add(glassView.id)
        }
    }

    private fun updateChapterProgressMinimap(show: Boolean = binding.readMenu.isVisible) {
        val textChapter = ReadBook.curTextChapter
        val pageCount = textChapter?.pageSize ?: 0
        val shouldShow = show && !binding.readMenu.isExpandedPanelVisible
        if (show && textChapter != null) {
            binding.chapterProgressMinimap.updateChapter(
                textChapter.getContent(),
                pageCount,
                ReadBook.durPageIndex
            )
        } else {
            binding.chapterProgressMinimap.updateProgress(pageCount, ReadBook.durPageIndex)
        }
        binding.chapterProgressMinimapPanel.gone(!shouldShow || pageCount <= 1)
        if (!shouldShow || pageCount <= 1) {
            return
        }
        if (!chapterProgressMinimapMenuChromeReady()) {
            binding.chapterProgressMinimapPanel.gone()
            binding.chapterProgressMinimapPanel.post {
                if (binding.readMenu.isVisible) {
                    updateChapterProgressMinimap(show = true)
                }
            }
            return
        }
        val preservePanelPosition = binding.chapterProgressMinimap.shouldPreservePanelPosition()
        setupChapterMinimapControlGlass()
        if (!preservePanelPosition && !constrainChapterProgressMinimapPanel()) {
            binding.chapterProgressMinimapPanel.gone()
        }
    }

    private fun scheduleChapterProgressMinimapStableSync() {
        if (pendingChapterProgressMinimapLayoutSync) {
            return
        }
        pendingChapterProgressMinimapLayoutSync = true
        binding.chapterProgressMinimapPanel.post {
            binding.chapterProgressMinimapPanel.post {
                pendingChapterProgressMinimapLayoutSync = false
                if (binding.readMenu.isVisible &&
                    binding.chapterProgressMinimapPanel.isVisible &&
                    !binding.chapterProgressMinimap.shouldPreservePanelPosition()
                ) {
                    constrainChapterProgressMinimapPanel()
                }
            }
        }
    }

    private fun chapterProgressMinimapMenuChromeReady(): Boolean {
        val topBar = binding.readMenu.findViewById<View>(R.id.title_bar_shell)
        val bottomBar = binding.readMenu.findViewById<View>(R.id.bottom_menu)
        return topBar?.isVisible == true &&
                topBar.height > 0 &&
                bottomBar?.isVisible == true &&
                bottomBar.height > 0
    }

    private fun constrainChapterProgressMinimapPanel(): Boolean {
        val root = binding.root
        if (root.height <= 0) {
            root.post {
                updateChapterProgressMinimap(show = true)
            }
            return true
        }
        val gap = 8.dpToPx()
        val topLimit = minimapTopLimit(
            root = root,
            topBar = binding.readMenu.findViewById(R.id.title_bar_shell),
            fallbackTop = 96.dpToPx(),
            gap = gap
        )
        val bottomLimit = minimapBottomLimit(
            root = root,
            bottomBar = binding.readMenu.findViewById(R.id.bottom_menu),
            fallbackBottom = 80.dpToPx(),
            gap = gap
        )
        val availableHeight = (bottomLimit - topLimit).coerceAtLeast(0)
        val controlsTopMargin = (binding.chapterProgressMinimapControls.layoutParams as? ViewGroup.MarginLayoutParams)
            ?.topMargin ?: 0
        val controlsHeight = binding.chapterProgressMinimapControls.height
            .takeIf { it > 0 }
            ?: 70.dpToPx()
        val maxMinimapHeight = availableHeight - controlsTopMargin - controlsHeight
        val minimumMinimapHeight = 96.dpToPx()
        if (maxMinimapHeight < minimumMinimapHeight) {
            return false
        }
        val minimapHeight = 300.dpToPx().coerceAtMost(maxMinimapHeight)
        val panelHeight = minimapHeight + controlsTopMargin + controlsHeight
        binding.chapterProgressMinimap.updateLayoutParams<ViewGroup.LayoutParams> {
            height = minimapHeight
        }
        binding.chapterProgressMinimapPanel.updateLayoutParams<FrameLayout.LayoutParams> {
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

    private fun previewChapterProgressMinimap(ratio: Float) {
        val target = chapterProgressTarget(ratio) ?: return
        binding.readView.previewChapterProgress(target.pageIndex, target.pageOffset)
    }

    private fun commitChapterProgressMinimap(ratio: Float) {
        val target = chapterProgressTarget(ratio) ?: return
        ReadBook.commitChapterProgressPosition(target.chapterPosition) {
            updateChapterProgressMinimap(show = true)
            binding.chapterProgressMinimap.pinProgressRatio(ratio)
        }
    }

    private fun chapterProgressTarget(ratio: Float): ChapterProgressTarget? {
        val pages = ReadBook.curTextChapter?.pages?.takeIf { it.isNotEmpty() } ?: return null
        val visibleHeight = ChapterProvider.visibleHeight.toFloat().coerceAtLeast(1f)
        val totalHeight = pages.sumOf { page ->
            page.height.toDouble().coerceAtLeast(1.0)
        }.toFloat()
        val maxScroll = (totalHeight - visibleHeight).coerceAtLeast(0f)
        if (maxScroll <= 0f) {
            val firstPage = pages.first()
            return ChapterProgressTarget(0, 0, firstPage.chapterPosition)
        }
        val scrollTop = maxScroll * ratio.coerceIn(0f, 1f)
        var beforePage = 0f
        pages.forEachIndexed { index, page ->
            val pageHeight = page.height.coerceAtLeast(1f)
            val afterPage = beforePage + pageHeight
            if (scrollTop < afterPage || index == pages.lastIndex) {
                val offsetInPage = (scrollTop - beforePage).coerceIn(0f, pageHeight)
                val pageOffset = -(scrollTop - beforePage)
                    .coerceIn(0f, pageHeight)
                    .roundToInt()
                return ChapterProgressTarget(
                    index,
                    pageOffset,
                    chapterPositionForMinimapOffset(page, offsetInPage)
                )
            }
            beforePage = afterPage
        }
        val lastPage = pages.last()
        return ChapterProgressTarget(
            pages.lastIndex,
            0,
            lastPage.chapterPosition
        )
    }

    private fun chapterPositionForMinimapOffset(page: TextPage, offsetInPage: Float): Int {
        val targetY = offsetInPage + ChapterProvider.paddingTop
        return page.lines.firstOrNull { line ->
            line.lineBottom >= targetY
        }?.chapterPosition
            ?: page.lines.lastOrNull()?.let { line ->
                line.chapterPosition + line.charSize
            }
            ?: page.chapterPosition
    }

    private data class ChapterProgressTarget(
        val pageIndex: Int,
        val pageOffset: Int,
        val chapterPosition: Int
    )

    /**
     * 鏄剧ず鑿滃崟
     */
    override fun showMenuBar() {
        binding.readMenu.runMenuIn()
    }

    override val oldBook: Book?
        get() = ReadBook.book

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        if (!book.isAudio) {
            viewModel.changeTo(book, toc)
        } else {
            ReadAloud.stop(this)
            lifecycleScope.launch {
                withContext(IO) {
                    ReadBook.book?.migrateTo(book, toc)
                    book.removeType(BookType.updateError)
                    ReadBook.book?.delete()
                    appDb.bookDao.insert(book)
                }
                startActivityForBook(book)
                finish()
            }
        }
    }

    override fun replaceContent(content: String) {
        ReadBook.book?.let {
            viewModel.saveContent(it, content)
        }
    }

    override fun showActionMenu() {
        when {
            BaseReadAloudService.isRun -> showReadAloudDialog()
            isAutoPage -> showDialogFragment<AutoReadDialog>()
            isShowingSearchResult -> binding.searchMenu.runMenuIn()
            else -> binding.readMenu.runMenuIn()
        }
    }

    /**
     * 鏄剧ず鏈楄鑿滃崟
     */
    override fun showReadAloudDialog() {
        showDialogFragment<ReadAloudDialog>()
    }

    /**
     * 鑷姩缈婚〉
     */
    override fun autoPage() {
        ReadAloud.stop(this)
        if (isAutoPage) {
            autoPageStop()
        } else {
            binding.readView.autoPager.start()
            binding.readMenu.setAutoPage(true)
            screenTimeOut = -1L
            screenOffTimerStart()
        }
    }

    override fun autoPageStop() {
        if (isAutoPage) {
            binding.readView.autoPager.stop()
            binding.readMenu.setAutoPage(false)
            dismissDialogFragment<AutoReadDialog>()
            upScreenTimeOut()
        }
    }

    override fun openSourceEditActivity() {
        ReadBook.bookSource?.let {
            sourceEditActivity.launch {
                putExtra("sourceUrl", it.bookSourceUrl)
            }
        }
    }

    override fun openBookInfoActivity() {
        ReadBook.book?.let {
            bookInfoActivity.launch {
                putExtra("name", it.name)
                putExtra("author", it.author)
            }
        }
    }

    /**
     * 鏇挎崲
     */
    override fun openReplaceRule() {
        replaceActivity.launch(Intent(this, ReplaceRuleActivity::class.java))
    }

    /**
     * 鎵撳紑鐩綍
     */
    override fun openChapterList() {
        ReadBook.book?.let {
            tocActivity.launch(it.bookUrl)
        }
    }

    /**
     * 鎵撳紑鎼滅储鐣岄潰
     */
    override fun openSearchActivity(searchWord: String?) {
        val book = ReadBook.book ?: return
        searchContentActivity.launch {
            putExtra("bookUrl", book.bookUrl)
            putExtra("searchWord", searchWord ?: viewModel.searchContentQuery)
            putExtra("searchResultIndex", viewModel.searchResultIndex)
            viewModel.searchResultList?.first()?.let {
                if (it.query == viewModel.searchContentQuery) {
                    IntentData.put("searchResultList", viewModel.searchResultList)
                }
            }
        }
    }

    override fun openInlineSearchResult(
        searchResult: SearchResult,
        results: List<SearchResult>,
        index: Int
    ) {
        if (results.isEmpty()) {
            return
        }
        viewModel.searchContentQuery = searchResult.query
        viewModel.searchResultIndex = index
        viewModel.searchResultList = results
        binding.searchMenu.upSearchResultList(results)
        binding.searchMenu.updateSearchResultIndex(index)
        isShowingSearchResult = true
        ReadBook.saveCurrentBookProgress()
        binding.readMenu.runMenuOut {
            skipToSearch(searchResult)
            showActionMenu()
        }
    }

    /**
     * 绂佺敤涔︽簮
     */
    override fun disableSource() {
        viewModel.disableSource()
    }

    override val curFontPath: String
        get() = ReadBookConfig.textFont

    override fun selectFont(path: String) {
        if (path != ReadBookConfig.textFont || path.isEmpty()) {
            ReadBookConfig.textFont = path
            ReadBookConfig.save()
            binding.readMenu.reset()
            postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5))
        }
    }

    /**
     * 鏄剧ず鏇村璁剧疆
     */
    override fun showMoreSetting() {
        showDialogFragment<MoreConfigDialog>()
    }

    override fun showSearchSetting() {
        showDialogFragment<MoreConfigDialog>()
    }

    /**
     * 鏇存柊鐘舵€佹爮,瀵艰埅鏍?
     */
    override fun upSystemUiVisibility() {
        upSystemUiVisibility(isInMultiWindow, !menuLayoutIsVisible, bottomDialog > 0)
        upNavigationBarColor()
    }

    // 閫€鍑哄叏鏂囨悳绱?
    override fun exitSearchMenu() {
        if (isShowingSearchResult) {
            isShowingSearchResult = false
            binding.searchMenu.invalidate()
            binding.searchMenu.invisible()
            ReadBook.clearSearchResult()
            binding.readView.cancelSelect(true)
        }
    }

    /* 鎭㈠鍒?鍏ㄦ枃鎼滅储/杩涘害鏉¤烦杞墠鐨勪綅缃?*/
    private fun restoreLastBookProcess() {
        if (confirmRestoreProcess == true) {
            ReadBook.restoreLastBookProgress()
        } else if (confirmRestoreProcess == null) {
            alert(R.string.draw) {
                setMessage(R.string.restore_last_book_process)
                yesButton {
                    confirmRestoreProcess = true
                    ReadBook.restoreLastBookProgress() //鎭㈠鍚姩鍏ㄦ枃鎼滅储鍓嶇殑杩涘害
                }
                noButton {
                    ReadBook.lastBookProgress = null
                    confirmRestoreProcess = false
                }
                onCancelled {
                    ReadBook.lastBookProgress = null
                    confirmRestoreProcess = false
                }
            }
        }
    }

    override fun showLogin() {
        val url = ReadBook.curTextChapter?.chapter?.getAbsoluteURL()
            ?.takeIf { it.isNotBlank() }
            ?: return
        startActivity<WebViewActivity> {
            val bookSource = ReadBook.bookSource
            putExtra("title", ReadBook.curTextChapter?.title ?: ReadBook.book?.name)
            putExtra("url", url)
            putExtra("sourceOrigin", bookSource?.bookSourceUrl)
            putExtra("sourceName", bookSource?.bookSourceName)
            putExtra("sourceType", bookSource?.getSourceType())
        }
    }

    override fun payAction() {
        val book = ReadBook.book ?: return
        if (book.isLocal) return
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
        if (chapter == null) {
            toastOnUi("no chapter")
            return
        }
        alert(R.string.chapter_pay) {
            setMessage(chapter.title)
            yesButton {
                Coroutine.async(lifecycleScope) {
                    val source =
                        ReadBook.bookSource ?: throw NoStackTraceException("no book source")
                    val payAction = source.getContentRule().payAction
                    if (payAction.isNullOrBlank()) {
                        throw NoStackTraceException("no pay action")
                    }
                    val java = SourceLoginJsExtensions(this@ReadBookActivity, source, BookType.text)
                    runScriptWithContext {
                        source.evalJS(payAction) {
                            put("java", java)
                            put("book", book)
                            put("chapter", chapter)
                            put("title", chapter.title)
                            put("baseUrl", chapter.url)
                            put("result", null)
                            put("src", null)
                        }.toString()
                    }
                }.onSuccess(IO) {
                    if (it.isAbsUrl()) {
                        startActivity<WebViewActivity> {
                            val bookSource = ReadBook.bookSource
                            putExtra("title", getString(R.string.chapter_pay))
                            putExtra("url", it)
                            putExtra("sourceOrigin", bookSource?.bookSourceUrl)
                            putExtra("sourceName", bookSource?.bookSourceName)
                            putExtra("sourceType", bookSource?.getSourceType())
                        }
                    } else if (it.isTrue()) {
                        //璐拱鎴愬姛鍚庡埛鏂扮洰褰?
                        ReadBook.book?.let {
                            ReadBook.curTextChapter = null
                            BookHelp.delContent(book, chapter)
                            loadChapterList(book)
                        }
                    }
                }.onError {
                    AppLog.put("鎵ц璐拱鎿嶄綔鍑洪敊\n${it.localizedMessage}", it, true)
                }
            }
            noButton()
        }
    }

    /**
     * 鐐瑰嚮鍥剧墖
     */
    private fun evalParagraphRuleClick(click: String?, src: String): Boolean {
        if (!ParagraphRuleProcessor.isParagraphClick(click)) return false
        if (commentBrowserOpening || commentBrowserShowing) return true
        val clickValue = click ?: return false
        commentBrowserOpening = true
        getCommentWebViewSession().prepare(applicationContext)
        Coroutine.async(lifecycleScope, IO) {
            val book = ReadBook.book ?: return@async
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: throw Exception("no find chapter")
            ParagraphRuleProcessor.evalClick(
                book,
                chapter,
                clickValue,
                src,
                paragraphRuleBrowserCallback(),
                coroutineContext
            )
        }.onError {
            AppLog.put("ParagraphRule pclick error: ${it.localizedMessage}", it, true)
        }.onFinally {
            if (!commentBrowserShowing) {
                commentBrowserOpening = false
            }
        }
        return true
    }

    private fun getCommentWebViewSession(): CommentWebViewSession {
        return commentWebViewSession ?: CommentWebViewSession.shared.also { commentWebViewSession = it }
    }

    private fun paragraphRuleBrowserCallback(): ParagraphRuleProcessor.BrowserCallback {
        return object : ParagraphRuleProcessor.BrowserCallback {
            override fun showBrowser(
                url: String,
                html: String?,
                preloadJs: String?,
                config: String?,
                sourceKey: String?
            ): Boolean {
                val browserSourceKey = sourceKey ?: ReadBook.bookSource?.getKey() ?: return false
                commentBrowserShowing = true
                runOnUiThread {
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        commentBrowserShowing = false
                        commentBrowserOpening = false
                        return@runOnUiThread
                    }
                    showDialogFragment(
                        BottomWebViewDialog(
                            browserSourceKey,
                            BookType.text,
                            url,
                            html,
                            preloadJs,
                            config,
                            getCommentWebViewSession(),
                            onDismiss = {
                                commentBrowserShowing = false
                                commentBrowserOpening = false
                            }
                        )
                    )
                }
                return true
            }
        }
    }

    private fun ensureCurrentChapterCacheForClick(book: Book, chapter: BookChapter) {
        val current = ReadBook.curTextChapter
            ?.takeIf { it.chapter.index == chapter.index }
            ?.getContent()
            ?.takeIf { it.isNotBlank() }
        if (!BookHelp.hasContent(book, chapter) && current != null) {
            BookHelp.saveText(book, chapter, current)
        }
        BookHelp.ensureLegacyContentAlias(book, chapter, current)
    }

    override fun oldClickImg(src: String): Boolean {
        val urlMatcher = paramPattern.matcher(src)
        if (urlMatcher.find()) {
            val urlOptionStr = src.substring(urlMatcher.end())
            val urlOptionMap = GSON.fromJsonObject<Map<String, String>>(urlOptionStr).getOrNull()
            val pclick = urlOptionMap?.get("pclick")
            if (!pclick.isNullOrBlank() && evalParagraphRuleClick(pclick, src)) return true
            val click = urlOptionMap?.get("click")
                ?.takeUnless { ParagraphRuleProcessor.isParagraphClick(it) }
            if (!click.isNullOrBlank()) {
                Coroutine.async(lifecycleScope,IO) {
                    val source = ReadBook.bookSource ?: return@async
                    val java = SourceLoginJsExtensions(this@ReadBookActivity, source, BookType.text)
                    val book = ReadBook.book ?: return@async
                    val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex) ?: throw Exception("no find chapter")
                    ensureCurrentChapterCacheForClick(book, chapter)
                    runScriptWithContext {
                        source.evalJS(click) {
                            put("java", java)
                            put("book", book)
                            put("chapter", chapter)
                            put("result", src)
                        }
                    }
                }.onError {
                    AppLog.put("鎵ц鍥剧墖閾炬帴click閿€煎嚭閿橽n${it.localizedMessage}", it, true)
                }
                return true
            }
            val jsStr = urlOptionMap?.get("js") ?: return false
            Coroutine.async(lifecycleScope, IO) {
                val source = ReadBook.bookSource ?: return@async
                val book = ReadBook.book ?: return@async
                val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex) ?: throw Exception("no find chapter")
                val urlNoOption = src.take(urlMatcher.start())
                AnalyzeRule(book, source).apply {
                    setCoroutineContext(coroutineContext)
                    setBaseUrl(chapter.url)
                    setChapter(chapter)
                    evalJS(jsStr, urlNoOption)
                }
            }.onError {
                AppLog.put("鎵ц鍥剧墖閾炬帴js閿€煎嚭閿橽n${it.localizedMessage}", it, true)
            }
            return true
        }
        return false
    }

    override fun clickImg(click: String, src: String) {
        if (evalParagraphRuleClick(click, src)) return
        Coroutine.async(lifecycleScope,IO) {
            val source = ReadBook.bookSource ?: return@async
            val java = SourceLoginJsExtensions(this@ReadBookActivity, source, BookType.text)
            val book = ReadBook.book ?: return@async
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex) ?: throw Exception("no find chapter")
            ensureCurrentChapterCacheForClick(book, chapter)
            runScriptWithContext {
                source.evalJS(click) {
                    put("java", java)
                    put("book", book)
                    put("chapter", chapter)
                    put("result", src)
                }
            }
        }.onError {
            AppLog.put("鎵ц鍥剧墖閾炬帴click閿€煎嚭閿橽n${it.localizedMessage}", it, true)
        }
    }


    /**
     * 鏈楄鎸夐挳
     */
    override fun onClickReadAloud() {
        autoPageStop()
        when {
            !BaseReadAloudService.isRun -> {
                ReadAloud.upReadAloudClass()
                val scrollPageAnim = ReadBook.pageAnim() == 3
                if (scrollPageAnim) {
                    val pos = binding.readView.getReadAloudPos()
                    if (pos != null) {
                        val (index, line) = pos
                        if (ReadBook.durChapterIndex != index) {
                            ReadBook.openChapter(index, line.chapterPosition, false) {
                                ReadBook.readAloud(startPos = line.pagePosition)
                            }
                        } else {
                            ReadBook.durChapterPos = line.chapterPosition
                            ReadBook.readAloud(startPos = line.pagePosition)
                        }
                    } else {
                        ReadBook.readAloud()
                    }
                } else {
                    ReadBook.readAloud()
                }
            }

            BaseReadAloudService.pause -> {
                val scrollPageAnim = ReadBook.pageAnim() == 3
                if (scrollPageAnim && pageChanged) {
                    pageChanged = false
                    val pos = binding.readView.getReadAloudPos()
                    if (pos != null) {
                        val (index, line) = pos
                        if (ReadBook.durChapterIndex != index) {
                            ReadBook.openChapter(index, line.chapterPosition, false) {
                                ReadBook.readAloud(startPos = line.pagePosition)
                            }
                        } else {
                            ReadBook.durChapterPos = line.chapterPosition
                            ReadBook.readAloud(startPos = line.pagePosition)
                        }
                    } else {
                        ReadBook.readAloud()
                    }
                } else {
                    ReadAloud.resume(this)
                }
            }

            else -> ReadAloud.pause(this)
        }
    }

    override fun showHelp() {
        showHelp("readMenuHelp")
    }

    /**
     * 闀挎寜鍥剧墖
     */
    @SuppressLint("RtlHardcoded")
    override fun onImageLongPress(x: Float, y: Float, src: String) {
        popupAction.setItems(
            listOf(
                SelectItem(getString(R.string.show), "show"),
                SelectItem(getString(R.string.refresh), "refresh"),
                SelectItem(getString(R.string.action_save), "save"),
                SelectItem(getString(R.string.menu), "menu"),
                SelectItem(getString(R.string.select_folder), "selectFolder")
            )
        )
        popupAction.onActionClick = {
            when (it) {
                "show" -> showDialogFragment(PhotoDialog(src, isBook = true))
                "refresh" -> viewModel.refreshImage(src)
                "save" -> {
                    val path = ACache.get().getAsString(AppConst.imagePathKey)
                    if (path.isNullOrEmpty()) {
                        selectImageDir.launch {
                            value = src
                        }
                    } else {
                        viewModel.saveImage(src, path.toUri())
                    }
                }

                "menu" -> showActionMenu()
                "selectFolder" -> selectImageDir.launch()
            }
            popupAction.dismiss()
        }
        val navigationBarHeight =
            if (!ReadBookConfig.hideNavigationBar && navigationBarGravity == Gravity.BOTTOM)
                binding.navigationBar.height else 0
        popupAction.showAtLocation(
            binding.readView, Gravity.BOTTOM or Gravity.LEFT, x.toInt(),
            binding.root.height + navigationBarHeight - y.toInt()
        )
    }

    /**
     * colorSelectDialog
     */
    override fun onColorSelected(dialogId: Int, color: Int) = ReadBookConfig.durConfig.run {
        when (dialogId) {
            TEXT_COLOR -> {
                setCurTextColor(color)
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6, 9, 11))
                if (AppConfig.readBarStyleFollowPage) {
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }
                binding.readMenu.persistActiveThemeSuiteChange()
            }

            TEXT_ACCENT_COLOR -> {
                setCurTextAccentColor(color)
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6, 9, 11))
                if (AppConfig.readBarStyleFollowPage) {
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }
            }

            BG_COLOR -> {
                setCurBg(0, "#${color.hexString}")
                postEvent(EventBus.UP_CONFIG, arrayListOf(1))
                if (AppConfig.readBarStyleFollowPage) {
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }
                binding.readMenu.reset()
                binding.readMenu.persistActiveThemeSuiteChange()
            }

            TIP_COLOR -> {
                ReadTipConfig.tipColor = color
                postEvent(EventBus.TIP_COLOR, "")
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }

            TIP_DIVIDER_COLOR -> {
                ReadTipConfig.tipDividerColor = color
                postEvent(EventBus.TIP_COLOR, "")
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
        }
    }

    /**
     * colorSelectDialog
     */
    override fun onDialogDismissed(dialogId: Int) = Unit

    override fun onTocRegexDialogResult(tocRegex: String) {
        ReadBook.book?.let {
            it.tocUrl = tocRegex
            loadChapterList(it)
        }
    }

    private fun sureSyncProgress(progress: BookProgress) {
        alert(R.string.get_book_progress) {
            setMessage(R.string.current_progress_exceeds_cloud)
            okButton {
                ReadBook.setProgress(progress)
            }
            noButton()
        }
    }

    /* 杩涘害鏉¤烦杞埌鎸囧畾绔犺妭 */
    override fun skipToChapter(index: Int) {
        ReadBook.saveCurrentBookProgress() //閫€鍑虹珷鑺傝烦杞仮澶嶆鏃惰繘搴?
        viewModel.openChapter(index)
    }

    /* 鍏ㄦ枃鎼滅储璺宠浆 */
    override fun navigateToSearch(searchResult: SearchResult, index: Int) {
        viewModel.searchResultIndex = index
        skipToSearch(searchResult)
    }

    override fun onMenuShow() {
        updateChapterProgressMinimap(show = true)
        scheduleChapterProgressMinimapStableSync()
        binding.readView.autoPager.pause()
    }

    override fun onMenuHide() {
        binding.readView.autoPager.resume()
        binding.chapterProgressMinimap.clearPinnedProgressRatio()
        binding.chapterProgressMinimapPanel.gone()
    }

    override fun onReadMenuExpandedPanelVisibilityChanged(isVisible: Boolean) {
        if (isVisible) {
            binding.chapterProgressMinimapPanel.gone()
        } else {
            updateChapterProgressMinimap(show = binding.readMenu.isVisible)
        }
    }

    override fun onLayoutPageCompleted(index: Int, page: TextPage) {
        updateChapterProgressMinimapThrottle.invoke()
        binding.readView.onLayoutPageCompleted(index, page)
    }

    /* 鍏ㄦ枃鎼滅储璺宠浆 */
    private fun skipToSearch(searchResult: SearchResult) {
        if (searchResult.chapterIndex != ReadBook.durChapterIndex) {
            viewModel.openChapter(searchResult.chapterIndex) {
                jumpToPosition(searchResult)
            }
        } else {
            jumpToPosition(searchResult)
        }
    }

    private fun jumpToPosition(searchResult: SearchResult) {
        val curTextChapter = ReadBook.curTextChapter ?: return
        binding.searchMenu.updateSearchInfo()
        val searchResultPositions =
            viewModel.searchResultPositions(curTextChapter, searchResult)
        val (pageIndex, lineIndex, charIndex, addLine, charIndex2) = searchResultPositions
        ReadBook.skipToPage(pageIndex) {
            isSelectingSearchResult = true
            binding.readView.curPage.selectStartMoveIndex(0, lineIndex, charIndex)
            when (addLine) {
                0 -> binding.readView.curPage.selectEndMoveIndex(
                    0,
                    lineIndex,
                    charIndex + searchResultPositions[5] - 1
                )

                1 -> binding.readView.curPage.selectEndMoveIndex(
                    0, lineIndex + 1, charIndex2
                )
                //consider change page, jump to scroll position
                -1 -> binding.readView.curPage.selectEndMoveIndex(1, 0, charIndex2)
            }
            binding.readView.isTextSelected = true
            isSelectingSearchResult = false
        }
    }

    override fun addBookmark() {
        val book = ReadBook.book
        val page = ReadBook.curTextChapter?.getPage(ReadBook.durPageIndex)
        if (book != null && page != null) {
            val bookmark = book.createBookMark().apply {
                chapterIndex = ReadBook.durChapterIndex
                chapterPos = ReadBook.durChapterPos
                chapterName = page.title
                bookText = page.text.trim()
            }
            showDialogFragment(BookmarkDialog(bookmark))
        }
    }

    override fun changeReplaceRuleState() {
        ReadBook.book?.let {
            it.setUseReplaceRule(!it.getUseReplaceRule())
            ReadBook.saveRead()
            menu?.findItem(R.id.menu_enable_replace)?.isChecked = it.getUseReplaceRule()
            viewModel.replaceRuleChanged()
        }
    }

    private fun startBackupJob() {
        backupJob?.cancel()
        backupJob = lifecycleScope.launch(IO) {
            delay(300000)
            ReadBook.book?.let {
                AppWebDav.uploadBookProgress(it)
                ensureActive()
                it.update()
                Backup.autoBack(this@ReadBookActivity)
            }
        }
    }

    override fun sureNewProgress(progress: BookProgress) {
        syncDialog?.dismiss()
        syncDialog = alert(R.string.get_book_progress) {
            setMessage(R.string.cloud_progress_exceeds_current)
            okButton {
                ReadBook.setProgress(progress)
            }
            noButton()
        }
    }

    override fun finish() {
        val book = ReadBook.book ?: return super.finish()
        if (ReadBook.inBookshelf) {
            callBackBookEnd()
            return super.finish()
        }
        if (!AppConfig.showAddToShelfAlert) {
            callBackBookEnd()
            viewModel.removeFromBookshelf { super.finish() }
        } else {
            alert(title = getString(R.string.add_to_bookshelf)) {
                setMessage(getString(R.string.check_add_bookshelf, book.name))
                okButton {
                    ReadBook.book?.removeType(BookType.notShelf)
                    ReadBook.book?.save()
                    SourceCallBack.callBackBook(SourceCallBack.ADD_BOOK_SHELF, ReadBook.bookSource, ReadBook.book)
                    ReadBook.inBookshelf = true
                    setResult(RESULT_OK)
                    callBackBookEnd()
                    super.finish()
                }
                noButton {
                    callBackBookEnd()
                    viewModel.removeFromBookshelf { super.finish() }
                }
            }
        }
    }

    private fun callBackBookEnd() {
        SourceCallBack.callBackBook(SourceCallBack.END_READ, ReadBook.bookSource, ReadBook.book, ReadBook.curTextChapter?.chapter)
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.clearTts()
        textActionMenu.dismiss()
        popupAction.dismiss()
        binding.readView.onDestroy()
        commentWebViewSession?.destroy()
        commentWebViewSession = null
        commentBrowserOpening = false
        commentBrowserShowing = false
        ReadBook.unregister(this)
        handler.removeCallbacksAndMessages(null) // 娓呯悊Handler娑堟伅
        if (!ReadBook.inBookshelf && !isChangingConfigurations) {
            viewModel.removeFromBookshelf(null)
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        commentWebViewSession?.trimMemory(level)
    }

    override fun observeLiveBus() = binding.run {
        observeEvent<String>(EventBus.TIME_CHANGED) { readView.upTime() }
        observeEvent<Int>(EventBus.BATTERY_CHANGED) { readView.upBattery(it) }
        observeEvent<Boolean>(EventBus.MEDIA_BUTTON) {
            if (it) {
                onClickReadAloud()
            } else {
                ReadBook.readAloud(!BaseReadAloudService.pause)
            }
        }
        observeEvent<ArrayList<Int>>(EventBus.UP_CONFIG) {
            it.forEach { value ->
                when (value) {
                    0 -> upSystemUiVisibility()
                    1 -> readView.upBg()
                    2 -> readView.upStyle()
                    3 -> readView.upBgAlpha()
                    4 -> readView.upPageSlopSquare()
                    5 -> if (isInitFinish) ReadBook.loadContent(resetPageOffset = false)
                    6 -> readView.upContent(resetPageOffset = false)
                    8 -> ChapterProvider.upStyle()
                    9 -> readView.invalidateTextPage()
                    10 -> ChapterProvider.upLayout()
                    11 -> readView.submitRenderTask()
                    12 -> readView.upPageTouchClick()
                }
            }
        }
        observeEvent<Int>(EventBus.ALOUD_STATE) {
            if (it == Status.STOP || it == Status.PAUSE) {
                ReadBook.curTextChapter?.let { textChapter ->
                    val page = textChapter.getPageByReadPos(ReadBook.durChapterPos)
                    if (page != null) {
                        page.removePageAloudSpan()
                        readView.upContent(resetPageOffset = false)
                    }
                }
            }
        }
        observeEventSticky<Int>(EventBus.TTS_PROGRESS) { chapterStart ->
            lifecycleScope.launch(IO) {
                if (BaseReadAloudService.isPlay()) {
                    ReadBook.curTextChapter?.let { textChapter ->
                        ReadBook.durChapterPos = chapterStart
                        val pageIndex = ReadBook.durPageIndex
                        val aloudSpanStart = chapterStart - textChapter.getReadLength(pageIndex)
                        textChapter.getPage(pageIndex)
                            ?.upPageAloudSpan(aloudSpanStart)
                        upContent()
                    }
                }
            }
        }
        observeEvent<Boolean>(PreferKey.keepLight) {
            upScreenTimeOut()
        }
        observeEvent<Boolean>(PreferKey.textSelectAble) {
            readView.curPage.upSelectAble(it)
        }
        observeEvent<String>(PreferKey.showBrightnessView) {
            readMenu.upBrightnessState()
        }
        observeEvent<List<SearchResult>>(EventBus.SEARCH_RESULT) {
            viewModel.searchResultList = it
        }
        observeEvent<Boolean>(EventBus.UPDATE_READ_ACTION_BAR) {
            readMenu.reset()
        }
        observeEvent<Boolean>(EventBus.UP_SEEK_BAR) {
            updateChapterProgressMinimap()
        }
        observeEvent<Boolean>(EventBus.REFRESH_BOOK_CONTENT) { //涔︽簮js鍑芥暟瑙﹀彂鍒锋柊
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                ReadBook.book?.let {
                    ReadBook.curTextChapter = null
                    binding.readView.upContent()
                    viewModel.refreshContentDur(it)
                }
            }
        }
        observeEvent<Boolean>(EventBus.REFRESH_BOOK_TOC) { //涔︽簮js鍑芥暟瑙﹀彂鍒锋柊
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                ReadBook.book?.let {
                    loadChapterList(it)
                }
            }
        }
    }

    private fun upScreenTimeOut() {
        val keepLightPrefer = getPrefString(PreferKey.keepLight)?.toInt() ?: 0
        screenTimeOut = keepLightPrefer * 1000L
        screenOffTimerStart()
    }

    /**
     * 閲嶇疆榛戝睆鏃堕棿
     */
    override fun screenOffTimerStart() {
        handler.post {
            if (screenTimeOut < 0) {
                keepScreenOn(true)
                return@post
            }
            val t = screenTimeOut - sysScreenOffTime
            if (t > 0) {
                keepScreenOn(true)
                handler.removeCallbacks(screenOffRunnable)
                handler.postDelayed(screenOffRunnable, screenTimeOut)
            } else {
                keepScreenOn(false)
            }
        }
    }

    companion object {
        const val RESULT_DELETED = 100
    }

}
