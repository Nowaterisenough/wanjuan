package io.wanjuan.app.ui.book.read

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.qmdeve.liquidglass.widget.LiquidGlassView
import io.wanjuan.app.R
import io.wanjuan.app.constant.EventBus
import io.wanjuan.app.constant.PageAnim
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Bookmark
import io.wanjuan.app.data.entities.BookChapter
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.databinding.DialogEditTextBinding
import io.wanjuan.app.databinding.ViewReadThemeCardBinding
import io.wanjuan.app.databinding.ViewReadMenuBinding
import io.wanjuan.app.databinding.ViewReadBackgroundCardBinding
import io.wanjuan.app.help.book.BookHelp
import io.wanjuan.app.help.config.AdvancedTitleConfig
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.config.LocalConfig
import io.wanjuan.app.help.config.ReadBookConfig
import io.wanjuan.app.help.config.ReadTipConfig
import io.wanjuan.app.help.book.ContentProcessor
import io.wanjuan.app.help.coroutine.Coroutine
import io.wanjuan.app.help.glide.ImageLoader
import io.wanjuan.app.help.source.getSourceType
import io.wanjuan.app.lib.dialogs.alert
import io.wanjuan.app.lib.dialogs.selector
import io.wanjuan.app.lib.theme.Selector
import io.wanjuan.app.lib.theme.accentColor
import io.wanjuan.app.lib.theme.bottomBackground
import io.wanjuan.app.lib.theme.buttonDisabledColor
import io.wanjuan.app.lib.theme.getPrimaryTextColor
import io.wanjuan.app.lib.theme.view.ThemeSwitch
import io.wanjuan.app.model.ReadAloud
import io.wanjuan.app.model.ReadBook
import io.wanjuan.app.service.BaseReadAloudService
import io.wanjuan.app.ui.browser.WebViewActivity
import io.wanjuan.app.ui.font.FontSelectDialog
import io.wanjuan.app.ui.book.read.config.BgTextConfigDialog.Companion.BG_COLOR
import io.wanjuan.app.ui.book.read.config.ContentSelectMenuConfigDialog
import io.wanjuan.app.ui.book.read.config.PageKeyDialog
import io.wanjuan.app.ui.book.read.config.ReadAloudConfigDialog
import io.wanjuan.app.ui.book.read.config.BgTextConfigDialog.Companion.TEXT_COLOR
import io.wanjuan.app.ui.book.read.config.TipConfigDialog.Companion.TIP_DIVIDER_COLOR
import io.wanjuan.app.ui.book.read.page.provider.ChapterProvider
import io.wanjuan.app.ui.book.searchContent.SearchContentAdapter
import io.wanjuan.app.ui.book.searchContent.SearchContentViewModel
import io.wanjuan.app.ui.book.searchContent.SearchResult
import io.wanjuan.app.ui.widget.number.NumberPickerDialog
import io.wanjuan.app.ui.widget.seekbar.SeekBarChangeListener
import io.wanjuan.app.utils.ChineseUtils
import io.wanjuan.app.utils.ColorUtils
import io.wanjuan.app.utils.applyTopBarIconMetrics
import io.wanjuan.app.utils.canvasrecorder.CanvasRecorderFactory
import io.wanjuan.app.utils.activity
import io.wanjuan.app.utils.applyNavigationBarPadding
import io.wanjuan.app.utils.applyStatusBarPadding
import io.wanjuan.app.utils.dpToPx
import io.wanjuan.app.utils.getPrefBoolean
import io.wanjuan.app.utils.getCompatColor
import io.wanjuan.app.utils.getPrefString
import io.wanjuan.app.utils.putPrefInt
import io.wanjuan.app.utils.gone
import io.wanjuan.app.utils.hexString
import io.wanjuan.app.utils.invisible
import io.wanjuan.app.utils.loadAnimation
import io.wanjuan.app.utils.openUrl
import io.wanjuan.app.utils.postEvent
import io.wanjuan.app.utils.putPrefBoolean
import io.wanjuan.app.utils.putPrefString
import io.wanjuan.app.utils.showDialogFragment
import io.wanjuan.app.utils.stackBlur
import io.wanjuan.app.utils.startActivity
import io.wanjuan.app.utils.toastOnUi
import io.wanjuan.app.utils.visible
import splitties.views.onClick
import java.io.File
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 阅读界面菜单
 */
class ReadMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    var canShowMenu: Boolean = false
    val isExpandedPanelVisible: Boolean
        get() = binding.flExpandedPanel.isVisible
    private val callBack: CallBack get() = activity as CallBack
    private val binding = ViewReadMenuBinding.inflate(LayoutInflater.from(context), this, true)
    private var isMenuOutAnimating = false
    private enum class BottomTab {
        Search,
        Toc,
        Aloud,
        Settings,
        Layout,
        PageTurn,
        Background,
        Theme
    }

    private enum class BottomTabMode {
        Primary,
        Interface
    }

    private enum class LayoutMarginAdjustMode {
        Body,
        Text,
        Title,
        Header,
        Footer
    }

    private enum class LayoutMarginSide {
        Top,
        Bottom,
        Left,
        Right
    }

    private enum class TocPanelPage {
        Chapters,
        Bookmarks
    }

    private var activeBottomTab: BottomTab? = null
    private var bottomTabSelectionAnchor: BottomTab? = null
    private var bottomTabMode: BottomTabMode = BottomTabMode.Primary
    private var activeLayoutMarginAdjustMode: LayoutMarginAdjustMode = LayoutMarginAdjustMode.Body
    private var suppressBottomNavSelection: Boolean = false
    private var bottomTabPanelAttached: Boolean = false
    private var bottomTabHeightAnimator: ValueAnimator? = null
    private var bottomTabGlassStyleKey: String? = null
    private var topBarGlassStyleKey: String? = null
    private var layoutAdjustGlassStyleKey: String? = null
    private var bottomTabGlassLayerHeight: Int = 0
    private var bottomTabIndicatorRequestToken: Int = 0
    private var layoutAdjustGlassLayerHeight: Int = 0
    private var layoutAdjustBackdropBitmap: Bitmap? = null
    private var layoutAdjustBackdropCropBitmap: Bitmap? = null
    private var tocLoadJob: Coroutine<List<BookChapter>>? = null
    private var bookmarkLoadJob: Coroutine<List<Bookmark>>? = null
    private var tocPanelFullscreen: Boolean = false
    private var tocPanelPage: TocPanelPage = TocPanelPage.Chapters
    private var tocDragStartY: Float = 0f
    private var tocDragStartPanelHeight: Int = 0
    private val boundBottomTabGlassViewIds = hashSetOf<Int>()
    private val tocAdapter by lazy { ReadMenuTocAdapter(::openTocChapter) }
    private val bookmarkAdapter by lazy { ReadMenuBookmarkAdapter(::openBookmark) }
    private val searchPanelAdapter by lazy {
        SearchContentAdapter(context, object : SearchContentAdapter.Callback {
            override fun openSearchResult(searchResult: SearchResult, index: Int) {
                if (searchResult.query.isBlank()) {
                    return
                }
                callBack.openInlineSearchResult(
                    searchResult,
                    searchPanelResults.toList(),
                    index
                )
            }

            override fun durChapterIndex(): Int = ReadBook.durChapterIndex
        })
    }
    private val searchPanelResults = arrayListOf<SearchResult>()
    private var searchPanelJob: Coroutine<List<SearchResult>>? = null
    private data class FontSample(
        val binding: ViewReadThemeCardBinding,
        val titleRes: Int,
        val typeface: Typeface,
        val isSelected: () -> Boolean,
        val onClick: () -> Unit
    )

    private data class PageAnimSample(
        val binding: ViewReadThemeCardBinding,
        val labelRes: Int,
        val anim: Int?
    )

    private data class BackgroundSample(
        val binding: ViewReadBackgroundCardBinding,
        val assetName: String
    )

    private data class ThemeSuiteCard(
        val suite: ReadMenuThemeSuite,
        val selected: Boolean,
        val custom: Boolean
    )

    private val fontSampleBindings by lazy {
        listOf(
            FontSample(
                newFontSampleCard(),
                R.string.default_font,
                Typeface.DEFAULT,
                { ReadBookConfig.textFont.isBlank() && AppConfig.systemTypefaces == 0 },
                { setSystemFont(0) }
            ),
            FontSample(
                newFontSampleCard(false),
                R.string.read_menu_add_custom_font,
                Typeface.DEFAULT,
                { ReadBookConfig.textFont.isNotBlank() },
                { openFontSelectDialog() }
            )
        )
    }

    private fun newFontSampleCard(withEndMargin: Boolean = true): ViewReadThemeCardBinding {
        return ViewReadThemeCardBinding.inflate(
            LayoutInflater.from(context),
            binding.llFontSampleRow,
            false
        ).also { card ->
            card.themeCardPreview.updateLayoutParams<ViewGroup.LayoutParams> {
                height = 82.dpToPx()
            }
            val params = LinearLayout.LayoutParams(
                96.dpToPx(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (withEndMargin) {
                    marginEnd = 8.dpToPx()
                }
            }
            binding.llFontSampleRow.addView(card.root, params)
        }
    }
    private val pageAnimSampleBindings by lazy {
        listOf(
            PageAnimSample(newPageAnimSampleCard(), R.string.btn_default_s, null),
            PageAnimSample(newPageAnimSampleCard(), R.string.page_anim_cover, PageAnim.coverPageAnim),
            PageAnimSample(newPageAnimSampleCard(), R.string.page_anim_linked_cover, PageAnim.linkedCoverPageAnim),
            PageAnimSample(newPageAnimSampleCard(), R.string.page_anim_slide, PageAnim.slidePageAnim),
            PageAnimSample(newPageAnimSampleCard(), R.string.page_anim_simulation, PageAnim.simulationPageAnim),
            PageAnimSample(newPageAnimSampleCard(), R.string.page_anim_scroll, PageAnim.scrollPageAnim),
            PageAnimSample(newPageAnimSampleCard(false), R.string.page_anim_none, PageAnim.noAnim)
        )
    }

    private fun newPageAnimSampleCard(withEndMargin: Boolean = true): ViewReadThemeCardBinding {
        return ViewReadThemeCardBinding.inflate(
            LayoutInflater.from(context),
            binding.llPageAnimCardRow,
            false
        ).also { card ->
            card.themeCardPreview.updateLayoutParams<ViewGroup.LayoutParams> {
                height = 82.dpToPx()
            }
            val params = LinearLayout.LayoutParams(
                96.dpToPx(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (withEndMargin) {
                    marginEnd = 8.dpToPx()
                }
            }
            binding.llPageAnimCardRow.addView(card.root, params)
        }
    }

    private val backgroundSampleBindings by lazy {
        listOf(
            BackgroundSample(binding.backgroundCardBeach, "午后沙滩.jpg"),
            BackgroundSample(binding.backgroundCardNight, "宁静夜色.jpg"),
            BackgroundSample(binding.backgroundCardGreen, "护眼漫绿.jpg"),
            BackgroundSample(binding.backgroundCardInk, "山水墨影.jpg"),
            BackgroundSample(binding.backgroundCardParchment, "羊皮纸4.jpg"),
            BackgroundSample(binding.backgroundCardNewParchment, "新羊皮纸.jpg"),
            BackgroundSample(binding.backgroundCardParchment1, "羊皮纸1.jpg"),
            BackgroundSample(binding.backgroundCardParchment2, "羊皮纸2.jpg"),
            BackgroundSample(binding.backgroundCardParchment3, "羊皮纸3.jpg"),
            BackgroundSample(binding.backgroundCardFresh, "清新时光.jpg"),
            BackgroundSample(binding.backgroundCardPalace, "深宫魅影.jpg"),
            BackgroundSample(binding.backgroundCardCanvas, "边彩画布.jpg"),
            BackgroundSample(binding.backgroundCardLandscape, "山水画.jpg"),
            BackgroundSample(binding.backgroundCardBright, "明媚倾城.jpg")
        )
    }
    private val backgroundColorCardBinding by lazy {
        ViewReadBackgroundCardBinding.bind(
            binding.llBackgroundImageRow.getChildAt(0)
        )
    }
    private val themePresets by lazy { ReadMenuThemePreset.defaultPresets() }
    private val themeCardBindings by lazy {
        listOf(
            binding.themeCardFollowSystem
        )
    }
    private val dynamicThemeCardBindings = mutableListOf<ViewReadThemeCardBinding>()
    private val menuTopIn: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_top_in)
    }
    private val menuTopOut: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_top_out)
    }
    private val menuBottomIn: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_bottom_in)
    }
    private val menuBottomOut: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_bottom_out)
    }
    private val panelIn: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_panel_in_up)
    }
    private val panelOut: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_panel_out_down)
    }
    private val bottomTabIndicatorInterpolator = DecelerateInterpolator(1.8f)
    private val bottomTabSlideInterpolator = DecelerateInterpolator(1.4f)
    private val bottomTabPulseInterpolator = AccelerateDecelerateInterpolator()
    private val hideBottomTabIndicatorRunnable = Runnable {
        fadeBottomTabIndicator()
    }
    private val immersiveMenu: Boolean
        get() = AppConfig.readBarStyleFollowPage && ReadBookConfig.durConfig.curBgType() == 0
    private var bgColor: Int = if (immersiveMenu) {
        kotlin.runCatching {
            Color.parseColor(ReadBookConfig.durConfig.curBgStr())
        }.getOrDefault(context.bottomBackground)
    } else {
        context.bottomBackground
    }
    private var textColor: Int = if (immersiveMenu) {
        ReadBookConfig.durConfig.curTextColor()
    } else {
        context.getPrimaryTextColor(ColorUtils.isColorLight(bgColor))
    }

    private var bottomBackgroundList: ColorStateList = Selector.colorBuild()
        .setDefaultColor(bgColor)
        .setPressedColor(ColorUtils.darkenColor(bgColor))
        .create()
    private var onMenuOutEnd: (() -> Unit)? = null
    private val sourceMenu by lazy {
        PopupMenu(context, binding.tvSourceAction).apply {
            inflate(R.menu.book_read_source)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_login -> callBack.showLogin()
                    R.id.menu_chapter_pay -> callBack.payAction()
                    R.id.menu_edit_source -> callBack.openSourceEditActivity()
                    R.id.menu_disable_source -> callBack.disableSource()
                }
                true
            }
        }
    }
    private val menuInListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            updateTopBarSourceAction()
            updateTopBarLoginAction()
            callBack.upSystemUiVisibility()
            configureTopBarFrostedGlass()
        }

        @SuppressLint("RtlHardcoded")
        override fun onAnimationEnd(animation: Animation) {
            binding.vwMenuBg.setOnClickListener { handleBackgroundDismiss() }
            callBack.upSystemUiVisibility()
            if (!LocalConfig.readMenuHelpVersionIsLast) {
                callBack.showHelp()
            }
        }

        override fun onAnimationRepeat(animation: Animation) = Unit
    }
    private val menuOutListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            isMenuOutAnimating = true
            binding.vwMenuBg.setOnClickListener(null)
        }

        override fun onAnimationEnd(animation: Animation) {
            this@ReadMenu.invisible()
            binding.titleBarShell.invisible()
            binding.bottomMenu.invisible()
            binding.layoutMarginAdjustOverlay.gone()
            clearLayoutAdjustBackdrop()
            binding.flExpandedPanel.gone()
            binding.flExpandedPanel.alpha = 0f
            setBottomTabBarHeight(bottomTabCollapsedHeight())
            activeBottomTab = null
            bottomTabSelectionAnchor = null
            switchBottomTabMode(BottomTabMode.Primary, animate = false)
            renderBottomTabState()
            canShowMenu = false
            isMenuOutAnimating = false
            onMenuOutEnd?.invoke()
            callBack.upSystemUiVisibility()
        }

        override fun onAnimationRepeat(animation: Animation) = Unit
    }

    init {
        binding.titleBar.applyStatusBarPadding(withInitialPadding = true)
        initView()
        upBrightnessState()
        bindEvent()
    }

    private fun initView() = binding.run {
        initAnimation()
        setupExpandableBottomTabContainer()
        setupSearchPanel()
        setupTocPanel()
        setupAloudPanel()
        setupTopBarLoginAction()
        val topBarTextColor = textColor
        configureCompactReadToolbar()
        val additionTextColor = if (immersiveMenu) {
            ColorUtils.withAlpha(ColorUtils.lightenColor(topBarTextColor), 0.75f)
        } else {
            topBarTextColor
        }
        titleBar.setTextColor(topBarTextColor)
        titleBar.setBackgroundColor(Color.TRANSPARENT)
        applyTopBarIconColor()
        tvChapterName.setTextColor(additionTextColor)
        tvChapterUrl.setTextColor(additionTextColor)
        tvSourceAction.gone()
        if (AppConfig.isEInkMode) {
            titleBarGlassView.gone()
            titleBarShellOverlay.gone()
            titleBar.setBackgroundResource(R.drawable.bg_eink_border_bottom)
        } else {
            configureTopBarFrostedGlass()
        }
        flExpandedPanel.background = null
        flExpandedPanel.elevation = 0f
        tvPanelTocCount.setTextColor(textColor)
        taggedText("tv_panel_bookmarks_empty").setTextColor(ColorUtils.adjustAlpha(textColor, 0.62f))
        tvPanelSearchTitle.setTextColor(textColor)
        taggedText("tv_panel_aloud_title").setTextColor(textColor)
        taggedText("tv_aloud_prev_chapter").setTextColor(textColor)
        taggedText("tv_aloud_next_chapter").setTextColor(textColor)
        taggedText("tv_aloud_timer").setTextColor(textColor)
        taggedText("tv_aloud_tts_speed").setTextColor(textColor)
        taggedText("tv_aloud_tts_speed_value").setTextColor(textColor)
        taggedSwitch("cb_aloud_tts_follow_sys").setTextColor(textColor)
        taggedText("panel_aloud_catalog").setTextColor(textColor)
        taggedText("panel_aloud_setting").setTextColor(textColor)
        taggedText("panel_aloud_backstage").setTextColor(textColor)
        taggedText("tv_panel_settings_title").setTextColor(textColor)
        tvPanelLayoutTitle.setTextColor(textColor)
        tvPanelBackgroundTitle.setTextColor(textColor)
        tvPanelThemeTitle.setTextColor(textColor)
        tvPanelPageTurnTitle.setTextColor(textColor)
        tvPanelMoreTitle.setTextColor(textColor)
        tvPageAnimCardsTitle.setTextColor(textColor)
        panelPageAutoPage.setTextColor(textColor)
        panelPageTouchSlop.setTextColor(textColor)
        panelPageAnimSpeed.setTextColor(textColor)
        panelPageVolumeKey.setTextColor(textColor)
        panelPageMouseWheel.setTextColor(textColor)
        panelMoreSearch.setTextColor(textColor)
        panelMoreAutoPage.setTextColor(textColor)
        panelMoreReplace.setTextColor(textColor)
        panelMoreSettings.setTextColor(textColor)
        listOf(
            taggedImageButton("iv_aloud_play_prev"),
            taggedImageButton("iv_aloud_play_pause"),
            taggedImageButton("iv_aloud_stop"),
            taggedImageButton("iv_aloud_play_next"),
            taggedImageButton("iv_aloud_timer"),
            taggedImageButton("iv_aloud_tts_speech_reduce"),
            taggedImageButton("iv_aloud_tts_speech_add")
        ).forEach { it.setColorFilter(textColor) }
        tintLayoutPanel(textColor)
        tintThemePanel(textColor)
        tintSearchPanel(textColor)
        tintBackgroundPanel(textColor)
        tintPageTurnPanel(textColor)
        renderLayoutPanel()
        renderBottomTabState()
        updateLayoutControlsFromConfig()
        updatePageTurnControls()
        updateThemeControlsFromConfig()
        updateThemePresetCards()
        updateFontSampleCards()
        updateBackgroundSampleCards()
        updateBackgroundControlsFromConfig()
        updateAloudControls()
        renderSettingsPanel()
        ivBrightnessAuto.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
        seekBrightness.post {
            seekBrightness.progress = AppConfig.readBrightness
            updateBrightnessValue()
        }
        if (AppConfig.showReadTitleBarAddition) {
            titleBarAddition.visible()
        } else {
            titleBarAddition.gone()
        }
        if (!AppConfig.isEInkMode) {
            configureTopBarFrostedGlass()
        }
        /**
         * 确保视图不被导航栏遮挡
         */
        applyNavigationBarPadding()
    }

    private fun configureCompactReadToolbar() = binding.run {
        titleBar.toolbar.updateLayoutParams<ViewGroup.LayoutParams> {
            height = topBarButtonSize()
        }
        titleBarAddition.updateLayoutParams<ViewGroup.LayoutParams> {
            height = topBarButtonSize()
        }
        titleBar.toolbar.minimumHeight = topBarButtonSize()
        titleBar.toolbar.contentInsetStartWithNavigation = 0
        titleBar.toolbar.contentInsetEndWithActions = 0
        applyTopBarIconColor()
    }

    private fun topBarButtonSize(): Int {
        return resources.getDimensionPixelSize(R.dimen.read_top_bar_button_size)
    }

    private fun applyTopBarIconColor() {
        binding.titleBar.setColorFilter(Color.WHITE)
        binding.titleBar.toolbar.applyTopBarIconMetrics(Color.WHITE)
    }

    fun reset() {
        upColorConfig()
        initView()
    }

    fun refreshMenuColorFilter() {
        applyTopBarIconColor()
    }

    private fun upColorConfig() {
        bgColor = if (immersiveMenu) {
            kotlin.runCatching {
                Color.parseColor(ReadBookConfig.durConfig.curBgStr())
            }.getOrDefault(context.bottomBackground)
        } else {
            context.bottomBackground
        }
        textColor = if (immersiveMenu) {
            ReadBookConfig.durConfig.curTextColor()
        } else {
            context.getPrimaryTextColor(ColorUtils.isColorLight(bgColor))
        }
        bottomBackgroundList = Selector.colorBuild()
            .setDefaultColor(bgColor)
            .setPressedColor(ColorUtils.darkenColor(bgColor))
            .create()
    }

    fun upBrightnessState() {
        if (brightnessAuto()) {
            binding.ivBrightnessAuto.setColorFilter(context.accentColor)
            binding.seekBrightness.isEnabled = false
        } else {
            binding.ivBrightnessAuto.setColorFilter(context.buttonDisabledColor)
            binding.seekBrightness.isEnabled = true
        }
        updateBrightnessValue()
        setScreenBrightness(AppConfig.readBrightness.toFloat())
    }

    private fun updateBrightnessValue() = binding.run {
        tvLayoutBrightnessValue.text = if (brightnessAuto()) {
            context.getString(R.string.read_menu_brightness_auto)
        } else {
            "${(seekBrightness.progress * 100f / seekBrightness.max.coerceAtLeast(1)).roundToInt()}%"
        }
    }

    /**
     * 设置屏幕亮度
     */
    fun setScreenBrightness(value: Float) {
        activity?.run {
            var brightness = BRIGHTNESS_OVERRIDE_NONE
            if (!brightnessAuto() && value != BRIGHTNESS_OVERRIDE_NONE) {
                brightness = value
                if (brightness < 1f) brightness = 1f
                brightness /= 255f
            }
            val params = window.attributes
            params.screenBrightness = brightness
            window.attributes = params
        }
    }

    fun runMenuIn(anim: Boolean = !AppConfig.isEInkMode) {
        callBack.onMenuShow()
        this.visible()
        binding.titleBarShell.visible()
        binding.bottomMenu.visible()
        binding.layoutMarginAdjustOverlay.gone()
        switchBottomTabMode(BottomTabMode.Primary, animate = false)
        hideExpandedPanel(anim = false)
        bottomTabSelectionAnchor = null
        if (anim) {
            binding.titleBarShell.startAnimation(menuTopIn)
            binding.bottomMenu.startAnimation(menuBottomIn)
        } else {
            menuInListener.onAnimationStart(menuBottomIn)
            menuInListener.onAnimationEnd(menuBottomIn)
        }
    }

    fun runMenuOut(anim: Boolean = !AppConfig.isEInkMode, onMenuOutEnd: (() -> Unit)? = null) {
        if (isMenuOutAnimating) {
            return
        }
        callBack.onMenuHide()
        this.onMenuOutEnd = onMenuOutEnd
        if (this.isVisible) {
            if (anim) {
                binding.titleBarShell.startAnimation(menuTopOut)
                binding.bottomMenu.startAnimation(menuBottomOut)
            } else {
                menuOutListener.onAnimationStart(menuBottomOut)
                menuOutListener.onAnimationEnd(menuBottomOut)
            }
        }
    }

    private fun brightnessAuto(): Boolean {
        return context.getPrefBoolean("brightnessAuto", true)
    }

    private fun setupExpandableBottomTabContainer() = binding.run {
        if (bottomTabPanelAttached) {
            return@run
        }
        val oldParent = flExpandedPanel.parent as? ViewGroup
        oldParent?.removeView(flExpandedPanel)
        flExpandedPanel.background = null
        flExpandedPanel.elevation = 0f
        flExpandedPanel.alpha = 0f
        flExpandedPanel.gone()
        val panelParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        )
        bottomTabBar.addView(flExpandedPanel, 2.coerceAtMost(bottomTabBar.childCount), panelParams)
        setBottomTabBarHeight(bottomTabCollapsedHeight())
        bottomTabPanelAttached = true
    }

    private fun setupSearchPanel() = binding.run {
        if (rvPanelSearchResults.adapter == null) {
            rvPanelSearchResults.layoutManager = LinearLayoutManager(context)
            rvPanelSearchResults.adapter = searchPanelAdapter
        }
        etPanelSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                startInlineSearch(etPanelSearchQuery.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }
        btnPanelSearchSubmit.setOnClickListener {
            startInlineSearch(etPanelSearchQuery.text?.toString().orEmpty())
        }
    }

    private fun startInlineSearch(rawQuery: String) = binding.run {
        val query = rawQuery.trim()
        if (query.isBlank()) {
            searchPanelJob?.cancel()
            searchPanelResults.clear()
            searchPanelAdapter.setItems(emptyList())
            tvPanelSearchCount.text = null
            return@run
        }
        val book = ReadBook.book ?: return@run
        searchPanelJob?.cancel()
        tvPanelSearchCount.text = context.getString(R.string.loading)
        searchPanelAdapter.setItems(emptyList())
        searchPanelResults.clear()
        searchPanelJob = Coroutine.async {
            val contentProcessor = ContentProcessor.get(book.name, book.origin)
            val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
            val results = arrayListOf<SearchResult>()
            chapters.forEach { chapter ->
                currentCoroutineContext().ensureActive()
                if (!chapter.isVolume && BookHelp.hasContent(book, chapter)) {
                    val content = BookHelp.getContent(book, chapter) ?: return@forEach
                    currentCoroutineContext().ensureActive()
                    val displayTitle = when (AppConfig.chineseConverterType) {
                        1 -> ChineseUtils.t2s(chapter.title)
                        2 -> ChineseUtils.s2t(chapter.title)
                        else -> chapter.title
                    }
                    val searchableContent = contentProcessor.getContent(
                        book = book,
                        chapter = chapter,
                        content = content,
                        useReplace = SearchContentViewModel.replaceEnabled
                    ).toString()
                    inlineSearchPositions(searchableContent, query).forEachIndexed { index, position ->
                        currentCoroutineContext().ensureActive()
                        val (queryIndex, resultText) = inlineSearchResultText(
                            searchableContent,
                            position,
                            query
                        )
                        results.add(
                            SearchResult(
                                resultCount = results.size,
                                resultCountWithinChapter = index,
                                resultText = resultText,
                                chapterTitle = displayTitle,
                                query = query,
                                chapterIndex = chapter.index,
                                queryIndexInResult = queryIndex,
                                queryIndexInChapter = position,
                                isRegex = SearchContentViewModel.regexReplace
                            )
                        )
                    }
                }
            }
            results.toList()
        }.onSuccess { results ->
            searchPanelResults.clear()
            searchPanelResults.addAll(results)
            searchPanelAdapter.setItems(results)
            tvPanelSearchCount.text = context.getString(R.string.search_content_size) + ": ${results.size}"
        }.onError {
            tvPanelSearchCount.text = context.getString(R.string.search_content_size) + ": 0"
        }
    }

    private suspend fun inlineSearchPositions(content: String, query: String): List<Int> {
        val positions = arrayListOf<Int>()
        if (SearchContentViewModel.regexReplace) {
            runCatching {
                Regex(query).findAll(content).forEach { match ->
                    currentCoroutineContext().ensureActive()
                    positions.add(match.range.first)
                }
            }
        } else {
            var index = content.indexOf(query)
            while (index >= 0) {
                currentCoroutineContext().ensureActive()
                positions.add(index)
                index = content.indexOf(query, index + query.length)
            }
        }
        return positions
    }

    private fun inlineSearchResultText(
        content: String,
        queryIndexInContent: Int,
        query: String
    ): Pair<Int, String> {
        val length = 20
        val start = (queryIndexInContent - length).coerceAtLeast(0)
        val end = (queryIndexInContent + query.length + length).coerceAtMost(content.length)
        return queryIndexInContent - start to content.substring(start, end)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTocPanel() = binding.run {
        if (rvPanelToc.adapter == null) {
            rvPanelToc.layoutManager = LinearLayoutManager(context)
            rvPanelToc.adapter = tocAdapter
        }
        val bookmarkRecycler = taggedRecycler("rv_panel_bookmarks")
        if (bookmarkRecycler.adapter == null) {
            bookmarkRecycler.layoutManager = LinearLayoutManager(context)
            bookmarkRecycler.adapter = bookmarkAdapter
        }
        taggedText("tv_toc_tab_chapters").setOnClickListener {
            setTocPanelPage(TocPanelPage.Chapters)
        }
        taggedText("tv_toc_tab_bookmarks").setOnClickListener {
            setTocPanelPage(TocPanelPage.Bookmarks)
        }
        renderTocPanelPage()
        tocDragHandleIndicator.background = roundedRect(
            ColorUtils.adjustAlpha(bottomTabContentColor(), 0.26f),
            2f.dpToPx()
        )
        tocDragHandle.setOnClickListener {
            if (activeBottomTab == BottomTab.Toc && flExpandedPanel.isVisible) {
                animateTocPanelTo(if (tocPanelFullscreen) tocDefaultPanelHeight() else tocFullPanelHeight())
            }
        }
        tocDragHandle.setOnTouchListener { _, event ->
            if (activeBottomTab != BottomTab.Toc || !flExpandedPanel.isVisible) {
                return@setOnTouchListener false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    bottomTabHeightAnimator?.cancel()
                    tocDragStartY = event.rawY
                    tocDragStartPanelHeight = flExpandedPanel.height
                    tocDragHandle.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dragOffset = (tocDragStartY - event.rawY).roundToInt()
                    val targetHeight = (tocDragStartPanelHeight + dragOffset)
                        .coerceIn(tocDefaultPanelHeight(), tocFullPanelHeight())
                    setBottomTabPanelHeight(targetHeight)
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    settleTocPanelDrag()
                    tocDragHandle.parent.requestDisallowInterceptTouchEvent(false)
                    true
                }

                else -> false
            }
        }
    }

    private fun settleTocPanelDrag() {
        val currentHeight = binding.flExpandedPanel.height
            .coerceIn(tocDefaultPanelHeight(), tocFullPanelHeight())
        val thresholdHeight = tocFullscreenThresholdHeight()
        if (currentHeight >= thresholdHeight) {
            animateTocPanelTo(tocFullPanelHeight())
        } else {
            animateTocPanelTo(currentHeight)
        }
    }

    private fun tocFullscreenThresholdHeight(): Int =
        (tocDefaultPanelHeight() + tocFullPanelHeight()) / 2

    private fun setTocPanelPage(page: TocPanelPage) {
        if (tocPanelPage == page) {
            return
        }
        tocPanelPage = page
        renderTocPanelPage()
        loadTocPanel()
    }

    private fun renderTocPanelPage() = binding.run {
        val showingChapters = tocPanelPage == TocPanelPage.Chapters
        configureTocHeaderTab(taggedText("tv_toc_tab_chapters"), showingChapters)
        configureTocHeaderTab(taggedText("tv_toc_tab_bookmarks"), !showingChapters)
        rvPanelToc.gone(!showingChapters)
        taggedRecycler("rv_panel_bookmarks").gone(showingChapters)
        taggedText("tv_panel_bookmarks_empty").gone(showingChapters || bookmarkAdapter.itemCount > 0)
        if (!showingChapters) {
            tvPanelTocCount.text = bookmarkAdapter.itemCount.toString()
        }
    }

    private fun loadTocPanel() {
        renderTocPanelPage()
        if (tocPanelPage == TocPanelPage.Bookmarks) {
            loadBookmarkPanel()
            return
        }
        val book = ReadBook.book
        if (book == null) {
            tocAdapter.submit(emptyList())
            binding.tvPanelTocCount.text = null
            return
        }
        tocLoadJob?.cancel()
        tocLoadJob = Coroutine.async {
            appDb.bookChapterDao.getChapterList(book.bookUrl)
        }.onSuccess {
            tocAdapter.submit(it)
            binding.tvPanelTocCount.text = if (it.isEmpty()) {
                context.getString(R.string.chapter_list_empty)
            } else {
                "${ReadBook.durChapterIndex + 1}/${it.size}"
            }
            val currentIndex = it.indexOfFirst { chapter -> chapter.index == ReadBook.durChapterIndex }
            if (currentIndex >= 0) {
                binding.rvPanelToc.post {
                    (binding.rvPanelToc.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(currentIndex, 80.dpToPx())
                }
            }
        }
    }

    private fun loadBookmarkPanel() {
        val book = ReadBook.book
        if (book == null) {
            bookmarkAdapter.submit(emptyList())
            binding.tvPanelTocCount.text = "0"
            taggedText("tv_panel_bookmarks_empty").visible()
            return
        }
        bookmarkLoadJob?.cancel()
        bookmarkLoadJob = Coroutine.async {
            appDb.bookmarkDao.getByBook(book.name, book.author)
        }.onSuccess {
            bookmarkAdapter.submit(it)
            binding.tvPanelTocCount.text = it.size.toString()
            taggedText("tv_panel_bookmarks_empty").gone(it.isNotEmpty())
            val currentIndex = it.indexOfLast { bookmark ->
                bookmark.chapterIndex <= ReadBook.durChapterIndex
            }.coerceAtLeast(0)
            if (it.isNotEmpty()) {
                taggedRecycler("rv_panel_bookmarks").post {
                    (taggedRecycler("rv_panel_bookmarks").layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(currentIndex, 64.dpToPx())
                }
            }
        }
    }

    private fun openTocChapter(chapter: BookChapter) {
        if (chapter.isVolume) {
            return
        }
        runMenuOut {
            callBack.skipToChapter(chapter.index)
        }
    }

    private fun openBookmark(bookmark: Bookmark) {
        runMenuOut {
            ReadBook.saveCurrentBookProgress()
            ReadBook.openChapter(bookmark.chapterIndex, bookmark.chapterPos)
        }
    }


    private fun animateTocPanelTo(targetHeight: Int) {
        tocPanelFullscreen = targetHeight >= tocFullPanelHeight()
        updateBottomTabGlassLayerHeight(tocFullPanelHeight())
        animateBottomTabPanelHeight(
            targetPanelHeight = targetHeight,
            animate = !AppConfig.isEInkMode
        ) {
            renderBottomTabState()
        }
    }

    private fun setupAloudPanel() = binding.run {
        taggedImageButton("iv_aloud_play_pause").setOnClickListener {
            callBack.onClickReadAloud()
            updateAloudControls()
        }
        taggedImageButton("iv_aloud_stop").setOnClickListener {
            ReadAloud.stop(context)
            updateAloudControls()
        }
        taggedImageButton("iv_aloud_play_prev").setOnClickListener { ReadAloud.prevParagraph(context) }
        taggedImageButton("iv_aloud_play_next").setOnClickListener { ReadAloud.nextParagraph(context) }
        taggedText("tv_aloud_prev_chapter").setOnClickListener {
            ReadBook.moveToPrevChapter(upContent = true, toLast = false)
        }
        taggedText("tv_aloud_next_chapter").setOnClickListener { ReadBook.moveToNextChapter(true) }
        taggedImageButton("iv_aloud_timer").setOnClickListener {
            ReadAloud.setTimer(context, taggedSeekBar("seek_aloud_timer").progress)
        }
        taggedText("tv_aloud_timer").setOnClickListener {
            val times = intArrayOf(0, 5, 10, 15, 30, 60, 90, 180)
            val timeKeys = times.map { context.getString(R.string.timer_m, it) }
            context.selector(context.getString(R.string.set_timer), timeKeys) { _, index ->
                ReadAloud.setTimer(context, times[index])
                taggedSeekBar("seek_aloud_timer").progress = times[index]
                updateAloudTimerText(times[index])
            }
        }
        taggedSeekBar("seek_aloud_timer").setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateAloudTimerText(progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                ReadAloud.setTimer(context, seekBar.progress)
            }
        })
        taggedSwitch("cb_aloud_tts_follow_sys").setOnCheckedChangeListener { _, isChecked ->
            AppConfig.ttsFlowSys = isChecked
            updateAloudTtsSpeechRateEnabled(!isChecked)
            applyAloudTtsSpeechRate()
        }
        taggedImageButton("iv_aloud_tts_speech_reduce").setOnClickListener {
            setAloudTtsSpeechRate(AppConfig.ttsSpeechRate - 1)
        }
        taggedImageButton("iv_aloud_tts_speech_add").setOnClickListener {
            setAloudTtsSpeechRate(AppConfig.ttsSpeechRate + 1)
        }
        taggedSeekBar("seek_aloud_tts_speech_rate").setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateAloudTtsSpeechRateText(progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                setAloudTtsSpeechRate(seekBar.progress)
            }
        })
        taggedText("panel_aloud_catalog").setOnClickListener {
            showBottomPanel(BottomTab.Toc)
        }
        taggedText("panel_aloud_setting").setOnClickListener {
            activity?.showDialogFragment<ReadAloudConfigDialog>()
        }
        taggedText("panel_aloud_backstage").setOnClickListener {
            runMenuOut { activity?.finish() }
        }
    }

    private fun setupTopBarLoginAction() = binding.run {
        val loginItem = titleBar.menu.findItem(R.id.menu_login)
            ?: titleBar.menu.add(0, R.id.menu_login, 0, R.string.login).apply {
                setIcon(R.drawable.ic_lucide_link_2)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
        loginItem.setIcon(R.drawable.ic_lucide_link_2)
        loginItem.title = context.getString(R.string.login)
        applyTopBarIconColor()
        titleBar.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.menu_login) {
                callBack.showLogin()
                true
            } else {
                false
            }
        }
        updateTopBarLoginAction()
    }

    private fun updateTopBarLoginAction() = binding.run {
        val source = ReadBook.bookSource
        val hasLoginUrl = !source?.loginUrl.isNullOrBlank()
        titleBar.menu.findItem(R.id.menu_login)?.run {
            isVisible = !ReadBook.isLocalBook && source != null
            setIcon(if (hasLoginUrl) R.drawable.ic_lucide_user else R.drawable.ic_lucide_link_2)
            title = context.getString(
                if (hasLoginUrl) R.string.login else R.string.open_in_app_webview
            )
        }
        applyTopBarIconColor()
    }

    private fun updateTopBarSourceAction() = binding.run {
        if (ReadBook.isLocalBook) {
            tvSourceAction.gone()
            return@run
        }
        tvSourceAction.text =
            ReadBook.bookSource?.bookSourceName ?: context.getString(R.string.book_source)
        tvSourceAction.visible()
    }

    private fun updateAloudControls() = binding.run {
        updateAloudPlayState()
        val timer = if (BaseReadAloudService.timeMinute > 0) {
            BaseReadAloudService.timeMinute
        } else {
            AppConfig.ttsTimer
        }
        taggedSeekBar("seek_aloud_timer").progress = timer
        updateAloudTimerText(timer)
        taggedSwitch("cb_aloud_tts_follow_sys").isChecked = AppConfig.ttsFlowSys
        taggedSeekBar("seek_aloud_tts_speech_rate").progress = AppConfig.ttsSpeechRate
        updateAloudTtsSpeechRateEnabled(!AppConfig.ttsFlowSys)
        updateAloudTtsSpeechRateText(AppConfig.ttsSpeechRate)
    }

    private fun updateAloudPlayState() = binding.run {
        val isPlaying = BaseReadAloudService.isRun && !BaseReadAloudService.pause
        val iconRes = if (isPlaying) {
            R.drawable.ic_pause_24dp
        } else {
            R.drawable.ic_play_24dp
        }
        val descriptionRes = if (isPlaying) {
            R.string.pause
        } else {
            R.string.audio_play
        }
        taggedImageButton("iv_aloud_play_pause").run {
            setImageResource(iconRes)
            contentDescription = context.getString(descriptionRes)
            setColorFilter(textColor)
        }
    }

    private fun updateAloudTimerText(value: Int) {
        taggedText("tv_aloud_timer").text = context.getString(R.string.timer_m, value.coerceAtLeast(0))
    }

    private fun updateAloudTtsSpeechRateEnabled(enabled: Boolean) = binding.run {
        val speechRateSeek = taggedSeekBar("seek_aloud_tts_speech_rate")
        val reduceButton = taggedImageButton("iv_aloud_tts_speech_reduce")
        val addButton = taggedImageButton("iv_aloud_tts_speech_add")
        taggedText("tv_aloud_tts_speed_value").visible(enabled)
        speechRateSeek.isEnabled = enabled
        reduceButton.isEnabled = enabled
        addButton.isEnabled = enabled
        val alpha = if (enabled) 1f else 0.42f
        speechRateSeek.alpha = alpha
        reduceButton.alpha = alpha
        addButton.alpha = alpha
    }

    @SuppressLint("SetTextI18n")
    private fun updateAloudTtsSpeechRateText(value: Int) {
        taggedText("tv_aloud_tts_speed_value").text = ((value + 5) / 10f).toString()
    }

    private fun setAloudTtsSpeechRate(value: Int) = binding.run {
        val speechRateSeek = taggedSeekBar("seek_aloud_tts_speech_rate")
        val boundedValue = value.coerceIn(0, speechRateSeek.max)
        speechRateSeek.progress = boundedValue
        AppConfig.ttsSpeechRate = boundedValue
        applyAloudTtsSpeechRate()
    }

    private fun applyAloudTtsSpeechRate() {
        ReadAloud.upTtsSpeechRate(context)
        if (!BaseReadAloudService.pause) {
            ReadAloud.pause(context)
            ReadAloud.resume(context)
        }
        updateAloudTtsSpeechRateText(AppConfig.ttsSpeechRate)
    }

    private fun loadSettingsPanel() {
        renderSettingsPanel()
    }

    private fun renderSettingsPanel() {
        val list = taggedLinear("panel_settings_list")
        list.removeAllViews()
        addReadSettingChoice(
            list,
            R.string.screen_direction,
            PreferKey.screenOrientation,
            R.array.screen_direction_title,
            R.array.screen_direction_value,
            "0"
        )
        addReadSettingChoice(
            list,
            R.string.keep_light,
            PreferKey.keepLight,
            R.array.screen_time_out,
            R.array.screen_time_out_value,
            "0"
        )
        addReadSettingSwitch(list, R.string.pt_hide_status_bar, PreferKey.hideStatusBar)
        addReadSettingSwitch(list, R.string.pt_hide_navigation_bar, PreferKey.hideNavigationBar)
        addReadSettingSwitch(list, R.string.read_body_to_lh, PreferKey.readBodyToLh, true)
        addReadSettingSwitch(list, R.string.padding_display_cutouts, PreferKey.paddingDisplayCutouts, true)
        addReadSettingChoice(
            list,
            R.string.double_page_horizontal,
            PreferKey.doublePageHorizontal,
            R.array.double_page_title,
            R.array.double_page_value,
            "0"
        )
        addReadSettingChoice(
            list,
            R.string.progress_bar_behavior,
            PreferKey.progressBarBehavior,
            R.array.progress_bar_behavior_title,
            R.array.progress_bar_behavior_value,
            "page"
        )
        addReadSettingSwitch(list, R.string.use_zh_layout, PreferKey.useZhLayout)
        addReadSettingSwitch(list, R.string.text_full_justify, PreferKey.textFullJustify, true)
        addReadSettingSwitch(list, R.string.text_bottom_justify, PreferKey.textBottomJustify, true)
        addReadSettingSwitch(list, R.string.adapt_special_style, PreferKey.adaptSpecialStyle, true)
        addReadSettingSwitch(list, R.string.mouse_wheel_page, PreferKey.mouseWheelPage, true)
        addReadSettingSwitch(list, R.string.volume_key_page, PreferKey.volumeKeyPage, true)
        addReadSettingSwitch(list, R.string.volume_key_page_on_play, PreferKey.volumeKeyPageOnPlay)
        addReadSettingSwitch(list, R.string.key_page_on_long_press, PreferKey.keyPageOnLongPress)
        addReadSettingAction(
            list,
            R.string.page_touch_slop_title,
            context.getString(R.string.page_touch_slop_summary, AppConfig.pageTouchSlop)
        ) {
            NumberPickerDialog(context)
                .setTitle(context.getString(R.string.page_touch_slop_dialog_title))
                .setMaxValue(9999)
                .setMinValue(0)
                .setValue(AppConfig.pageTouchSlop)
                .show {
                    AppConfig.pageTouchSlop = it
                    onReadSettingChanged(PreferKey.pageTouchSlop)
                    renderSettingsPanel()
                }
        }
        addReadSettingAction(
            list,
            R.string.page_touch_click_title,
            context.getString(R.string.page_touch_click_summary, AppConfig.pageTouchClick)
        ) {
            NumberPickerDialog(context)
                .setTitle(context.getString(R.string.page_touch_click_dialog_title))
                .setMaxValue(399)
                .setMinValue(0)
                .setValue(AppConfig.pageTouchClick)
                .show {
                    AppConfig.pageTouchClick = it
                    onReadSettingChanged(PreferKey.pageTouchClick)
                    renderSettingsPanel()
                }
        }
        addReadSettingAction(
            list,
            R.string.read_menu_alpha,
            context.getString(R.string.ui_layout_alpha_value, AppConfig.readMenuAlpha)
        ) {
            NumberPickerDialog(context)
                .setTitle(context.getString(R.string.read_menu_alpha))
                .setMaxValue(100)
                .setMinValue(35)
                .setValue(AppConfig.readMenuAlpha)
                .setCustomButton(R.string.btn_default_s) {
                    AppConfig.readMenuAlpha = 100
                    onReadSettingChanged(PreferKey.readMenuAlpha)
                    renderSettingsPanel()
                }
                .show {
                    AppConfig.readMenuAlpha = it.coerceIn(35, 100)
                    onReadSettingChanged(PreferKey.readMenuAlpha)
                    renderSettingsPanel()
                }
        }
        addReadSettingSwitch(list, R.string.auto_change_source, PreferKey.autoChangeSource, true)
        addReadSettingSwitch(list, R.string.selectText, PreferKey.textSelectAble, true)
        addReadSettingSwitch(list, R.string.no_anim_scroll_page, PreferKey.noAnimScrollPage)
        addReadSettingChoice(
            list,
            R.string.click_image_way,
            PreferKey.clickImgWay,
            R.array.click_image_way_title,
            R.array.click_image_way_value,
            "0"
        )
        if (CanvasRecorderFactory.isSupport) {
            addReadSettingSwitch(list, R.string.enable_optimize_render, PreferKey.optimizeRender)
        }
        addReadSettingAction(list, R.string.click_regional_config) {
            (activity as? BaseReadBookActivity)?.showClickRegionalConfig()
        }
        addReadSettingSwitch(list, R.string.disable_return_key, "disableReturnKey")
        addReadSettingAction(list, R.string.custom_page_key) {
            PageKeyDialog(context).show()
        }
        addReadSettingSwitch(list, R.string.expand_text_menu, PreferKey.expandTextMenu)
        addReadSettingAction(
            list,
            R.string.content_select_menu_config,
            context.getString(R.string.content_select_menu_config_summary)
        ) {
            activity?.showDialogFragment(ContentSelectMenuConfigDialog())
        }
        addReadSettingSwitch(list, R.string.show_read_title_addition, PreferKey.showReadTitleAddition, true)
        addReadSettingSwitch(list, R.string.read_bar_style_follow_page, PreferKey.readBarStyleFollowPage)
    }

    private fun addReadSettingChoice(
        parent: LinearLayout,
        titleRes: Int,
        key: String,
        entriesRes: Int,
        valuesRes: Int,
        defaultValue: String
    ) {
        val entries = resources.getTextArray(entriesRes).map { it.toString() }
        val values = resources.getStringArray(valuesRes)
        val currentValue = context.getPrefString(key, defaultValue) ?: defaultValue
        val selectedIndex = values.indexOf(currentValue)
            .takeIf { it >= 0 }
            ?: values.indexOf(defaultValue).takeIf { it >= 0 }
            ?: 0
        val row = createReadSettingRow(parent)
        addReadSettingLabel(row, context.getString(titleRes))
        row.addView(readSettingValueChip(entries[selectedIndex]))
        row.setOnClickListener {
            context.selector(context.getString(titleRes), entries) { _, index ->
                context.putPrefString(key, values[index])
                onReadSettingChanged(key)
                renderSettingsPanel()
            }
        }
    }

    private fun addReadSettingSwitch(
        parent: LinearLayout,
        titleRes: Int,
        key: String,
        defaultValue: Boolean = false
    ) {
        val row = createReadSettingRow(parent)
        addReadSettingLabel(row, context.getString(titleRes))
        val switch = SwitchCompat(context).apply {
            isChecked = context.getPrefBoolean(key, defaultValue)
            setTextColor(textColor)
            setOnCheckedChangeListener { _, checked ->
                context.putPrefBoolean(key, checked)
                onReadSettingChanged(key)
            }
        }
        row.addView(switch)
        row.setOnClickListener {
            switch.isChecked = !switch.isChecked
        }
    }

    private fun addReadSettingAction(
        parent: LinearLayout,
        titleRes: Int,
        summary: String? = null,
        onClick: () -> Unit
    ) {
        val row = createReadSettingRow(parent)
        addReadSettingLabel(row, context.getString(titleRes), summary)
        row.addView(readSettingValueChip(">"))
        row.setOnClickListener { onClick() }
    }

    private fun createReadSettingRow(parent: LinearLayout): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 58.dpToPx()
            setPadding(14.dpToPx(), 8.dpToPx(), 12.dpToPx(), 8.dpToPx())
        }
        row.background = readSettingRowBackground()
        parent.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8.dpToPx()
            }
        )
        return row
    }

    private fun addReadSettingLabel(row: LinearLayout, title: String, summary: String? = null) {
        row.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = title
                    setTextColor(textColor)
                    textSize = 15f
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
                if (!summary.isNullOrBlank()) {
                    addView(TextView(context).apply {
                        text = summary
                        setTextColor(ColorUtils.adjustAlpha(textColor, 0.68f))
                        textSize = 12f
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                    })
                }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
    }

    private fun readSettingValueChip(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(textColor)
            textSize = 13f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            minWidth = 48.dpToPx()
            maxWidth = 148.dpToPx()
            setPadding(12.dpToPx(), 7.dpToPx(), 12.dpToPx(), 7.dpToPx())
            background = readSettingChipBackground()
        }
    }

    private fun readSettingRowBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 14f.dpToPx()
            setColor(ColorUtils.adjustAlpha(Color.WHITE, 0.08f))
            setStroke(1.dpToPx(), ColorUtils.adjustAlpha(Color.WHITE, 0.22f))
        }
    }

    private fun readSettingChipBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 12f.dpToPx()
            setColor(ColorUtils.adjustAlpha(context.accentColor, 0.30f))
        }
    }

    private fun onReadSettingChanged(key: String) {
        when (key) {
            PreferKey.readBodyToLh -> activity?.recreate()
            PreferKey.hideStatusBar -> {
                ReadBookConfig.hideStatusBar = context.getPrefBoolean(PreferKey.hideStatusBar)
                postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
            }

            PreferKey.hideNavigationBar -> {
                ReadBookConfig.hideNavigationBar = context.getPrefBoolean(PreferKey.hideNavigationBar)
                postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
            }

            PreferKey.keepLight -> postEvent(key, true)
            PreferKey.textSelectAble -> postEvent(key, context.getPrefBoolean(key))
            PreferKey.screenOrientation -> (activity as? BaseReadBookActivity)?.setOrientation()
            PreferKey.textFullJustify,
            PreferKey.textBottomJustify,
            PreferKey.useZhLayout,
            PreferKey.adaptSpecialStyle -> {
                postEvent(EventBus.UP_CONFIG, arrayListOf(5))
            }

            PreferKey.expandTextMenu -> {
                (activity as? ReadBookActivity)?.textActionMenu?.upMenu()
            }

            PreferKey.doublePageHorizontal -> {
                ChapterProvider.upLayout()
                ReadBook.loadContent(false)
            }

            PreferKey.showReadTitleAddition,
            PreferKey.readBarStyleFollowPage,
            PreferKey.readMenuAlpha -> {
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            PreferKey.progressBarBehavior -> {
                postEvent(EventBus.UP_SEEK_BAR, true)
            }

            PreferKey.noAnimScrollPage -> {
                ReadBook.callBack?.upPageAnim()
            }

            PreferKey.optimizeRender -> {
                ChapterProvider.upStyle()
                ReadBook.callBack?.upPageAnim(true)
                ReadBook.loadContent(false)
            }

            PreferKey.paddingDisplayCutouts -> {
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
        }
    }

    private fun toggleBottomTab(tab: BottomTab): Boolean {
        if (activeBottomTab == tab && binding.flExpandedPanel.isVisible) {
            hideExpandedPanel(returnToPrimary = true)
            return false
        }
        showBottomPanel(tab)
        return true
    }

    private fun showBottomPanel(tab: BottomTab) = binding.run {
        if (tab.usesPrimaryNavigation()) {
            if (bottomTabMode != BottomTabMode.Primary) {
                switchBottomTabMode(BottomTabMode.Primary, animate = false)
            }
        } else if (bottomTabMode != BottomTabMode.Interface) {
            switchBottomTabMode(BottomTabMode.Interface, animate = false)
        }
        val previousTab = activeBottomTab
        val wasExpanded = flExpandedPanel.isVisible &&
                bottomTabBar.height > bottomTabCollapsedHeight()
        activeBottomTab = tab
        bottomTabSelectionAnchor = tab
        when (tab) {
            BottomTab.Search -> Unit

            BottomTab.Toc -> {
                if (previousTab != BottomTab.Toc) {
                    tocPanelFullscreen = false
                }
                loadTocPanel()
            }

            BottomTab.Aloud -> updateAloudControls()

            BottomTab.Settings -> loadSettingsPanel()

            BottomTab.Layout -> Unit

            BottomTab.PageTurn -> updatePageTurnControls()

            BottomTab.Background,
            BottomTab.Theme -> Unit
        }
        panelSearch.gone(tab != BottomTab.Search)
        panelToc.gone(tab != BottomTab.Toc)
        taggedView("panel_aloud").gone(tab != BottomTab.Aloud)
        taggedView("panel_settings").gone(tab != BottomTab.Settings)
        panelLayout.gone(tab != BottomTab.Layout)
        panelPageTurn.gone(tab != BottomTab.PageTurn)
        panelBackground.gone(tab != BottomTab.Background)
        panelTheme.gone(tab != BottomTab.Theme)
        panelMore.gone()
        updateExpandedPanelHeight(tab)
        val glassPanelHeight = expandedPanelStableHeight()
        updateBottomTabGlassLayerHeight(glassPanelHeight)
        val panelHeight = expandedPanelTargetHeight(tab)
        flExpandedPanel.alpha = if (wasExpanded) 1f else 0f
        flExpandedPanel.visible()
        callBack.onReadMenuExpandedPanelVisibilityChanged(true)
        if (!wasExpanded) {
            setExpandedPanelHeight(0)
        }
        renderBottomTabState()
        animateBottomTabPanelHeight(
            targetPanelHeight = panelHeight,
            animate = !AppConfig.isEInkMode
        ) {
            if (activeBottomTab == tab && flExpandedPanel.isVisible) {
                flExpandedPanel.animate().cancel()
                if (wasExpanded) {
                    flExpandedPanel.alpha = 1f
                } else {
                    flExpandedPanel.animate()
                        .alpha(1f)
                        .setDuration(if (AppConfig.isEInkMode) 0L else 90L)
                        .start()
                }
            }
        }
    }

    private fun hideExpandedPanel(
        anim: Boolean = !AppConfig.isEInkMode,
        returnToPrimary: Boolean = false
    ) {
        if (!binding.flExpandedPanel.isVisible) {
            activeBottomTab = null
            bottomTabSelectionAnchor = null
            if (returnToPrimary) {
                switchBottomTabMode(BottomTabMode.Primary)
            } else {
                renderBottomTabState()
            }
            return
        }
        val closingTab = activeBottomTab
        activeBottomTab = null
        bottomTabSelectionAnchor = closingTab
        binding.flExpandedPanel.animate().cancel()
        binding.flExpandedPanel.alpha = 0f
        if (returnToPrimary) {
            switchBottomTabMode(BottomTabMode.Primary)
        } else {
            renderBottomTabState()
        }
        animateBottomTabPanelHeight(
            targetPanelHeight = 0,
            animate = anim
        ) {
            binding.flExpandedPanel.gone()
            binding.flExpandedPanel.updateLayoutParams<FrameLayout.LayoutParams> {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            clearBottomTabSelectionAnchor(closingTab)
            callBack.onReadMenuExpandedPanelVisibilityChanged(false)
        }
    }

    private fun clearBottomTabSelectionAnchor(tab: BottomTab?) {
        if (activeBottomTab == null && bottomTabSelectionAnchor == tab) {
            bottomTabSelectionAnchor = null
            renderBottomTabState()
        }
    }

    private fun handleBackgroundDismiss() {
        when {
            binding.flExpandedPanel.isVisible -> hideExpandedPanel(returnToPrimary = true)
            else -> runMenuOut()
        }
    }

    private fun renderBottomTabState() = binding.run {
        configureBottomTabFrostedGlass()
        bottomTabBar.elevation = 8f.dpToPx()
        flExpandedPanel.elevation = 0f
        applyBottomNavigationColors()
        syncBottomNavigationSelection()
        updateBottomTabIndicator()
    }

    private fun updateExpandedPanelHeight(tab: BottomTab) = binding.run {
        panelLayout.updateLayoutParams<FrameLayout.LayoutParams> {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        panelLayoutScroll.updateLayoutParams<LinearLayout.LayoutParams> {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        panelTheme.updateLayoutParams<FrameLayout.LayoutParams> {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        taggedView("panel_settings").updateLayoutParams<FrameLayout.LayoutParams> {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        if (tab == BottomTab.Layout || tab == BottomTab.Theme || tab == BottomTab.Settings) {
            applyAdaptivePanelHeight(tab, expandedPanelMaxHeight())
        }
    }

    private fun applyAdaptivePanelHeight(tab: BottomTab, maxHeight: Int) = binding.run {
        when (tab) {
            BottomTab.Layout -> {
                val measuredHeight = measureBottomPanelHeight(panelLayout, maxHeight)
                if (measuredHeight >= maxHeight) {
                    panelLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                        height = maxHeight
                    }
                    panelLayoutScroll.updateLayoutParams<LinearLayout.LayoutParams> {
                        height = maxHeight
                    }
                } else {
                    panelLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    panelLayoutScroll.updateLayoutParams<LinearLayout.LayoutParams> {
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                }
            }

            BottomTab.Theme -> {
                val measuredHeight = measureBottomPanelHeight(panelTheme, maxHeight)
                panelTheme.updateLayoutParams<FrameLayout.LayoutParams> {
                    height = if (measuredHeight >= maxHeight) {
                        maxHeight
                    } else {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                }
            }

            BottomTab.Settings -> {
                val panelChromeHeight = 76.dpToPx()
                val targetHeight = 520.dpToPx()
                    .coerceAtMost(maxHeight)
                    .coerceAtLeast(280.dpToPx().coerceAtMost(maxHeight))
                taggedView("panel_settings").updateLayoutParams<FrameLayout.LayoutParams> {
                    height = targetHeight
                }
                taggedView("panel_settings_scroll").updateLayoutParams<LinearLayout.LayoutParams> {
                    height = (targetHeight - panelChromeHeight).coerceAtLeast(180.dpToPx())
                }
            }

            BottomTab.Search,
            BottomTab.Toc,
            BottomTab.Aloud,
            BottomTab.PageTurn,
            BottomTab.Background -> Unit
        }
    }

    private fun expandedPanelMaxHeight(): Int {
        val rootHeight = binding.vwMenuRoot.height
            .takeIf { it > 0 }
            ?: resources.displayMetrics.heightPixels
        val bottomBarHeight = bottomTabCollapsedHeight()
        val topGap = 16.dpToPx()
        val bottomPadding = binding.bottomMenu.paddingBottom
        val screenCap = (rootHeight - bottomBarHeight - bottomPadding - topGap)
            .coerceAtLeast(96.dpToPx())
        val minHeight = 180.dpToPx().coerceAtMost(screenCap)
        val belowTitle = rootHeight - binding.titleBarShell.bottom - topGap -
                bottomBarHeight - bottomPadding
        return belowTitle.coerceIn(minHeight, screenCap)
    }

    private fun tocDefaultPanelHeight(): Int {
        return 360.dpToPx()
            .coerceAtMost(tocFullPanelHeight())
            .coerceAtLeast(220.dpToPx().coerceAtMost(tocFullPanelHeight()))
    }

    private fun tocFullPanelHeight(): Int {
        val rootHeight = binding.vwMenuRoot.height
            .takeIf { it > 0 }
            ?: resources.displayMetrics.heightPixels
        val bottomBarHeight = bottomTabCollapsedHeight()
        val bottomPadding = binding.bottomMenu.paddingBottom
        return (rootHeight - bottomBarHeight - bottomPadding - 8.dpToPx())
            .coerceAtLeast(tocDefaultMinHeight())
    }

    private fun tocDefaultMinHeight(): Int = 220.dpToPx()

    private fun bottomTabCollapsedHeight(): Int = 56.dpToPx()

    private fun expandedPanelTargetHeight(tab: BottomTab): Int {
        if (tab == BottomTab.Search) {
            return tocDefaultPanelHeight()
        }
        if (tab == BottomTab.Toc) {
            return if (tocPanelFullscreen) {
                tocFullPanelHeight()
            } else {
                tocDefaultPanelHeight()
            }
        }
        val maxHeight = expandedPanelMaxHeight()
        val panelView = selectedBottomPanelView(tab)
        val measuredHeight = measureBottomPanelHeight(panelView, maxHeight)
        return measuredHeight.coerceIn(120.dpToPx(), maxHeight)
    }

    private fun expandedPanelStableHeight(): Int = binding.run {
        val maxHeight = expandedPanelMaxHeight()
        val currentBottomTab = activeBottomTab

        panelToc.gone()
        panelSearch.gone()
        taggedView("panel_aloud").visible()
        val aloudHeight = measureBottomPanelHeight(taggedView("panel_aloud"), maxHeight)
        taggedView("panel_aloud").gone()
        taggedView("panel_settings").visible()
        applyAdaptivePanelHeight(BottomTab.Settings, maxHeight)
        val settingsHeight = measureBottomPanelHeight(taggedView("panel_settings"), maxHeight)
        taggedView("panel_settings").gone()
        panelLayout.visible()
        panelTheme.gone()
        val layoutHeight = measureBottomPanelHeight(panelLayout, maxHeight)

        panelLayout.gone()
        panelPageTurn.visible()
        val pageTurnHeight = measureBottomPanelHeight(panelPageTurn, maxHeight)
        panelPageTurn.gone()
        panelBackground.visible()
        val backgroundHeight = measureBottomPanelHeight(panelBackground, maxHeight)
        panelBackground.gone()
        panelTheme.visible()
        val themeHeight = measureBottomPanelHeight(panelTheme, maxHeight)

        panelTheme.gone()
        panelMore.gone()
        val tocHeight = tocFullPanelHeight()
        val searchHeight = tocDefaultPanelHeight()

        panelSearch.gone(currentBottomTab != BottomTab.Search)
        panelToc.gone(currentBottomTab != BottomTab.Toc)
        taggedView("panel_aloud").gone(currentBottomTab != BottomTab.Aloud)
        taggedView("panel_settings").gone(currentBottomTab != BottomTab.Settings)
        panelLayout.gone(currentBottomTab != BottomTab.Layout)
        panelPageTurn.gone(currentBottomTab != BottomTab.PageTurn)
        panelBackground.gone(currentBottomTab != BottomTab.Background)
        panelTheme.gone(currentBottomTab != BottomTab.Theme)
        panelMore.gone()

        maxOf(
            layoutHeight,
            pageTurnHeight,
            backgroundHeight,
            themeHeight,
            aloudHeight,
            settingsHeight,
            searchHeight,
            tocHeight
        )
            .coerceIn(120.dpToPx(), tocFullPanelHeight())
    }

    private fun selectedBottomPanelView(tab: BottomTab): View {
        return when (tab) {
            BottomTab.Search -> binding.panelSearch
            BottomTab.Toc -> binding.panelToc
            BottomTab.Aloud -> taggedView("panel_aloud")
            BottomTab.Settings -> taggedView("panel_settings")
            BottomTab.Layout -> binding.panelLayout
            BottomTab.PageTurn -> binding.panelPageTurn
            BottomTab.Background -> binding.panelBackground
            BottomTab.Theme -> binding.panelTheme
        }
    }

    private fun setBottomTabPanelHeight(height: Int) {
        setExpandedPanelHeight(height)
        setBottomTabBarHeight(bottomTabCollapsedHeight() + height)
    }

    private fun measureBottomPanelHeight(view: View, maxHeight: Int): Int {
        val width = binding.bottomTabBar.width
            .takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
        view.measure(widthSpec, heightSpec)
        return view.measuredHeight
    }

    private fun setExpandedPanelHeight(height: Int) = binding.flExpandedPanel.run {
        val targetHeight = height.coerceAtLeast(0)
        val params = layoutParams as? FrameLayout.LayoutParams
        if (params?.height == targetHeight) {
            return@run
        }
        updateLayoutParams<FrameLayout.LayoutParams> {
            this.height = targetHeight
        }
    }

    private fun setBottomTabBarHeight(height: Int) = binding.bottomTabBar.run {
        val targetHeight = height.coerceAtLeast(bottomTabCollapsedHeight())
        val params = layoutParams as? FrameLayout.LayoutParams
        if (params?.height == targetHeight) {
            return@run
        }
        updateLayoutParams<FrameLayout.LayoutParams> {
            this.height = targetHeight
        }
    }

    private fun updateBottomTabGlassLayerHeight(panelHeight: Int) = binding.run {
        val targetHeight = bottomTabCollapsedHeight() + panelHeight.coerceAtLeast(0)
        if (bottomTabGlassLayerHeight == targetHeight) {
            return@run
        }
        bottomTabGlassLayerHeight = targetHeight
        setBottomTabGlassLayerChildHeight(bottomTabGlassView, targetHeight)
        setBottomTabGlassLayerChildHeight(bottomTabShellOverlay, targetHeight)
    }

    private fun setBottomTabGlassLayerChildHeight(view: View, height: Int) {
        view.updateLayoutParams<FrameLayout.LayoutParams> {
            this.height = height
            gravity = Gravity.BOTTOM
        }
    }

    private fun animateBottomTabBarHeight(
        targetHeight: Int,
        animate: Boolean,
        onEnd: () -> Unit
    ) = binding.run {
        bottomTabHeightAnimator?.cancel()
        val startHeight = bottomTabBar.height
            .takeIf { it > 0 }
            ?: bottomTabCollapsedHeight()
        if (!animate || startHeight == targetHeight) {
            if (startHeight != targetHeight) {
                setBottomTabBarHeight(targetHeight)
            }
            onEnd()
            return@run
        }
        bottomTabHeightAnimator = ValueAnimator.ofInt(startHeight, targetHeight).apply {
            duration = 220L
            interpolator = bottomTabSlideInterpolator
            addUpdateListener { animator ->
                setBottomTabBarHeight(animator.animatedValue as Int)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) {
                        setBottomTabBarHeight(targetHeight)
                        onEnd()
                    }
                    if (bottomTabHeightAnimator == this@apply) {
                        bottomTabHeightAnimator = null
                    }
                }
            })
            start()
        }
    }

    private fun animateBottomTabPanelHeight(
        targetPanelHeight: Int,
        animate: Boolean,
        onEnd: () -> Unit
    ) = binding.run {
        bottomTabHeightAnimator?.cancel()
        val startPanelHeight = flExpandedPanel.height.takeIf { it >= 0 } ?: 0
        if (!animate || startPanelHeight == targetPanelHeight) {
            setBottomTabPanelHeight(targetPanelHeight)
            onEnd()
            return@run
        }
        bottomTabHeightAnimator = ValueAnimator.ofInt(startPanelHeight, targetPanelHeight).apply {
            duration = 220L
            interpolator = bottomTabSlideInterpolator
            addUpdateListener { animator ->
                setBottomTabPanelHeight(animator.animatedValue as Int)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) {
                        setBottomTabPanelHeight(targetPanelHeight)
                        onEnd()
                    }
                    if (bottomTabHeightAnimator == this@apply) {
                        bottomTabHeightAnimator = null
                    }
                }
            })
            start()
        }
    }

    private fun updateBottomTabIndicator() = binding.run {
        val tab = activeBottomTab
        if (tab == null) {
            hideBottomTabIndicator()
            return@run
        }
        if (tab.usesPrimaryNavigation()) {
            showBottomTabIndicator(
                nav = readBottomPrimaryNav,
                itemId = tab.menuItemId(),
                animate = true,
                autoHide = false
            )
            return@run
        }
        showBottomTabIndicator(
            nav = readBottomInterfaceNav,
            itemId = tab.menuItemId(),
            animate = true,
            autoHide = false
        )
    }

    private fun applyBottomNavigationColors() = binding.run {
        val selectedContentColor = if (activeBottomTab == null) {
            bottomTabContentColor()
        } else {
            bottomTabSelectedContentColor()
        }
        val selectedLabelColor = if (activeBottomTab == null) {
            bottomTabContentColor()
        } else {
            bottomTabSelectedLabelColor()
        }
        val iconColors = Selector.colorBuild()
            .setDefaultColor(bottomTabContentColor())
            .setPressedColor(Color.WHITE)
            .setSelectedColor(selectedContentColor)
            .setCheckedColor(selectedContentColor)
            .create()
        val textColors = Selector.colorBuild()
            .setDefaultColor(bottomTabContentColor())
            .setPressedColor(Color.WHITE)
            .setSelectedColor(selectedLabelColor)
            .setCheckedColor(selectedLabelColor)
            .create()
        listOf(readBottomPrimaryNav, readBottomInterfaceNav).forEach { nav ->
            nav.itemIconTintList = iconColors
            nav.itemTextColor = textColors
            nav.itemRippleColor = null
        }
    }

    private fun syncBottomNavigationSelection() = binding.run {
        val selectedTab = activeBottomTab ?: bottomTabSelectionAnchor
        setBottomNavigationSelection(
            readBottomPrimaryNav,
            when (selectedTab) {
                BottomTab.Search,
                BottomTab.Toc,
                BottomTab.Aloud,
                BottomTab.Settings -> selectedTab.menuItemId()

                else -> null
            }
        )
        setBottomNavigationSelection(
            readBottomInterfaceNav,
            if (selectedTab?.usesPrimaryNavigation() == true) {
                null
            } else {
                selectedTab?.menuItemId()
            }
        )
    }

    private fun setBottomNavigationSelection(nav: BottomNavigationView, itemId: Int?) {
        val wasSelectedItemId = checkedBottomNavigationItemId(nav)
        suppressBottomNavSelection = true
        try {
            if (itemId == null) {
                clearBottomNavigationSelection(nav)
            } else {
                clearBottomNavigationSelection(nav)
                nav.menu.findItem(itemId)?.isChecked = true
                animateBottomNavigationSelectedItem(nav, itemId, wasSelectedItemId != itemId)
            }
        } finally {
            suppressBottomNavSelection = false
        }
    }

    private fun clearBottomNavigationSelection(nav: BottomNavigationView) {
        nav.menu.setGroupCheckable(0, true, false)
        for (index in 0 until nav.menu.size()) {
            nav.menu.getItem(index).isChecked = false
        }
        nav.menu.setGroupCheckable(0, true, true)
        resetBottomNavigationItemAnimations(nav)
    }

    private fun checkedBottomNavigationItemId(nav: BottomNavigationView): Int? {
        for (index in 0 until nav.menu.size()) {
            val item = nav.menu.getItem(index)
            if (item.isChecked) {
                return item.itemId
            }
        }
        return null
    }

    private fun animateBottomNavigationSelectedItem(
        nav: BottomNavigationView,
        itemId: Int,
        changed: Boolean
    ) {
        if (!changed || AppConfig.isEInkMode) return
        val itemView = findBottomNavigationItemView(nav, itemId) ?: return
        val contentContainer = itemView.findViewById<View>(
            com.google.android.material.R.id.navigation_bar_item_content_container
        ) ?: return
        val labelsGroup = itemView.findViewById<View>(
            com.google.android.material.R.id.navigation_bar_item_labels_group
        )
        itemView.doOnPreDraw {
            val labelHeight = labelsGroup?.height?.takeIf { it > 0 } ?: 0
            val startOffset = (labelHeight * 0.46f)
                .coerceIn(8.dpToPx().toFloat(), 18.dpToPx().toFloat())
            contentContainer.animate().cancel()
            contentContainer.translationY = startOffset
            contentContainer.animate()
                .translationY(0f)
                .setDuration(180L)
                .setInterpolator(bottomTabIndicatorInterpolator)
                .start()
            labelsGroup?.animate()?.cancel()
            labelsGroup?.alpha = 0f
            labelsGroup?.animate()
                ?.alpha(1f)
                ?.setDuration(120L)
                ?.setInterpolator(bottomTabPulseInterpolator)
                ?.start()
        }
    }

    private fun resetBottomNavigationItemAnimations(nav: BottomNavigationView) {
        val menuView = nav.getChildAt(0) as? ViewGroup ?: return
        for (index in 0 until menuView.childCount) {
            val itemView = menuView.getChildAt(index)
            itemView.findViewById<View>(
                com.google.android.material.R.id.navigation_bar_item_content_container
            )?.run {
                animate().cancel()
                translationY = 0f
            }
            itemView.findViewById<View>(
                com.google.android.material.R.id.navigation_bar_item_labels_group
            )?.run {
                animate().cancel()
                alpha = 1f
            }
        }
    }

    private fun flashBottomTabIndicator(nav: BottomNavigationView, itemId: Int) {
        showBottomTabIndicator(nav, itemId, animate = true, autoHide = false)
    }

    private fun showBottomTabIndicator(
        nav: BottomNavigationView,
        itemId: Int,
        animate: Boolean,
        autoHide: Boolean
    ) = binding.run {
        val requestToken = nextBottomTabIndicatorRequestToken()
        bottomTabIndicatorContainer.removeCallbacks(hideBottomTabIndicatorRunnable)
        bottomTabIndicatorContainer.animate().cancel()
        bottomTabIndicatorContainer.post {
            if (requestToken != bottomTabIndicatorRequestToken ||
                !isBottomTabIndicatorRequestCurrent(nav, itemId)
            ) {
                return@post
            }
            val itemView = findBottomNavigationItemView(nav, itemId) ?: return@post
            val menuView = nav.getChildAt(0) as? ViewGroup ?: return@post
            val maxWidth = 68.dpToPx()
            val minWidth = 48.dpToPx()
            val horizontalInset = 4.dpToPx()
            val targetWidth = minOf(
                maxWidth,
                (itemView.width - horizontalInset).coerceAtLeast(minWidth)
            )
            bottomTabIndicatorContainer.updateLayoutParams<FrameLayout.LayoutParams> {
                width = targetWidth
            }
            val targetX = bottomTabNavViewport.x +
                    nav.x +
                    nav.translationX +
                    menuView.x +
                    itemView.x +
                    (itemView.width - targetWidth) / 2f
            val firstPlacement = bottomTabIndicatorContainer.alpha <= 0f ||
                    !bottomTabIndicatorContainer.isVisible
            bottomTabIndicatorContainer.visible()
            if (!animate || firstPlacement || AppConfig.isEInkMode) {
                bottomTabIndicatorContainer.x = targetX
                bottomTabIndicatorContainer.alpha = 1f
                bottomTabIndicatorContainer.scaleX = 1f
                bottomTabIndicatorContainer.scaleY = 1f
            } else if (abs(bottomTabIndicatorContainer.x - targetX) >= 0.5f) {
                bottomTabIndicatorContainer.animate()
                    .x(targetX)
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220L)
                    .setInterpolator(bottomTabIndicatorInterpolator)
                    .start()
            }
            if (autoHide) {
                bottomTabIndicatorContainer.postDelayed(hideBottomTabIndicatorRunnable, 520L)
            }
        }
    }

    private fun nextBottomTabIndicatorRequestToken(): Int {
        bottomTabIndicatorRequestToken += 1
        return bottomTabIndicatorRequestToken
    }

    private fun isBottomTabIndicatorRequestCurrent(nav: BottomNavigationView, itemId: Int): Boolean {
        val tab = activeBottomTab ?: return false
        val expectedNav = if (tab.usesPrimaryNavigation()) {
            binding.readBottomPrimaryNav
        } else {
            binding.readBottomInterfaceNav
        }
        return nav === expectedNav && itemId == tab.menuItemId()
    }

    private fun hideBottomTabIndicator() = binding.run {
        nextBottomTabIndicatorRequestToken()
        bottomTabIndicatorContainer.removeCallbacks(hideBottomTabIndicatorRunnable)
        bottomTabIndicatorContainer.animate().cancel()
        if (!bottomTabIndicatorContainer.isVisible) {
            bottomTabIndicatorContainer.alpha = 0f
            return@run
        }
        if (AppConfig.isEInkMode) {
            bottomTabIndicatorContainer.alpha = 0f
            bottomTabIndicatorContainer.invisible()
            return@run
        }
        fadeBottomTabIndicator()
    }

    private fun fadeBottomTabIndicator() = binding.run {
        bottomTabIndicatorContainer.animate().cancel()
        bottomTabIndicatorContainer.animate()
            .alpha(0f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(140L)
            .setInterpolator(bottomTabPulseInterpolator)
            .withEndAction {
                if (activeBottomTab == null) {
                    bottomTabIndicatorContainer.invisible()
                }
            }
            .start()
    }

    private fun findBottomNavigationItemView(nav: BottomNavigationView, itemId: Int): View? {
        val menuView = nav.getChildAt(0) as? ViewGroup ?: return null
        for (index in 0 until menuView.childCount) {
            val child = menuView.getChildAt(index)
            if (child.id == itemId && child.visibility == View.VISIBLE) {
                return child
            }
        }
        return null
    }

    private fun switchBottomTabMode(
        mode: BottomTabMode,
        animate: Boolean = !AppConfig.isEInkMode
    ) = binding.run {
        val wasMode = bottomTabMode
        bottomTabMode = mode
        renderBottomTabState()
        val width = bottomTabNavViewport.width.takeIf { it > 0 }
            ?: bottomTabBar.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        readBottomPrimaryNav.animate().cancel()
        readBottomInterfaceNav.animate().cancel()
        if (!animate || wasMode == mode || width <= 0) {
            readBottomPrimaryNav.translationX = if (mode == BottomTabMode.Primary) 0f else -width.toFloat()
            readBottomInterfaceNav.translationX =
                if (mode == BottomTabMode.Interface) 0f else width.toFloat()
            readBottomPrimaryNav.visible(mode == BottomTabMode.Primary)
            readBottomInterfaceNav.visible(mode == BottomTabMode.Interface)
            return@run
        }
        when (mode) {
            BottomTabMode.Primary -> {
                readBottomPrimaryNav.visible()
                readBottomPrimaryNav.translationX = -width.toFloat()
                readBottomInterfaceNav.visible()
                readBottomInterfaceNav.translationX = 0f
                readBottomPrimaryNav.animate()
                    .translationX(0f)
                    .setDuration(220L)
                    .setInterpolator(bottomTabSlideInterpolator)
                    .start()
                readBottomInterfaceNav.animate()
                    .translationX(width.toFloat())
                    .setDuration(220L)
                    .setInterpolator(bottomTabSlideInterpolator)
                    .withEndAction {
                        if (bottomTabMode == BottomTabMode.Primary) {
                            readBottomInterfaceNav.invisible()
                        }
                    }
                    .start()
            }

            BottomTabMode.Interface -> {
                readBottomPrimaryNav.visible()
                readBottomPrimaryNav.translationX = 0f
                readBottomInterfaceNav.visible()
                readBottomInterfaceNav.translationX = width.toFloat()
                readBottomPrimaryNav.animate()
                    .translationX(-width.toFloat())
                    .setDuration(220L)
                    .setInterpolator(bottomTabSlideInterpolator)
                    .withEndAction {
                        if (bottomTabMode == BottomTabMode.Interface) {
                            readBottomPrimaryNav.invisible()
                        }
                    }
                    .start()
                readBottomInterfaceNav.animate()
                    .translationX(0f)
                    .setDuration(220L)
                    .setInterpolator(bottomTabSlideInterpolator)
                    .start()
            }
        }
    }

    private fun BottomTab.menuItemId(): Int {
        return when (this) {
            BottomTab.Search -> R.id.menu_read_search
            BottomTab.Toc -> R.id.menu_read_toc
            BottomTab.Aloud -> R.id.menu_read_aloud
            BottomTab.Settings -> R.id.menu_read_settings
            BottomTab.Layout -> R.id.menu_read_layout
            BottomTab.PageTurn -> R.id.menu_read_page_turn
            BottomTab.Background -> R.id.menu_read_background
            BottomTab.Theme -> R.id.menu_read_theme
        }
    }

    private fun Int.toBottomTab(): BottomTab? {
        return when (this) {
            R.id.menu_read_search -> BottomTab.Search
            R.id.menu_read_toc -> BottomTab.Toc
            R.id.menu_read_aloud -> BottomTab.Aloud
            R.id.menu_read_settings -> BottomTab.Settings
            R.id.menu_read_layout -> BottomTab.Layout
            R.id.menu_read_page_turn -> BottomTab.PageTurn
            R.id.menu_read_background -> BottomTab.Background
            R.id.menu_read_theme -> BottomTab.Theme
            else -> null
        }
    }

    private fun BottomTab.usesPrimaryNavigation(): Boolean {
        return when (this) {
            BottomTab.Search,
            BottomTab.Toc,
            BottomTab.Aloud,
            BottomTab.Settings -> true

            BottomTab.Layout,
            BottomTab.PageTurn,
            BottomTab.Background,
            BottomTab.Theme -> false
        }
    }

    private fun tintLayoutPanel(color: Int) = binding.run {
        listOf(
            tvLayoutBrightnessLabel,
            tvLayoutBrightnessValue,
            tvLayoutFontTitle,
            tvLayoutTextSpacingTitle,
            tvLayoutPageMarginTitle,
            tvLayoutTitleSettingsTitle,
            tvLayoutTitleSizeLabel,
            tvLayoutTitleTopSpacingLabel,
            tvLayoutTitleBottomSpacingLabel,
            tvLayoutHeaderShowValue,
            tvLayoutFooterShowValue,
            tvLayoutTipColorLabel,
            tvLayoutTipColorValue,
            tvLayoutTipDividerColorLabel,
            tvLayoutTipDividerColorValue,
            tvLayoutLetterSpacingLabel,
            tvLayoutLineSpacingLabel,
            tvLayoutParagraphSpacingLabel,
            tvLayoutPaddingTopLabel,
            tvLayoutPaddingBottomLabel,
            tvLayoutPaddingLeftLabel,
            tvLayoutPaddingRightLabel,
            tvLayoutMarginAdjustTitle,
            tvLayoutTextStyleEntry,
            tvLayoutTextStyleEntryValue,
            tvLayoutBodyMarginEntry,
            tvLayoutBodyMarginEntryValue,
            tvLayoutTitleMarginEntry,
            tvLayoutTitleMarginEntryValue,
            tvLayoutHeaderMarginEntryValue,
            tvLayoutFooterMarginEntryValue,
            tvLayoutHeaderTitle,
            tvLayoutHeaderLineToggle,
            tvLayoutHeaderTipItemsTitle,
            tvLayoutHeaderTipLeftValue,
            tvLayoutHeaderTipMiddleValue,
            tvLayoutHeaderTipRightValue,
            tvLayoutTitleSizeValue,
            tvLayoutTitleTopSpacingValue,
            tvLayoutTitleBottomSpacingValue,
            tvLayoutHeaderPaddingTopValue,
            tvLayoutHeaderPaddingBottomValue,
            tvLayoutFooterTitle,
            tvLayoutFooterLineToggle,
            tvLayoutFooterTipItemsTitle,
            tvLayoutFooterTipLeftValue,
            tvLayoutFooterTipMiddleValue,
            tvLayoutFooterTipRightValue,
            tvLayoutFooterPaddingTopValue,
            tvLayoutFooterPaddingBottomValue,
            tvLayoutLetterSpacingValue,
            tvLayoutLineSpacingValue,
            tvLayoutParagraphSpacingValue,
            tvLayoutPaddingTopValue,
            tvLayoutPaddingBottomValue,
            tvLayoutPaddingLeftValue,
            tvLayoutPaddingRightValue
        ).forEach { it.setTextColor(color) }
        listOf(
            ivBrightnessAuto,
            ivLayoutHeaderTitle,
            ivLayoutFooterTitle,
            ivLayoutTextStyleEntry,
            ivLayoutBodyMarginEntry,
            ivLayoutTitleMarginEntry,
            ivLayoutTipDividerColorArrow
        ).forEach { it.setColorFilter(color, PorterDuff.Mode.SRC_IN) }
        tintDescendantText(layoutTipControls, color)
        tintDescendantText(layoutTextStyleControls, color)
        listOf(
            btnLayoutMarginAdjustClose,
            btnLayoutPaddingTopIncrease,
            btnLayoutPaddingTopDecrease,
            btnLayoutPaddingBottomIncrease,
            btnLayoutPaddingBottomDecrease,
            btnLayoutPaddingLeftIncrease,
            btnLayoutPaddingLeftDecrease,
            btnLayoutPaddingRightIncrease,
            btnLayoutPaddingRightDecrease
        ).forEach { it.setColorFilter(color, PorterDuff.Mode.SRC_IN) }
        listOf(
            layoutMarginSpinboxTopField,
            layoutMarginSpinboxBottomField,
            layoutMarginSpinboxLeftField,
            layoutMarginSpinboxRightField
        ).forEach {
            it.background = roundedRect(
                ColorUtils.adjustAlpha(color, 0.04f),
                10f.dpToPx(),
                1.dpToPx(),
                ColorUtils.adjustAlpha(context.accentColor, 0.24f)
            )
        }
        layoutMarginAdjustPreview.setAccentColor(context.accentColor)
        configureLayoutAdjustFrostedGlass()
        listOf(
            tvLayoutTitleModeLeft,
            tvLayoutTitleModeCenter,
            tvLayoutTitleModeAdvanced,
            tvLayoutTitleModeHide,
            tvLayoutMarginTitleModeLeft,
            tvLayoutMarginTitleModeCenter,
            tvLayoutMarginTitleModeAdvanced,
            tvLayoutMarginTitleModeHide
        ).forEach { it.setTextColor(color) }
    }

    private fun tintPageTurnPanel(color: Int) = binding.run {
        listOf(
            tvPanelPageTurnTitle,
            tvPageAnimCardsTitle,
            panelPageAutoPage,
            panelPageTouchSlop,
            panelPageAnimSpeed,
            panelPageVolumeKey,
            panelPageMouseWheel
        ).forEach { it.setTextColor(color) }
    }

    private fun tintDescendantText(view: View, color: Int) {
        if (view is TextView) {
            view.setTextColor(color)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                tintDescendantText(view.getChildAt(index), color)
            }
        }
    }

    private fun tintThemePanel(color: Int) = binding.run {
        listOf(
            tvThemeFontWeightLabel,
            tvThemeTextSizeLabel,
            tvThemeFontWeightValue,
            tvThemeTextSizeValue
        ).forEach { it.setTextColor(color) }
    }

    private fun tintSearchPanel(color: Int) = binding.run {
        listOf(
            tvPanelSearchTitle,
            tvPanelSearchCount,
            etPanelSearchQuery
        ).forEach { it.setTextColor(color) }
        btnPanelSearchSubmit.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        searchInputPanel.background = roundedRect(
            ColorUtils.adjustAlpha(color, 0.06f),
            16f.dpToPx(),
            1.dpToPx(),
            ColorUtils.adjustAlpha(color, 0.14f)
        )
    }

    private fun tintBackgroundPanel(color: Int) = binding.run {
        listOf(
            tvPanelBackgroundTitle,
            tvBackgroundBrightnessLabel,
            tvBackgroundSaturationLabel,
            tvBackgroundAlphaLabel,
            tvBackgroundBrightnessValue,
            tvBackgroundSaturationValue,
            tvBackgroundAlphaValue
        ).forEach { it.setTextColor(color) }
        backgroundTonePanel.background = roundedRect(
            ColorUtils.adjustAlpha(color, 0.06f),
            14f.dpToPx(),
            1.dpToPx(),
            ColorUtils.adjustAlpha(color, 0.14f)
        )
    }

    private fun renderLayoutPanel() = binding.run {
        tvPanelLayoutTitle.setText(
            R.string.compose_type
        )
        panelLayoutFont.visible()
        panelLayoutSpacing.visible()
        panelLayoutStyle.visible()
    }

    private fun updateLayoutControlsFromConfig() = binding.run {
        val letterSpacingProgress = (ReadBookConfig.letterSpacing * 10f)
            .roundToInt()
            .coerceIn(0, seekLayoutLetterSpacing.max)
        seekLayoutLetterSpacing.progress = letterSpacingProgress
        updateLayoutDecimalValue(tvLayoutLetterSpacingValue, letterSpacingProgress)

        val lineSpacingProgress = ReadBookConfig.lineSpacingExtra
            .coerceIn(0, seekLayoutLineSpacing.max)
        seekLayoutLineSpacing.progress = lineSpacingProgress
        updateLayoutDecimalValue(tvLayoutLineSpacingValue, lineSpacingProgress)

        val paragraphSpacingProgress = ReadBookConfig.paragraphSpacing
            .coerceIn(0, seekLayoutParagraphSpacing.max)
        seekLayoutParagraphSpacing.progress = paragraphSpacingProgress
        updateLayoutDecimalValue(tvLayoutParagraphSpacingValue, paragraphSpacingProgress)

        val paddingTopProgress = ReadBookConfig.paddingTop.coerceIn(0, seekLayoutPaddingTop.max)
        seekLayoutPaddingTop.progress = paddingTopProgress
        updateLayoutIntValue(tvLayoutPaddingTopValue, paddingTopProgress)

        val paddingBottomProgress = ReadBookConfig.paddingBottom.coerceIn(0, seekLayoutPaddingBottom.max)
        seekLayoutPaddingBottom.progress = paddingBottomProgress
        updateLayoutIntValue(tvLayoutPaddingBottomValue, paddingBottomProgress)

        val paddingLeftProgress = ReadBookConfig.paddingLeft.coerceIn(0, seekLayoutPaddingLeft.max)
        seekLayoutPaddingLeft.progress = paddingLeftProgress
        updateLayoutIntValue(tvLayoutPaddingLeftValue, paddingLeftProgress)

        val paddingRightProgress = ReadBookConfig.paddingRight.coerceIn(0, seekLayoutPaddingRight.max)
        seekLayoutPaddingRight.progress = paddingRightProgress
        updateLayoutIntValue(tvLayoutPaddingRightValue, paddingRightProgress)
        updateLayoutMarginEntryValues()
        updateLayoutMarginPreview()

        seekLayoutTitleSize.progress = ReadBookConfig.titleSize.coerceIn(0, seekLayoutTitleSize.max)
        updateLayoutIntValue(tvLayoutTitleSizeValue, seekLayoutTitleSize.progress)
        seekLayoutTitleTopSpacing.progress = ReadBookConfig.titleTopSpacing
            .coerceIn(0, seekLayoutTitleTopSpacing.max)
        updateLayoutIntValue(tvLayoutTitleTopSpacingValue, seekLayoutTitleTopSpacing.progress)
        seekLayoutTitleBottomSpacing.progress = ReadBookConfig.titleBottomSpacing
            .coerceIn(0, seekLayoutTitleBottomSpacing.max)
        updateLayoutIntValue(tvLayoutTitleBottomSpacingValue, seekLayoutTitleBottomSpacing.progress)
        updateTitleModeButtons()

        seekLayoutHeaderPaddingTop.progress = ReadBookConfig.headerPaddingTop
            .coerceIn(0, seekLayoutHeaderPaddingTop.max)
        updateLayoutIntValue(tvLayoutHeaderPaddingTopValue, seekLayoutHeaderPaddingTop.progress)
        seekLayoutHeaderPaddingBottom.progress = ReadBookConfig.headerPaddingBottom
            .coerceIn(0, seekLayoutHeaderPaddingBottom.max)
        updateLayoutIntValue(tvLayoutHeaderPaddingBottomValue, seekLayoutHeaderPaddingBottom.progress)
        configureOptionButton(tvLayoutHeaderLineToggle, ReadBookConfig.showHeaderLine)

        seekLayoutFooterPaddingTop.progress = ReadBookConfig.footerPaddingTop
            .coerceIn(0, seekLayoutFooterPaddingTop.max)
        updateLayoutIntValue(tvLayoutFooterPaddingTopValue, seekLayoutFooterPaddingTop.progress)
        seekLayoutFooterPaddingBottom.progress = ReadBookConfig.footerPaddingBottom
            .coerceIn(0, seekLayoutFooterPaddingBottom.max)
        updateLayoutIntValue(tvLayoutFooterPaddingBottomValue, seekLayoutFooterPaddingBottom.progress)
        configureOptionButton(tvLayoutFooterLineToggle, ReadBookConfig.showFooterLine)
        updateTipSettingValues()
        updateLayoutTextStyleEntryValue()
        updateLayoutMarginEntryValues()
        updateLayoutMarginPreview()
    }

    private fun updateLayoutDecimalValue(view: TextView, progress: Int) {
        view.text = String.format(Locale.US, "%.1f", progress / 10f)
    }

    private fun updateLayoutIntValue(view: TextView, progress: Int) {
        view.text = progress.toString()
    }

    private fun updateLayoutMarginPreview() = binding.run {
        val margins = currentLayoutMarginValues()
        val showHorizontal = activeLayoutMarginAdjustMode == LayoutMarginAdjustMode.Body
        val showTextStyleMode = activeLayoutMarginAdjustMode == LayoutMarginAdjustMode.Text
        val showTitleMode = activeLayoutMarginAdjustMode == LayoutMarginAdjustMode.Title
        val showHeaderMode = activeLayoutMarginAdjustMode == LayoutMarginAdjustMode.Header
        val showFooterMode = activeLayoutMarginAdjustMode == LayoutMarginAdjustMode.Footer
        val showTipMode = showHeaderMode || showFooterMode
        val showVerticalSpinboxes = !showTextStyleMode
        val useTitleStackLayout = showTitleMode

        layoutMarginAdjustPreview.setAccentColor(context.accentColor)
        layoutMarginAdjustPreview.setMode(
            when (activeLayoutMarginAdjustMode) {
                LayoutMarginAdjustMode.Body -> ReadMarginPreviewView.Mode.Body
                LayoutMarginAdjustMode.Text -> ReadMarginPreviewView.Mode.Text
                LayoutMarginAdjustMode.Title -> ReadMarginPreviewView.Mode.Title
                LayoutMarginAdjustMode.Header -> ReadMarginPreviewView.Mode.Header
                LayoutMarginAdjustMode.Footer -> ReadMarginPreviewView.Mode.Footer
            }
        )
        layoutMarginAdjustPreview.setTitleSize(seekLayoutTitleSize.progress)
        layoutMarginAdjustPreview.setTitleMode(ReadBookConfig.titleMode)
        layoutMarginAdjustPreview.setMargins(
            margins.top,
            margins.bottom,
            margins.left,
            margins.right
        )
        updateLayoutIntValue(tvLayoutPaddingTopValue, margins.top)
        updateLayoutIntValue(tvLayoutPaddingBottomValue, margins.bottom)
        updateLayoutIntValue(tvLayoutPaddingLeftValue, margins.left)
        updateLayoutIntValue(tvLayoutPaddingRightValue, margins.right)
        layoutMarginAdjustPreview.visible(!showTextStyleMode)
        layoutMarginSpinboxTop.visible(showVerticalSpinboxes)
        layoutMarginSpinboxBottom.visible(showVerticalSpinboxes)
        layoutMarginSpinboxLeft.visible(showHorizontal)
        layoutMarginSpinboxRight.visible(showHorizontal)
        syncLayoutMarginSpinboxLayout(showHorizontal, useTitleStackLayout)
        layoutTextStyleControls.gone(!showTextStyleMode)
        layoutMarginTitleSize.gone(!showTitleMode)
        llLayoutMarginTitleMode.gone(!showTitleMode)
        layoutTipControls.gone(!showTipMode)
        layoutTipHeaderControls.gone(!showHeaderMode)
        layoutTipFooterControls.gone(!showFooterMode)
        layoutTipHeaderPaddingControls.gone(!showHeaderMode)
        layoutTipFooterPaddingControls.gone(!showFooterMode)
        syncLayoutAdjustPopupMetrics()
    }

    private fun syncLayoutAdjustPopupMetrics() = binding.run {
        syncLayoutAdjustPreviewBias()
        setExactHeight(layoutMarginAdjustPreviewHost, layoutAdjustPreviewHostHeight())
        val contentHeight = measureLayoutAdjustContentHeight()
        setExactHeight(layoutMarginAdjustPanel, contentHeight)
        setLayoutAdjustGlassLayerHeight(contentHeight)
    }

    private fun syncLayoutAdjustPreviewBias() = binding.run {
        val targetBias = when (activeLayoutMarginAdjustMode) {
            LayoutMarginAdjustMode.Body -> 0.50f
            LayoutMarginAdjustMode.Header,
            LayoutMarginAdjustMode.Footer -> 0f

            else -> 0.08f
        }
        layoutMarginAdjustPreview.updateLayoutParams<ConstraintLayout.LayoutParams> {
            verticalBias = targetBias
        }
    }

    private fun syncLayoutMarginSpinboxLayout(
        useBodyCrossLayout: Boolean,
        useTitleStackLayout: Boolean
    ) = binding.run {
        val unset = ConstraintLayout.LayoutParams.UNSET
        val previewId = R.id.layout_margin_adjust_preview
        if (useBodyCrossLayout) {
            layoutMarginAdjustPreview.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToTop = ConstraintSet.PARENT_ID
                bottomToBottom = ConstraintSet.PARENT_ID
                topToBottom = unset
                bottomToTop = unset
                topMargin = 0
                bottomMargin = 0
            }
            layoutMarginSpinboxTop.updateLayoutParams<ConstraintLayout.LayoutParams> {
                startToStart = previewId
                endToEnd = previewId
                topToTop = ConstraintSet.PARENT_ID
                bottomToTop = R.id.layout_margin_adjust_preview
                startToEnd = unset
                endToStart = unset
                topToBottom = unset
                bottomToBottom = unset
                horizontalBias = 0.5f
                verticalBias = 1f
                marginStart = 0
                marginEnd = 0
                topMargin = 0
                bottomMargin = 8.dpToPx()
            }
            layoutMarginSpinboxBottom.updateLayoutParams<ConstraintLayout.LayoutParams> {
                startToStart = previewId
                endToEnd = previewId
                topToBottom = R.id.layout_margin_adjust_preview
                bottomToBottom = ConstraintSet.PARENT_ID
                startToEnd = unset
                endToStart = unset
                topToTop = unset
                bottomToTop = unset
                horizontalBias = 0.5f
                verticalBias = 0f
                marginStart = 0
                marginEnd = 0
                topMargin = 8.dpToPx()
                bottomMargin = 0
            }
            layoutMarginSpinboxLeft.updateLayoutParams<ConstraintLayout.LayoutParams> {
                endToStart = R.id.layout_margin_adjust_preview
                topToTop = previewId
                bottomToBottom = previewId
                startToStart = unset
                startToEnd = unset
                endToEnd = unset
                topToBottom = unset
                bottomToTop = unset
                verticalBias = 0.5f
                marginStart = 0
                marginEnd = 10.dpToPx()
                topMargin = 0
                bottomMargin = 0
            }
            layoutMarginSpinboxRight.updateLayoutParams<ConstraintLayout.LayoutParams> {
                startToEnd = R.id.layout_margin_adjust_preview
                topToTop = previewId
                bottomToBottom = previewId
                startToStart = unset
                endToStart = unset
                endToEnd = unset
                topToBottom = unset
                bottomToTop = unset
                verticalBias = 0.5f
                marginStart = 10.dpToPx()
                marginEnd = 0
                topMargin = 0
                bottomMargin = 0
            }
            layoutMarginTitleSize.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToBottom = R.id.layout_margin_spinbox_bottom
                topMargin = 8.dpToPx()
            }
            llLayoutMarginTitleMode.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToBottom = R.id.layout_margin_title_size
                topMargin = 8.dpToPx()
            }
        } else if (useTitleStackLayout) {
            layoutMarginSpinboxTop.updateLayoutParams<ConstraintLayout.LayoutParams> {
                startToStart = ConstraintSet.PARENT_ID
                endToStart = R.id.layout_margin_spinbox_bottom
                topToTop = ConstraintSet.PARENT_ID
                startToEnd = unset
                endToEnd = unset
                topToBottom = unset
                bottomToTop = unset
                bottomToBottom = unset
                horizontalBias = 0.5f
                verticalBias = 0f
                marginStart = 0
                marginEnd = 10.dpToPx()
                topMargin = 0
                bottomMargin = 0
            }
            layoutMarginSpinboxBottom.updateLayoutParams<ConstraintLayout.LayoutParams> {
                startToEnd = R.id.layout_margin_spinbox_top
                endToEnd = ConstraintSet.PARENT_ID
                topToTop = ConstraintSet.PARENT_ID
                startToStart = unset
                endToStart = unset
                topToBottom = unset
                bottomToTop = unset
                bottomToBottom = unset
                horizontalBias = 0.5f
                verticalBias = 0f
                marginStart = 10.dpToPx()
                marginEnd = 0
                topMargin = 0
                bottomMargin = 0
            }
            layoutMarginAdjustPreview.updateLayoutParams<ConstraintLayout.LayoutParams> {
                startToStart = ConstraintSet.PARENT_ID
                endToEnd = ConstraintSet.PARENT_ID
                topToBottom = R.id.layout_margin_spinbox_top
                bottomToTop = unset
                topToTop = unset
                bottomToBottom = unset
                topMargin = 8.dpToPx()
                bottomMargin = 0
            }
            layoutMarginTitleSize.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToBottom = R.id.layout_margin_adjust_preview
                topMargin = 10.dpToPx()
            }
            llLayoutMarginTitleMode.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToBottom = R.id.layout_margin_title_size
                topMargin = 8.dpToPx()
            }
        } else {
            layoutMarginAdjustPreview.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToTop = ConstraintSet.PARENT_ID
                bottomToBottom = ConstraintSet.PARENT_ID
                topToBottom = unset
                bottomToTop = unset
                topMargin = 0
                bottomMargin = 0
            }
            layoutMarginSpinboxTop.updateLayoutParams<ConstraintLayout.LayoutParams> {
                endToStart = previewId
                topToTop = previewId
                bottomToBottom = previewId
                startToStart = unset
                startToEnd = unset
                endToEnd = unset
                topToBottom = unset
                bottomToTop = unset
                verticalBias = 0.16f
                marginStart = 0
                marginEnd = 8.dpToPx()
                topMargin = 0
                bottomMargin = 0
            }
            layoutMarginSpinboxBottom.updateLayoutParams<ConstraintLayout.LayoutParams> {
                startToEnd = previewId
                topToTop = previewId
                bottomToBottom = previewId
                startToStart = unset
                endToStart = unset
                endToEnd = unset
                topToBottom = unset
                bottomToTop = unset
                verticalBias = 0.16f
                marginStart = 8.dpToPx()
                marginEnd = 0
                topMargin = 0
                bottomMargin = 0
            }
            layoutMarginSpinboxLeft.updateLayoutParams<ConstraintLayout.LayoutParams> {
                endToStart = previewId
                topToTop = previewId
                bottomToBottom = previewId
                startToStart = unset
                startToEnd = unset
                endToEnd = unset
                topToBottom = unset
                bottomToTop = unset
                verticalBias = 0.90f
                marginStart = 0
                marginEnd = 6.dpToPx()
                topMargin = 0
                bottomMargin = 0
            }
            layoutMarginSpinboxRight.updateLayoutParams<ConstraintLayout.LayoutParams> {
                startToEnd = previewId
                topToTop = previewId
                bottomToBottom = previewId
                startToStart = unset
                endToStart = unset
                endToEnd = unset
                topToBottom = unset
                bottomToTop = unset
                verticalBias = 0.90f
                marginStart = 6.dpToPx()
                marginEnd = 0
                topMargin = 0
                bottomMargin = 0
            }
            layoutMarginTitleSize.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToBottom = R.id.layout_margin_spinbox_bottom
                topMargin = 8.dpToPx()
            }
            llLayoutMarginTitleMode.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToBottom = R.id.layout_margin_title_size
                topMargin = 8.dpToPx()
            }
        }
    }

    private fun layoutAdjustPreviewHostHeight(): Int = binding.run {
        return when (activeLayoutMarginAdjustMode) {
            LayoutMarginAdjustMode.Text -> measureLayoutAdjustChildHeight(layoutTextStyleControls)
                .coerceAtLeast(1.dpToPx())

            LayoutMarginAdjustMode.Body -> 300.dpToPx()
            LayoutMarginAdjustMode.Title -> 352.dpToPx()
            LayoutMarginAdjustMode.Header,
            LayoutMarginAdjustMode.Footer -> {
                val tipTopMargin = (layoutTipControls.layoutParams as? ViewGroup.MarginLayoutParams)
                    ?.topMargin
                    ?: 0
                (layoutMarginAdjustPreview.layoutParams.height +
                        tipTopMargin +
                        measureLayoutAdjustChildHeight(layoutTipControls))
                    .coerceAtLeast(220.dpToPx())
            }
        }
    }

    private fun measureLayoutAdjustContentHeight(): Int = binding.run {
        val widthSpec = MeasureSpec.makeMeasureSpec(layoutAdjustPanelWidth(), MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(layoutAdjustMaxPanelHeight(), MeasureSpec.AT_MOST)
        layoutMarginAdjustContent.measure(widthSpec, heightSpec)
        return layoutMarginAdjustContent.measuredHeight.coerceAtLeast(0)
    }

    private fun measureLayoutAdjustChildHeight(view: View): Int {
        val widthSpec = MeasureSpec.makeMeasureSpec(layoutAdjustPreviewContentWidth(), MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(layoutAdjustMaxPanelHeight(), MeasureSpec.AT_MOST)
        view.measure(widthSpec, heightSpec)
        return view.measuredHeight
    }

    private fun layoutAdjustPanelWidth(): Int = binding.run {
        val overlayWidth = layoutMarginAdjustOverlay.width
            .takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        return (overlayWidth - layoutMarginAdjustOverlay.paddingLeft - layoutMarginAdjustOverlay.paddingRight)
            .coerceAtLeast(1)
    }

    private fun layoutAdjustPreviewContentWidth(): Int = binding.run {
        return (layoutAdjustPanelWidth() -
                layoutMarginAdjustContent.paddingLeft -
                layoutMarginAdjustContent.paddingRight)
            .coerceAtLeast(1)
    }

    private fun layoutAdjustMaxPanelHeight(): Int = binding.run {
        val overlayHeight = layoutMarginAdjustOverlay.height
            .takeIf { it > 0 }
            ?: resources.displayMetrics.heightPixels
        return (overlayHeight - layoutMarginAdjustOverlay.paddingTop - layoutMarginAdjustOverlay.paddingBottom)
            .coerceAtLeast(1)
    }

    private fun setExactHeight(view: View, height: Int) {
        val targetHeight = height.coerceAtLeast(0)
        if (view.layoutParams?.height == targetHeight) {
            return
        }
        view.updateLayoutParams<ViewGroup.LayoutParams> {
            this.height = targetHeight
        }
    }

    private fun setLayoutAdjustGlassLayerHeight(height: Int) = binding.run {
        val targetHeight = height.coerceAtLeast(0)
        if (layoutAdjustGlassLayerHeight == targetHeight) {
            return@run
        }
        layoutAdjustGlassLayerHeight = targetHeight
        setLayoutAdjustGlassLayerChildHeight(layoutMarginAdjustBlurBackdrop, targetHeight)
        setLayoutAdjustGlassLayerChildHeight(layoutMarginAdjustGlassView, targetHeight)
        setLayoutAdjustGlassLayerChildHeight(layoutMarginAdjustShellOverlay, targetHeight)
    }

    private fun setLayoutAdjustGlassLayerChildHeight(view: View, height: Int) {
        view.updateLayoutParams<FrameLayout.LayoutParams> {
            this.height = height
        }
    }

    private fun captureLayoutAdjustBackdrop() = binding.run {
        val target = parent as? View ?: vwMenuRoot.rootView ?: return@run
        val targetWidth = target.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val targetHeight = target.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        if (targetWidth <= 0 || targetHeight <= 0) {
            return@run
        }
        clearLayoutAdjustBackdrop()
        val scale = 0.35f
        val bitmapWidth = (targetWidth * scale).roundToInt().coerceAtLeast(1)
        val bitmapHeight = (targetHeight * scale).roundToInt().coerceAtLeast(1)
        val source = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        Canvas(source).apply {
            scale(scale, scale)
            target.draw(this)
        }
        val blurred = source.stackBlur((24f * scale).roundToInt().coerceIn(4, 25))
        if (blurred !== source) {
            source.recycle()
        }
        layoutAdjustBackdropBitmap = blurred
    }

    private fun updateLayoutAdjustBlurBackdrop() = binding.run {
        val backdrop = layoutAdjustBackdropBitmap ?: return@run
        val target = parent as? View ?: vwMenuRoot.rootView ?: return@run
        val targetWidth = target.width.takeIf { it > 0 } ?: return@run
        val targetHeight = target.height.takeIf { it > 0 } ?: return@run
        val panelWidth = layoutMarginAdjustPanel.width.takeIf { it > 0 } ?: return@run
        val panelHeight = layoutMarginAdjustPanel.height.takeIf { it > 0 } ?: return@run
        val targetLocation = IntArray(2)
        val panelLocation = IntArray(2)
        target.getLocationInWindow(targetLocation)
        layoutMarginAdjustPanel.getLocationInWindow(panelLocation)
        val scaleX = backdrop.width / targetWidth.toFloat()
        val scaleY = backdrop.height / targetHeight.toFloat()
        val left = ((panelLocation[0] - targetLocation[0]) * scaleX)
            .roundToInt()
            .coerceIn(0, backdrop.width - 1)
        val top = ((panelLocation[1] - targetLocation[1]) * scaleY)
            .roundToInt()
            .coerceIn(0, backdrop.height - 1)
        val cropWidth = (panelWidth * scaleX).roundToInt()
            .coerceAtLeast(1)
            .coerceAtMost(backdrop.width - left)
        val cropHeight = (panelHeight * scaleY).roundToInt()
            .coerceAtLeast(1)
            .coerceAtMost(backdrop.height - top)
        if (cropWidth <= 0 || cropHeight <= 0) {
            return@run
        }
        val cropped = Bitmap.createBitmap(backdrop, left, top, cropWidth, cropHeight)
        layoutAdjustBackdropCropBitmap?.recycle()
        layoutAdjustBackdropCropBitmap = cropped
        layoutMarginAdjustBlurBackdrop.scaleType = ImageView.ScaleType.FIT_XY
        layoutMarginAdjustBlurBackdrop.setImageBitmap(cropped)
    }

    private fun clearLayoutAdjustBackdrop() = binding.run {
        layoutMarginAdjustBlurBackdrop.setImageDrawable(null)
        layoutAdjustBackdropCropBitmap?.recycle()
        layoutAdjustBackdropCropBitmap = null
        layoutAdjustBackdropBitmap?.recycle()
        layoutAdjustBackdropBitmap = null
    }

    private fun updateLayoutMarginEntryValues() = binding.run {
        tvLayoutBodyMarginEntryValue.text = listOf(
            ReadBookConfig.paddingTop,
            ReadBookConfig.paddingBottom,
            ReadBookConfig.paddingLeft,
            ReadBookConfig.paddingRight
        ).joinToString(" / ")
        tvLayoutTitleMarginEntryValue.text =
            "${ReadBookConfig.titleTopSpacing} / ${ReadBookConfig.titleBottomSpacing}"
        tvLayoutHeaderMarginEntryValue.text =
            "${ReadBookConfig.headerPaddingTop} / ${ReadBookConfig.headerPaddingBottom}"
        tvLayoutFooterMarginEntryValue.text =
            "${ReadBookConfig.footerPaddingTop} / ${ReadBookConfig.footerPaddingBottom}"
    }

    private fun updateLayoutTextStyleEntryValue() = binding.run {
        tvLayoutTextStyleEntryValue.text = "${ReadBookConfig.textSize} / ${ReadBookConfig.textWeight}"
    }

    private data class LayoutMargins(
        val top: Int,
        val bottom: Int,
        val left: Int,
        val right: Int
    )

    private fun currentLayoutMarginValues(): LayoutMargins = binding.run {
        when (activeLayoutMarginAdjustMode) {
            LayoutMarginAdjustMode.Body -> LayoutMargins(
                seekLayoutPaddingTop.progress,
                seekLayoutPaddingBottom.progress,
                seekLayoutPaddingLeft.progress,
                seekLayoutPaddingRight.progress
            )

            LayoutMarginAdjustMode.Text -> LayoutMargins(
                0,
                0,
                0,
                0
            )

            LayoutMarginAdjustMode.Title -> LayoutMargins(
                seekLayoutTitleTopSpacing.progress,
                seekLayoutTitleBottomSpacing.progress,
                0,
                0
            )

            LayoutMarginAdjustMode.Header -> LayoutMargins(
                seekLayoutHeaderPaddingTop.progress,
                seekLayoutHeaderPaddingBottom.progress,
                0,
                0
            )

            LayoutMarginAdjustMode.Footer -> LayoutMargins(
                seekLayoutFooterPaddingTop.progress,
                seekLayoutFooterPaddingBottom.progress,
                0,
                0
            )
        }
    }

    private fun showLayoutMarginAdjustOverlay(mode: LayoutMarginAdjustMode) = binding.run {
        activeLayoutMarginAdjustMode = mode
        tvLayoutMarginAdjustTitle.setText(
            when (mode) {
                LayoutMarginAdjustMode.Body -> R.string.main_body
                LayoutMarginAdjustMode.Text -> R.string.read_menu_text_style
                LayoutMarginAdjustMode.Title -> R.string.title
                LayoutMarginAdjustMode.Header -> R.string.read_menu_header
                LayoutMarginAdjustMode.Footer -> R.string.read_menu_footer
            }
        )
        updateLayoutControlsFromConfig()
        hideExpandedPanel(anim = false)
        bottomMenu.gone()
        captureLayoutAdjustBackdrop()
        layoutMarginAdjustOverlay.visible()
        layoutMarginAdjustOverlay.bringToFront()
        layoutMarginAdjustPanel.post {
            configureLayoutAdjustFrostedGlass()
        }
    }

    private fun hideLayoutMarginAdjustOverlay() = binding.run {
        layoutMarginAdjustOverlay.gone()
        clearLayoutAdjustBackdrop()
        bottomMenu.visible()
        switchBottomTabMode(BottomTabMode.Interface, animate = false)
        showBottomPanel(BottomTab.Layout)
    }

    private fun updateTitleModeButtons() = binding.run {
        listOf(
            tvLayoutTitleModeLeft to (ReadBookConfig.titleMode == 0),
            tvLayoutTitleModeCenter to (ReadBookConfig.titleMode == 1),
            tvLayoutTitleModeAdvanced to (ReadBookConfig.titleMode == AdvancedTitleConfig.TITLE_MODE_ADVANCED),
            tvLayoutTitleModeHide to (ReadBookConfig.titleMode == 2),
            tvLayoutMarginTitleModeLeft to (ReadBookConfig.titleMode == 0),
            tvLayoutMarginTitleModeCenter to (ReadBookConfig.titleMode == 1),
            tvLayoutMarginTitleModeAdvanced to (ReadBookConfig.titleMode == AdvancedTitleConfig.TITLE_MODE_ADVANCED),
            tvLayoutMarginTitleModeHide to (ReadBookConfig.titleMode == 2)
        ).forEach { (view, selected) ->
            configureOptionButton(view, selected)
        }
    }

    private fun updateTipSettingValues() = binding.run {
        tvLayoutHeaderShowValue.setText(R.string.read_menu_display_auto)
        tvLayoutFooterShowValue.setText(R.string.show)
        val currentTextColor = ReadBookConfig.durConfig.curTextColor()
        tvLayoutTipColorValue.text = "#${currentTextColor.hexString}"
        vwLayoutTipColorSwatch.background = roundedRect(
            currentTextColor,
            12f.dpToPx(),
            1.dpToPx(),
            ColorUtils.adjustAlpha(textColor, 0.18f)
        )
        tvLayoutTipDividerColorValue.text = when (ReadTipConfig.tipDividerColor) {
            -1, 0 -> ReadTipConfig.tipDividerColorNames
                .getOrElse(ReadTipConfig.tipDividerColor + 1) { "" }
            else -> "#${ReadTipConfig.tipDividerColor.hexString}"
        }
        vwLayoutTipDividerColorSwatch.background = roundedRect(
            effectiveTipDividerColor(),
            13f.dpToPx(),
            1.dpToPx(),
            ColorUtils.adjustAlpha(textColor, 0.18f)
        )
        tvLayoutHeaderTipLeftValue.text = tipName(ReadTipConfig.tipHeaderLeft)
        tvLayoutHeaderTipMiddleValue.text = tipName(ReadTipConfig.tipHeaderMiddle)
        tvLayoutHeaderTipRightValue.text = tipName(ReadTipConfig.tipHeaderRight)
        tvLayoutFooterTipLeftValue.text = tipName(ReadTipConfig.tipFooterLeft)
        tvLayoutFooterTipMiddleValue.text = tipName(ReadTipConfig.tipFooterMiddle)
        tvLayoutFooterTipRightValue.text = tipName(ReadTipConfig.tipFooterRight)
        renderTipDisplayCards()
        renderTipDividerControls()
    }

    private fun tipName(tip: Int): String {
        val names = ReadTipConfig.tipNames
        val index = ReadTipConfig.tipValues.indexOf(tip)
        return names.getOrElse(index) { names.getOrElse(ReadTipConfig.none) { "" } }
    }

    private fun renderTipDisplayCards() = binding.run {
        val headerHideCard = taggedView("layout_header_display_hide_card")
        val headerHideRadio = taggedView("vw_header_display_hide_radio")
        val footerAutoCard = taggedView("layout_footer_display_auto_card")
        val footerAutoRadio = taggedView("vw_footer_display_auto_radio")
        configureTipOptionCard(
            layoutHeaderDisplayAutoCard,
            vwHeaderDisplayAutoRadio,
            ReadTipConfig.headerMode == 0
        )
        configureTipOptionCard(
            layoutHeaderDisplayShowCard,
            vwHeaderDisplayShowRadio,
            ReadTipConfig.headerMode == 1
        )
        configureTipOptionCard(
            headerHideCard,
            headerHideRadio,
            ReadTipConfig.headerMode == 2
        )
        configureTipOptionCard(
            footerAutoCard,
            footerAutoRadio,
            ReadTipConfig.footerMode == 2
        )
        configureTipOptionCard(
            layoutFooterDisplayShowCard,
            vwFooterDisplayShowRadio,
            ReadTipConfig.footerMode == 0
        )
        configureTipOptionCard(
            layoutFooterDisplayHideCard,
            vwFooterDisplayHideRadio,
            ReadTipConfig.footerMode == 1
        )
    }

    private fun taggedView(tag: String): View =
        binding.root.findViewWithTag(tag)

    private fun taggedText(tag: String): TextView =
        taggedView(tag) as TextView

    private fun taggedImageButton(tag: String): AppCompatImageButton =
        taggedView(tag) as AppCompatImageButton

    private fun taggedLinear(tag: String): LinearLayout =
        taggedView(tag) as LinearLayout

    private fun taggedRecycler(tag: String): RecyclerView =
        taggedView(tag) as RecyclerView

    private fun taggedSeekBar(tag: String): SeekBar =
        taggedView(tag) as SeekBar

    private fun taggedSwitch(tag: String): ThemeSwitch =
        taggedView(tag) as ThemeSwitch

    private fun renderTipDividerControls() = binding.run {
        val showHeaderMode = activeLayoutMarginAdjustMode == LayoutMarginAdjustMode.Header
        val showFooterMode = activeLayoutMarginAdjustMode == LayoutMarginAdjustMode.Footer
        tvLayoutHeaderLineToggle.gone(!showHeaderMode)
        tvLayoutHeaderLineHide.gone(!showHeaderMode)
        tvLayoutFooterLineToggle.gone(!showFooterMode)
        tvLayoutFooterLineHide.gone(!showFooterMode)
        configureOptionButton(tvLayoutHeaderLineToggle, ReadBookConfig.showHeaderLine)
        configureOptionButton(tvLayoutHeaderLineHide, !ReadBookConfig.showHeaderLine)
        configureOptionButton(tvLayoutFooterLineToggle, ReadBookConfig.showFooterLine)
        configureOptionButton(tvLayoutFooterLineHide, !ReadBookConfig.showFooterLine)
    }

    private fun configureTipOptionCard(card: View, radio: View, selected: Boolean) {
        card.background = roundedRect(
            if (selected) context.accentColor else ColorUtils.adjustAlpha(textColor, 0.06f),
            12f.dpToPx(),
            1.dpToPx(),
            if (selected) context.accentColor else ColorUtils.adjustAlpha(textColor, 0.14f)
        )
        tintDescendantText(card, if (selected) Color.WHITE else textColor)
        radio.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (selected) context.accentColor else Color.TRANSPARENT)
            setStroke(
                1.dpToPx(),
                if (selected) context.accentColor else ColorUtils.adjustAlpha(textColor, 0.45f)
            )
        }
    }

    private fun effectiveTipDividerColor(): Int {
        return when (ReadTipConfig.tipDividerColor) {
            -1 -> context.getCompatColor(R.color.divider)
            0 -> ReadBookConfig.textColor
            else -> ReadTipConfig.tipDividerColor
        }
    }

    private fun updatePageTurnControls() = binding.run {
        updatePageAnimSampleCards()
        configureOptionButton(panelPageAutoPage, false)
        configureOptionButton(panelPageTouchSlop, AppConfig.pageTouchSlop > 0)
        configureOptionButton(panelPageAnimSpeed, AppConfig.pageAnimationSpeed != 300)
        configureOptionButton(panelPageVolumeKey, AppConfig.volumeKeyPage)
        configureOptionButton(panelPageMouseWheel, AppConfig.mouseWheelPage)
    }

    private fun updatePageAnimSampleCards() {
        val currentAnim = ReadBookConfig.pageAnim
        pageAnimSampleBindings.forEach { sample ->
            val selected = if (sample.anim == null) {
                false
            } else {
                currentAnim == sample.anim
            }
            bindPageAnimSampleCard(sample, selected)
        }
    }

    private fun bindPageAnimSampleCard(sample: PageAnimSample, selected: Boolean) {
        val card = sample.binding
        val label = context.getString(sample.labelRes)
        val pageColor = currentBackgroundColor()
        val inkColor = ReadBookConfig.durConfig.curTextColor()
        card.themeCardPreview.background = PageAnimPreviewDrawable(
            pageColor = pageColor,
            inkColor = inkColor,
            menuTextColor = textColor,
            accentColor = context.accentColor,
            anim = sample.anim,
            selected = selected
        )
        card.tvThemeCardTitle.isGone = true
        card.tvThemeCardBody.isGone = true
        card.tvThemeCardTitle.typeface = Typeface.DEFAULT
        card.tvThemeCardBody.typeface = Typeface.DEFAULT
        card.tvThemeCardLabel.text = label
        card.tvThemeCardLabel.setTextColor(if (selected) context.accentColor else textColor)
        card.ivThemeCardCheck.background = roundedRect(context.accentColor, 13f.dpToPx())
        card.ivThemeCardCheck.isVisible = selected
        card.tvThemeCardBadge.isVisible = false
        card.root.contentDescription = label
    }

    private fun applyPageAnimSample(anim: Int?) {
        val pageAnim = anim ?: PageAnim.coverPageAnim
        ReadBookConfig.pageAnim = pageAnim
        ReadBookConfig.save()
        ReadBook.book?.setPageAnim(null)
        ReadBook.saveRead()
        ReadBook.callBack?.upPageAnim(true)
        ReadBook.loadContent(resetPageOffset = false)
        updatePageTurnControls()
        persistActiveThemeSuiteChange()
        updateThemePresetCards()
    }

    private fun updateThemeControlsFromConfig() = binding.run {
        seekThemeFontWeight.progress = ReadBookConfig.textWeight.coerceIn(0, seekThemeFontWeight.max)
        updateThemeFontWeightValue(seekThemeFontWeight.progress)

        seekThemeTextSize.progress = (ReadBookConfig.textSize - 5)
            .coerceIn(0, seekThemeTextSize.max)
        updateThemeTextSizeValue(seekThemeTextSize.progress)
        updateFontButtons()
    }

    private fun updateThemePresetCards(revealSuite: ReadMenuThemeSuite? = null) {
        val currentBgType = ReadBookConfig.durConfig.curBgType()
        val currentBg = ReadBookConfig.durConfig.curBgStr()
        val currentText = ReadBookConfig.durConfig.curTextColor()
        val savedSuites = ReadMenuThemeSuiteStore.load(context)
        val explicitSavedIndex = ReadMenuThemeSuiteStore.explicitSavedIndex(context, savedSuites)
        val selectedPresetIndex = if (explicitSavedIndex == -1) {
            themePresets.indexOfFirst { preset ->
                isThemePresetSelected(preset, currentBgType, currentBg, currentText)
            }
        } else {
            -1
        }
        val selectedSavedIndex = when {
            explicitSavedIndex != -1 -> explicitSavedIndex
            selectedPresetIndex == -1 -> ReadMenuThemeSuiteStore.selectedSavedIndex(context, savedSuites)
            else -> -1
        }
        val savedThemeCards = savedSuites.mapIndexed { savedIndex, suite ->
            ThemeSuiteCard(suite, savedIndex == selectedSavedIndex, true)
        }
        val revealIndex = savedThemeCards.indexOfFirst {
            revealSuite != null && it.suite.createdAt == revealSuite.createdAt
        }
        syncThemeSuiteCards(savedThemeCards.size, revealIndex)
        themeCardBindings.zip(themePresets).forEachIndexed { presetIndex, (card, preset) ->
            bindThemePresetCard(
                card,
                preset,
                presetIndex == selectedPresetIndex
            )
        }
        var revealCard: ViewReadThemeCardBinding? = null
        dynamicThemeCardBindings.zip(savedThemeCards).forEachIndexed { index, (card, themeCard) ->
            bindThemeSuiteCard(card, themeCard.suite, themeCard.selected, themeCard.custom)
            if (index == revealIndex) {
                revealCard = card
            }
        }
        bindThemeAddCard(binding.themeCardAdd)
        revealCard?.let { animateThemeSuiteCardReveal(it) }
    }

    private fun syncThemeSuiteCards(count: Int, revealIndex: Int = -1) = binding.run {
        dynamicThemeCardBindings.forEach { llThemePresetRow.removeView(it.root) }
        dynamicThemeCardBindings.clear()
        repeat(count) { index ->
            val card = ViewReadThemeCardBinding.inflate(LayoutInflater.from(context), llThemePresetRow, false)
            val reveal = index == revealIndex
            card.root.alpha = if (reveal) 0f else 1f
            card.root.layoutParams = LinearLayout.LayoutParams(
                if (reveal) 0 else 96.dpToPx(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = if (reveal) 0 else 8.dpToPx()
            }
            val insertIndex = llThemePresetRow.indexOfChild(themeCardAdd.root)
                .takeIf { it >= 0 }
                ?: llThemePresetRow.childCount
            llThemePresetRow.addView(card.root, insertIndex)
            dynamicThemeCardBindings.add(card)
        }
    }

    private fun animateThemeSuiteCardReveal(card: ViewReadThemeCardBinding) = binding.run {
        val targetWidth = 96.dpToPx()
        val targetMargin = 8.dpToPx()
        card.root.animate().cancel()
        card.root.alpha = 0f
        ValueAnimator.ofInt(0, targetWidth).apply {
            duration = 220L
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener { animator ->
                val width = animator.animatedValue as Int
                val progress = width / targetWidth.toFloat()
                card.root.updateLayoutParams<LinearLayout.LayoutParams> {
                    this.width = width
                    marginEnd = (targetMargin * progress).roundToInt()
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    card.root.updateLayoutParams<LinearLayout.LayoutParams> {
                        width = targetWidth
                        marginEnd = targetMargin
                    }
                }
            })
            start()
        }
        card.root.animate()
            .alpha(1f)
            .setDuration(160L)
            .setInterpolator(DecelerateInterpolator(1.4f))
            .start()
        hsvThemePresets.post {
            hsvThemePresets.smoothScrollTo(llThemePresetRow.width, 0)
        }
    }

    private fun isThemePresetSelected(
        preset: ReadMenuThemePreset,
        currentBgType: Int,
        currentBg: String,
        currentText: Int
    ): Boolean {
        return currentBgType == preset.bgType &&
                currentBg.equals(preset.bgValue, ignoreCase = true) &&
                currentText == preset.textColor &&
                ReadBookConfig.textSize == preset.textSize &&
                ReadBookConfig.textWeight == preset.textWeight &&
                ReadBookConfig.textBold == preset.textBold &&
                ReadBookConfig.textFont == preset.textFont &&
                AppConfig.systemTypefaces == preset.systemTypeface &&
                abs(ReadBookConfig.letterSpacing - preset.letterSpacing) < 0.01f &&
                ReadBookConfig.lineSpacingExtra == preset.lineSpacingExtra &&
                ReadBookConfig.paragraphSpacing == preset.paragraphSpacing &&
                ReadBookConfig.paddingTop == preset.paddingTop &&
                ReadBookConfig.paddingBottom == preset.paddingBottom &&
                ReadBookConfig.paddingLeft == preset.paddingLeft &&
                ReadBookConfig.paddingRight == preset.paddingRight &&
                ReadBookConfig.titleTopSpacing == preset.titleTopSpacing &&
                ReadBookConfig.titleBottomSpacing == preset.titleBottomSpacing &&
                ReadBookConfig.titleSize == preset.titleSize &&
                ReadBookConfig.titleMode == preset.titleMode &&
                ReadTipConfig.headerMode == preset.headerMode &&
                ReadBookConfig.headerPaddingTop == preset.headerPaddingTop &&
                ReadBookConfig.headerPaddingBottom == preset.headerPaddingBottom &&
                ReadBookConfig.headerPaddingLeft == preset.headerPaddingLeft &&
                ReadBookConfig.headerPaddingRight == preset.headerPaddingRight &&
                ReadBookConfig.showHeaderLine == preset.showHeaderLine &&
                ReadTipConfig.footerMode == preset.footerMode &&
                ReadBookConfig.footerPaddingTop == preset.footerPaddingTop &&
                ReadBookConfig.footerPaddingBottom == preset.footerPaddingBottom &&
                ReadBookConfig.footerPaddingLeft == preset.footerPaddingLeft &&
                ReadBookConfig.footerPaddingRight == preset.footerPaddingRight &&
                ReadBookConfig.showFooterLine == preset.showFooterLine &&
                ReadBookConfig.pageAnim == preset.pageAnim &&
                AppConfig.pageAnimationSpeed == preset.pageAnimationSpeed &&
                ReadBookConfig.bgBrightness == preset.bgBrightness &&
                ReadBookConfig.bgSaturation == preset.bgSaturation &&
                ReadBookConfig.bgAlpha == preset.bgAlpha &&
                ReadBookConfig.paperInkStrength == preset.paperInkStrength
    }

    private fun bindThemePresetCard(
        card: ViewReadThemeCardBinding,
        preset: ReadMenuThemePreset,
        selected: Boolean
    ) {
        card.themeCardPreview.background = roundedRect(
            preset.backgroundColor,
            12f.dpToPx(),
            if (selected) 2.dpToPx() else 1.dpToPx(),
            if (selected) context.accentColor else ColorUtils.adjustAlpha(textColor, 0.12f)
        )
        bindThemeCardPreviewText(card, preset.textColor)
        card.tvThemeCardLabel.setText(preset.labelRes)
        card.tvThemeCardLabel.setTextColor(if (selected) context.accentColor else textColor)
        card.ivThemeCardCheck.background = roundedRect(context.accentColor, 13f.dpToPx())
        card.ivThemeCardCheck.isVisible = selected
        card.tvThemeCardBadge.isVisible = false
    }

    private fun bindThemeSuiteCard(
        card: ViewReadThemeCardBinding,
        suite: ReadMenuThemeSuite,
        selected: Boolean,
        custom: Boolean
    ) {
        card.themeCardPreview.background = themeSuitePreviewBackground(suite, selected, custom)
        bindThemeCardPreviewText(card, suite.textColor)
        card.tvThemeCardLabel.text = suite.name
        card.tvThemeCardLabel.setTextColor(if (selected) context.accentColor else textColor)
        card.ivThemeCardCheck.background = roundedRect(context.accentColor, 13f.dpToPx())
        card.ivThemeCardCheck.isVisible = selected
        card.tvThemeCardBadge.background = roundedRect(
            ColorUtils.adjustAlpha(context.accentColor, 0.92f),
            9f.dpToPx()
        )
        card.tvThemeCardBadge.isVisible = custom
        card.root.setOnClickListener { applySavedThemeSuite(suite) }
        card.root.setOnLongClickListener {
            showThemeSuiteActions(suite)
            true
        }
    }

    private fun showThemeSuiteActions(suite: ReadMenuThemeSuite) {
        val actions = listOf(
            context.getString(R.string.read_menu_theme_rename),
            context.getString(R.string.delete)
        )
        context.selector(suite.name, actions) { _, index ->
            when (index) {
                0 -> renameThemeSuite(suite)
                1 -> deleteThemeSuite(suite)
            }
        }
    }

    private fun bindThemeCardPreviewText(card: ViewReadThemeCardBinding, previewTextColor: Int) {
        card.tvThemeCardTitle.isVisible = true
        card.tvThemeCardBody.isVisible = true
        card.tvThemeCardTitle.gravity = Gravity.NO_GRAVITY
        card.tvThemeCardTitle.textSize = 13f
        card.tvThemeCardTitle.includeFontPadding = true
        card.tvThemeCardTitle.text = ReadMenuThemePreset.DEFAULT_PREVIEW_TITLE
        card.tvThemeCardBody.text = ReadMenuThemePreset.DEFAULT_PREVIEW_BODY
        card.tvThemeCardTitle.setTextColor(previewTextColor)
        card.tvThemeCardBody.setTextColor(ColorUtils.adjustAlpha(previewTextColor, 0.72f))
    }

    private fun themeSuitePreviewBackground(
        suite: ReadMenuThemeSuite,
        selected: Boolean,
        custom: Boolean
    ): Drawable {
        val color = suite.backgroundColor.takeIf { it != Color.TRANSPARENT }
            ?: ColorUtils.adjustAlpha(textColor, 0.06f)
        val strokeWidth = if (selected) 2.dpToPx() else 1.dpToPx()
        val strokeColor = if (selected) {
            context.accentColor
        } else {
            ColorUtils.adjustAlpha(textColor, 0.12f)
        }
        return roundedRect(
            color,
            12f.dpToPx(),
            strokeWidth,
            strokeColor
        )
    }

    private fun bindThemeAddCard(card: ViewReadThemeCardBinding) {
        card.themeCardPreview.background = roundedRect(
            ColorUtils.adjustAlpha(textColor, 0.06f),
            12f.dpToPx(),
            1.dpToPx(),
            ColorUtils.adjustAlpha(textColor, 0.14f)
        )
        card.tvThemeCardTitle.text = "+"
        card.tvThemeCardBody.isGone = true
        card.tvThemeCardLabel.setText(R.string.read_menu_theme_add)
        card.tvThemeCardTitle.gravity = Gravity.CENTER
        card.tvThemeCardTitle.textSize = 28f
        card.tvThemeCardTitle.includeFontPadding = false
        card.tvThemeCardTitle.setTextColor(context.accentColor)
        card.tvThemeCardLabel.setTextColor(textColor)
        card.ivThemeCardCheck.isVisible = false
        card.tvThemeCardBadge.isVisible = false
        card.root.setOnClickListener { addCurrentThemeSuite() }
    }

    private fun deleteThemeSuite(suite: ReadMenuThemeSuite) {
        ReadMenuThemeSuiteStore.delete(context, suite)
        context.toastOnUi(R.string.delete_success)
        updateThemePresetCards()
    }

    fun persistActiveThemeSuiteChange() {
        if (ReadMenuThemeSuiteStore.updateActiveFromCurrent(context)) {
            updateThemePresetCards()
        }
    }

    private fun applyThemePreset(preset: ReadMenuThemePreset) {
        ReadBookConfig.durConfig.setCurBg(preset.bgType, preset.bgValue)
        ReadBookConfig.durConfig.setCurTextColor(preset.textColor)
        ReadBookConfig.textSize = preset.textSize
        ReadBookConfig.textWeight = preset.textWeight
        ReadBookConfig.textBold = preset.textBold
        ReadBookConfig.textFont = preset.textFont
        AppConfig.systemTypefaces = preset.systemTypeface
        ReadBookConfig.letterSpacing = preset.letterSpacing
        ReadBookConfig.lineSpacingExtra = preset.lineSpacingExtra
        ReadBookConfig.paragraphSpacing = preset.paragraphSpacing
        ReadBookConfig.paddingTop = preset.paddingTop
        ReadBookConfig.paddingBottom = preset.paddingBottom
        ReadBookConfig.paddingLeft = preset.paddingLeft
        ReadBookConfig.paddingRight = preset.paddingRight
        ReadBookConfig.titleTopSpacing = preset.titleTopSpacing
        ReadBookConfig.titleBottomSpacing = preset.titleBottomSpacing
        ReadBookConfig.titleSize = preset.titleSize
        ReadBookConfig.titleMode = preset.titleMode
        ReadTipConfig.headerMode = preset.headerMode
        ReadBookConfig.headerPaddingTop = preset.headerPaddingTop
        ReadBookConfig.headerPaddingBottom = preset.headerPaddingBottom
        ReadBookConfig.headerPaddingLeft = preset.headerPaddingLeft
        ReadBookConfig.headerPaddingRight = preset.headerPaddingRight
        ReadBookConfig.showHeaderLine = preset.showHeaderLine
        ReadTipConfig.footerMode = preset.footerMode
        ReadBookConfig.footerPaddingTop = preset.footerPaddingTop
        ReadBookConfig.footerPaddingBottom = preset.footerPaddingBottom
        ReadBookConfig.footerPaddingLeft = preset.footerPaddingLeft
        ReadBookConfig.footerPaddingRight = preset.footerPaddingRight
        ReadBookConfig.showFooterLine = preset.showFooterLine
        ReadBookConfig.pageAnim = preset.pageAnim
        ReadBook.book?.setPageAnim(preset.pageAnim)
        AppConfig.pageAnimationSpeed = preset.pageAnimationSpeed
        ReadBookConfig.bgBrightness = preset.bgBrightness
        ReadBookConfig.bgSaturation = preset.bgSaturation
        ReadBookConfig.bgAlpha = preset.bgAlpha
        ReadBookConfig.paperInkStrength = preset.paperInkStrength
        ReadMenuThemeSuiteStore.clearSelection(context)
        finishThemeSuiteApplied()
    }

    private fun applySavedThemeSuite(suite: ReadMenuThemeSuite) {
        suite.applyToReader()
        ReadMenuThemeSuiteStore.select(context, suite)
        finishThemeSuiteApplied()
    }

    private fun finishThemeSuiteApplied(revealSuite: ReadMenuThemeSuite? = null) {
        ReadBookConfig.save()
        postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5, 6, 9, 11))
        if (AppConfig.readBarStyleFollowPage) {
            postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
        }
        updateLayoutControlsFromConfig()
        updatePageTurnControls()
        updateThemeControlsFromConfig()
        updateBackgroundControlsFromConfig()
        updateBackgroundSampleCards()
        updateThemePresetCards(revealSuite = revealSuite)
        reset()
        if (activeBottomTab != BottomTab.Theme) {
            showBottomPanel(BottomTab.Theme)
        }
    }

    private fun addCurrentThemeSuite() {
        val name = context.getString(
            R.string.read_menu_theme_default_name,
            ReadMenuThemeSuiteStore.load(context).size + 1
        )
        val suite = ReadMenuThemeSuiteStore.captureDefaultPreset(name, themePresets.first())
        ReadMenuThemeSuiteStore.save(context, suite)
        ReadMenuThemeSuiteStore.select(context, suite)
        suite.applyToReader()
        context.toastOnUi(context.getString(R.string.read_menu_theme_saved, suite.name))
        finishThemeSuiteApplied(revealSuite = suite)
    }

    private fun renameThemeSuite(suite: ReadMenuThemeSuite) {
        context.alert(R.string.read_menu_theme_rename) {
            val alertBinding = DialogEditTextBinding.inflate(LayoutInflater.from(context)).apply {
                editView.hint = context.getString(R.string.read_menu_theme_save_name)
                editView.setSingleLine()
                editView.setText(suite.name)
                editView.setSelection(0, editView.text?.length ?: 0)
            }
            customView { alertBinding.root }
            okButton {
                val name = alertBinding.editView.text?.toString()?.trim().orEmpty()
                if (name.isBlank()) {
                    context.toastOnUi(R.string.read_menu_theme_empty_name)
                    return@okButton
                }
                val renamed = ReadMenuThemeSuiteStore.rename(context, suite, name)
                    ?: return@okButton
                ReadMenuThemeSuiteStore.select(context, renamed)
                updateThemePresetCards()
            }
            cancelButton()
        }
    }

    private fun updateThemeFontWeightValue(progress: Int) {
        val resId = when {
            progress >= 67 -> R.string.read_style_weight_bold
            progress <= 33 -> R.string.read_style_weight_light
            else -> R.string.read_style_weight_standard
        }
        binding.tvThemeFontWeightValue.text = context.getString(resId, progress)
    }

    private fun updateThemeTextSizeValue(progress: Int) {
        binding.tvThemeTextSizeValue.text = (progress + 5).toString()
    }

    private fun setSystemFont(systemTypeface: Int) {
        ReadBookConfig.textFont = ""
        AppConfig.systemTypefaces = systemTypeface
        ReadBookConfig.save()
        updateFontButtons()
        postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5))
        persistActiveThemeSuiteChange()
    }

    private fun updateFontButtons() = binding.run {
        updateFontSampleCards()
    }

    private fun updateFontSampleCards() {
        fontSampleBindings.forEach { sample ->
            bindFontSampleCard(sample, sample.isSelected())
        }
    }

    private fun bindFontSampleCard(sample: FontSample, selected: Boolean) {
        val card = sample.binding
        if (sample.titleRes == R.string.read_menu_add_custom_font) {
            bindFontAddCard(card)
            return
        }
        val previewTextColor = ReadBookConfig.durConfig.curTextColor()
        card.themeCardPreview.background = roundedRect(
            currentBackgroundColor(),
            12f.dpToPx(),
            if (selected) 2.dpToPx() else 1.dpToPx(),
            if (selected) context.accentColor else ColorUtils.adjustAlpha(textColor, 0.14f)
        )
        card.tvThemeCardTitle.text = context.getString(R.string.chapter_list)
        card.tvThemeCardBody.text = "夜色微凉，星光洒落..."
        card.tvThemeCardBody.isVisible = true
        card.tvThemeCardTitle.gravity = Gravity.NO_GRAVITY
        card.tvThemeCardTitle.textSize = 13f
        card.tvThemeCardTitle.includeFontPadding = true
        card.tvThemeCardLabel.setText(sample.titleRes)
        card.tvThemeCardTitle.typeface = sample.typeface
        card.tvThemeCardBody.typeface = sample.typeface
        card.tvThemeCardTitle.setTextColor(previewTextColor)
        card.tvThemeCardBody.setTextColor(ColorUtils.adjustAlpha(previewTextColor, 0.76f))
        card.tvThemeCardLabel.setTextColor(if (selected) context.accentColor else textColor)
        card.ivThemeCardCheck.background = roundedRect(context.accentColor, 13f.dpToPx())
        card.ivThemeCardCheck.isVisible = selected
    }

    private fun bindFontAddCard(card: ViewReadThemeCardBinding) {
        card.themeCardPreview.background = roundedRect(
            ColorUtils.adjustAlpha(textColor, 0.06f),
            12f.dpToPx(),
            1.dpToPx(),
            ColorUtils.adjustAlpha(textColor, 0.14f)
        )
        card.tvThemeCardTitle.text = "+"
        card.tvThemeCardBody.isGone = true
        card.tvThemeCardLabel.setText(R.string.read_menu_add_custom_font)
        card.tvThemeCardTitle.gravity = Gravity.CENTER
        card.tvThemeCardTitle.textSize = 28f
        card.tvThemeCardTitle.includeFontPadding = false
        card.tvThemeCardTitle.typeface = Typeface.DEFAULT
        card.tvThemeCardTitle.setTextColor(context.accentColor)
        card.tvThemeCardLabel.setTextColor(textColor)
        card.ivThemeCardCheck.isVisible = false
        card.tvThemeCardBadge.isVisible = false
    }

    private fun updateBackgroundSampleCards() {
        val currentBgType = ReadBookConfig.durConfig.curBgType()
        val currentBg = ReadBookConfig.durConfig.curBgStr()
        bindBackgroundColorCard(currentBgType == 0)
        backgroundSampleBindings.forEach { sample ->
            bindBackgroundSampleCard(
                sample,
                currentBgType == 1 && currentBg.equals(sample.assetName, ignoreCase = true)
            )
        }
    }

    private fun bindBackgroundColorCard(selected: Boolean) {
        val card = backgroundColorCardBinding
        val color = currentBackgroundColor()
        card.ivBackgroundCardImage.setImageDrawable(null)
        card.backgroundCardPreview.background = roundedRect(
            color,
            12f.dpToPx(),
            1.dpToPx(),
            ColorUtils.adjustAlpha(textColor, 0.14f)
        )
        card.backgroundCardScrim.setBackgroundColor(Color.TRANSPARENT)
        card.tvBackgroundCardLabel.setText(R.string.background_color)
        bindBackgroundCardSelectedState(card, selected)
        card.root.setOnClickListener {
            ReadBookConfig.durConfig.setCurBg(0, "#${color.hexString}")
            ReadBookConfig.save()
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 3, 5, 6, 9, 11))
            if (AppConfig.readBarStyleFollowPage) {
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            updateBackgroundSampleCards()
            persistActiveThemeSuiteChange()
            ColorPickerDialog.newBuilder()
                .setColor(color)
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(BG_COLOR)
                .show(activity)
        }
    }

    private fun bindBackgroundSampleCard(sample: BackgroundSample, selected: Boolean) {
        val card = sample.binding
        kotlin.runCatching {
            ImageLoader.load(
                context,
                context.assets.open("bg${File.separator}${sample.assetName}").readBytes()
            ).centerCrop().into(card.ivBackgroundCardImage)
        }
        card.backgroundCardPreview.background = roundedRect(
            ColorUtils.adjustAlpha(textColor, if (selected) 0.16f else 0.06f),
            12f.dpToPx(),
            if (selected) 2.dpToPx() else 1.dpToPx(),
            if (selected) context.accentColor else ColorUtils.adjustAlpha(textColor, 0.14f)
        )
        card.backgroundCardScrim.setBackgroundColor(
            ColorUtils.adjustAlpha(Color.BLACK, if (selected) 0.10f else 0.20f)
        )
        card.tvBackgroundCardLabel.text = sample.assetName.substringBeforeLast(".")
        bindBackgroundCardSelectedState(card, selected)
        card.root.setOnClickListener {
            ReadBookConfig.durConfig.setCurBg(1, sample.assetName)
            ReadBookConfig.save()
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 3, 5, 6, 9, 11))
            if (AppConfig.readBarStyleFollowPage) {
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            updateBackgroundSampleCards()
            persistActiveThemeSuiteChange()
        }
    }

    private fun bindBackgroundCardSelectedState(
        card: ViewReadBackgroundCardBinding,
        selected: Boolean
    ) {
        card.backgroundCardSelectionBorder.background = roundedRect(
            Color.TRANSPARENT,
            12f.dpToPx(),
            if (selected) 2.dpToPx() else 0,
            if (selected) context.accentColor else Color.TRANSPARENT
        )
        card.backgroundCardSelectionBorder.isVisible = selected
        card.tvBackgroundCardLabel.setTextColor(if (selected) context.accentColor else textColor)
        card.ivBackgroundCardCheck.background = roundedRect(context.accentColor, 13f.dpToPx())
        card.ivBackgroundCardCheck.isVisible = selected
    }

    private fun currentBackgroundColor(): Int {
        return if (ReadBookConfig.durConfig.curBgType() == 0) {
            kotlin.runCatching {
                Color.parseColor(ReadBookConfig.durConfig.curBgStr())
            }.getOrDefault(context.getCompatColor(R.color.background))
        } else {
            ReadBookConfig.bgMeanColor.takeIf { it != 0 }
                ?: context.getCompatColor(R.color.background)
        }
    }

    private fun updateBackgroundControlsFromConfig() = binding.run {
        seekBackgroundBrightness.progress = ReadBookConfig.bgBrightness
            .coerceIn(0, seekBackgroundBrightness.max)
        updatePercentValue(tvBackgroundBrightnessValue, seekBackgroundBrightness.progress)
        seekBackgroundSaturation.progress = ReadBookConfig.bgSaturation
            .coerceIn(0, seekBackgroundSaturation.max)
        updatePercentValue(tvBackgroundSaturationValue, seekBackgroundSaturation.progress)
        seekBackgroundAlpha.progress = ReadBookConfig.bgAlpha.coerceIn(0, seekBackgroundAlpha.max)
        updatePercentValue(tvBackgroundAlphaValue, seekBackgroundAlpha.progress)
    }

    private fun updatePercentValue(view: TextView, progress: Int) {
        view.text = "${progress.coerceIn(0, 100)}%"
    }

    private fun setFontWeight(progress: Int) = binding.run {
        val value = progress.coerceIn(0, seekThemeFontWeight.max)
        ReadBookConfig.textWeight = value
        ReadBookConfig.textBold = when {
            value >= 67 -> 1
            value <= 33 -> 2
            else -> 0
        }
        seekThemeFontWeight.progress = value
        updateThemeFontWeightValue(value)
        updateLayoutTextStyleEntryValue()
        updateLayoutMarginPreview()
        ReadBookConfig.save()
        postEvent(EventBus.UP_CONFIG, arrayListOf(8, 9, 6))
        persistActiveThemeSuiteChange()
    }

    private fun setTextSize(progress: Int) {
        ReadBookConfig.textSize = progress + 5
        updateThemeTextSizeValue(progress)
        updateLayoutTextStyleEntryValue()
        updateLayoutMarginPreview()
        ReadBookConfig.save()
        postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        persistActiveThemeSuiteChange()
    }

    private fun setLayoutLetterSpacing(progress: Int) = binding.run {
        val value = progress.coerceIn(0, seekLayoutLetterSpacing.max)
        ReadBookConfig.letterSpacing = value / 10f
        updateLayoutDecimalValue(tvLayoutLetterSpacingValue, value)
        updateLayoutTextStyleEntryValue()
        updateLayoutMarginPreview()
        ReadBookConfig.save()
        postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        persistActiveThemeSuiteChange()
    }

    private fun setLayoutLineSpacing(progress: Int) = binding.run {
        val value = progress.coerceIn(0, seekLayoutLineSpacing.max)
        ReadBookConfig.lineSpacingExtra = value
        updateLayoutDecimalValue(tvLayoutLineSpacingValue, value)
        updateLayoutTextStyleEntryValue()
        updateLayoutMarginPreview()
        ReadBookConfig.save()
        postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        persistActiveThemeSuiteChange()
    }

    private fun setLayoutParagraphSpacing(progress: Int) = binding.run {
        val value = progress.coerceIn(0, seekLayoutParagraphSpacing.max)
        ReadBookConfig.paragraphSpacing = value
        updateLayoutDecimalValue(tvLayoutParagraphSpacingValue, value)
        updateLayoutTextStyleEntryValue()
        updateLayoutMarginPreview()
        ReadBookConfig.save()
        postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        persistActiveThemeSuiteChange()
    }

    private fun setLayoutBodyPadding(progress: Int, seekBar: SeekBar, valueView: TextView, setter: (Int) -> Unit) {
        val value = progress.coerceIn(0, seekBar.max)
        setter(value)
        if (seekBar.progress != value) {
            seekBar.progress = value
        }
        updateLayoutIntValue(valueView, value)
        updateLayoutMarginEntryValues()
        updateLayoutMarginPreview()
        ReadBookConfig.save()
        postEvent(EventBus.UP_CONFIG, arrayListOf(10, 5))
        persistActiveThemeSuiteChange()
    }

    private fun setLayoutTitleSize(progress: Int) = binding.run {
        val value = progress.coerceIn(0, seekLayoutTitleSize.max)
        ReadBookConfig.titleSize = value
        if (seekLayoutTitleSize.progress != value) {
            seekLayoutTitleSize.progress = value
        }
        updateLayoutIntValue(tvLayoutTitleSizeValue, value)
        updateLayoutMarginPreview()
        ReadBookConfig.save()
        postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        persistActiveThemeSuiteChange()
    }

    private fun setLayoutTitleTopSpacing(progress: Int) = binding.run {
        val value = progress.coerceIn(0, seekLayoutTitleTopSpacing.max)
        ReadBookConfig.titleTopSpacing = value
        if (seekLayoutTitleTopSpacing.progress != value) {
            seekLayoutTitleTopSpacing.progress = value
        }
        updateLayoutIntValue(tvLayoutTitleTopSpacingValue, value)
        updateLayoutMarginEntryValues()
        updateLayoutMarginPreview()
        ReadBookConfig.save()
        postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        persistActiveThemeSuiteChange()
    }

    private fun setLayoutTitleBottomSpacing(progress: Int) = binding.run {
        val value = progress.coerceIn(0, seekLayoutTitleBottomSpacing.max)
        ReadBookConfig.titleBottomSpacing = value
        if (seekLayoutTitleBottomSpacing.progress != value) {
            seekLayoutTitleBottomSpacing.progress = value
        }
        updateLayoutIntValue(tvLayoutTitleBottomSpacingValue, value)
        updateLayoutMarginEntryValues()
        updateLayoutMarginPreview()
        ReadBookConfig.save()
        postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        persistActiveThemeSuiteChange()
    }

    private fun setLayoutTitleMode(mode: Int) {
        ReadBookConfig.titleMode = mode
        ReadBookConfig.save()
        updateTitleModeButtons()
        updateLayoutMarginPreview()
        postEvent(EventBus.UP_CONFIG, arrayListOf(5))
        persistActiveThemeSuiteChange()
    }

    private fun setLayoutTipPadding(progress: Int, seekBar: SeekBar, valueView: TextView, setter: (Int) -> Unit) {
        val value = progress.coerceIn(0, seekBar.max)
        setter(value)
        if (seekBar.progress != value) {
            seekBar.progress = value
        }
        updateLayoutIntValue(valueView, value)
        updateLayoutMarginEntryValues()
        updateLayoutMarginPreview()
        ReadBookConfig.save()
        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        persistActiveThemeSuiteChange()
    }

    private fun setHeaderDisplayMode(mode: Int) {
        ReadTipConfig.headerMode = mode
        ReadBookConfig.save()
        updateTipSettingValues()
        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        persistActiveThemeSuiteChange()
    }

    private fun setFooterDisplayMode(mode: Int) {
        ReadTipConfig.footerMode = mode
        ReadBookConfig.save()
        updateTipSettingValues()
        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        persistActiveThemeSuiteChange()
    }

    private fun showHeaderTipItemSelector(titleSource: Int, setter: (Int) -> Unit) {
        context.selector(titleSource, ReadTipConfig.tipNames) { _, i ->
            val value = ReadTipConfig.tipValues[i]
            clearRepeatTipItem(value)
            setter(value)
            ReadBookConfig.save()
            updateTipSettingValues()
            postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            persistActiveThemeSuiteChange()
        }
    }

    private fun clearRepeatTipItem(repeat: Int) = ReadTipConfig.apply {
        if (repeat != none) {
            if (tipHeaderLeft == repeat) {
                tipHeaderLeft = none
            }
            if (tipHeaderMiddle == repeat) {
                tipHeaderMiddle = none
            }
            if (tipHeaderRight == repeat) {
                tipHeaderRight = none
            }
            if (tipFooterLeft == repeat) {
                tipFooterLeft = none
            }
            if (tipFooterMiddle == repeat) {
                tipFooterMiddle = none
            }
            if (tipFooterRight == repeat) {
                tipFooterRight = none
            }
        }
    }

    private fun setHeaderDividerVisible(visible: Boolean) {
        ReadBookConfig.showHeaderLine = visible
        ReadBookConfig.save()
        updateTipSettingValues()
        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        persistActiveThemeSuiteChange()
    }

    private fun setFooterDividerVisible(visible: Boolean) {
        ReadBookConfig.showFooterLine = visible
        ReadBookConfig.save()
        updateTipSettingValues()
        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        persistActiveThemeSuiteChange()
    }

    private fun bindBackgroundSeek(
        seekBar: SeekBar,
        valueView: TextView,
        onStop: (Int) -> Unit
    ) {
        seekBar.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updatePercentValue(valueView, progress)
                if (fromUser) onStop(progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }

    private fun configureOptionButton(view: TextView, selected: Boolean) {
        view.background = roundedRect(
            if (selected) context.accentColor else ColorUtils.adjustAlpha(textColor, 0.06f),
            12f.dpToPx(),
            1.dpToPx(),
            if (selected) context.accentColor else ColorUtils.adjustAlpha(textColor, 0.14f)
        )
        view.setTextColor(if (selected) Color.WHITE else textColor)
    }

    private fun configureTocHeaderTab(view: TextView, selected: Boolean) {
        view.background = null
        view.setTextColor(if (selected) textColor else ColorUtils.adjustAlpha(textColor, 0.54f))
        view.typeface = Typeface.defaultFromStyle(if (selected) Typeface.BOLD else Typeface.NORMAL)
        view.alpha = if (selected) 1f else 0.82f
    }

    private fun roundedRect(
        color: Int,
        radius: Float,
        strokeWidth: Int = 0,
        strokeColor: Int = Color.TRANSPARENT
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            if (strokeWidth > 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }
    }

    private fun roundedRectCorners(
        color: Int,
        radius: Float,
        topLeft: Boolean,
        topRight: Boolean,
        bottomRight: Boolean,
        bottomLeft: Boolean,
        strokeWidth: Int = 0,
        strokeColor: Int = Color.TRANSPARENT
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadii = floatArrayOf(
                if (topLeft) radius else 0f,
                if (topLeft) radius else 0f,
                if (topRight) radius else 0f,
                if (topRight) radius else 0f,
                if (bottomRight) radius else 0f,
                if (bottomRight) radius else 0f,
                if (bottomLeft) radius else 0f,
                if (bottomLeft) radius else 0f
            )
            if (strokeWidth > 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }
    }

    private fun configureTopBarFrostedGlass() = binding.run {
        titleBarShell.clipToOutline = true
        if (AppConfig.isEInkMode) {
            titleBarGlassView.gone()
            titleBarShellOverlay.gone()
            return@run
        }

        val glassLevel = (AppConfig.frostedGlassLevel / 100f)
            .coerceIn(0.45f, 1f)
        val glassStyleKey =
            "top-glass:${bottomTabUseDarkGlass()}:${context.accentColor}:${AppConfig.frostedGlassLevel}:$bgColor:$textColor"
        if (topBarGlassStyleKey != glassStyleKey) {
            titleBarShell.background = readBottomTabGlassFallbackShell(glassLevel, 0f)
            titleBarGlassView.visible()
            titleBarShellOverlay.visible()
            titleBarShellOverlay.background = readBottomTabGlassShell(glassLevel, 0f)
            topBarGlassStyleKey = glassStyleKey
        } else {
            if (!titleBarGlassView.isVisible) {
                titleBarGlassView.visible()
            }
            if (!titleBarShellOverlay.isVisible) {
                titleBarShellOverlay.visible()
            }
        }

        syncTopBarGlassLayerHeight()
        titleBarShell.post {
            syncTopBarGlassLayerHeight()
            setupTopBarFrostedGlassView(glassLevel)
        }
    }

    private fun setupTopBarFrostedGlassView(glassLevel: Float) = binding.run {
        val target = bottomTabGlassTarget()
        if (!target.isLaidOut || !titleBarShell.isLaidOut || syncTopBarGlassLayerHeight() <= 0) {
            return@run
        }
        setupBottomTabFrostedGlassView(
            liquidGlassView = titleBarGlassView,
            target = target,
            cornerRadius = 0f,
            refractionHeight = (12f + glassLevel * 8f).dpToPx(),
            refractionOffset = (34f + glassLevel * 18f).dpToPx(),
            blurRadius = (22f + glassLevel * 30f).dpToPx(),
            dispersion = (0.18f + glassLevel * 0.16f).coerceAtMost(0.42f),
            tintAlpha = bottomTabGlassTintAlpha(glassLevel),
            tintColor = bottomTabGlassTintColor()
        )
    }

    private fun syncTopBarGlassLayerHeight(): Int = binding.run {
        val targetHeight = titleBar.height.takeIf { it > 0 } ?: return@run 0
        setTopBarGlassLayerChildHeight(titleBarGlassView, targetHeight)
        setTopBarGlassLayerChildHeight(titleBarShellOverlay, targetHeight)
        targetHeight
    }

    private fun setTopBarGlassLayerChildHeight(view: View, height: Int) {
        view.updateLayoutParams<FrameLayout.LayoutParams> {
            this.height = height
            gravity = Gravity.TOP
        }
    }

    private fun configureBottomTabFrostedGlass() = binding.run {
        bottomTabBar.clipToOutline = true
        if (AppConfig.isEInkMode) {
            val glassStyleKey = "eink:$bgColor:$textColor:${context.accentColor}"
            if (bottomTabGlassStyleKey != glassStyleKey) {
                bottomTabBar.backgroundTintList = null
                bottomTabBar.foreground = null
                bottomTabGlassView.gone()
                bottomTabShellOverlay.gone()
                bottomTabIndicatorContainer.background = bottomTabIndicatorBackground()
                bottomTabBar.background = roundedRect(
                    bgColor,
                    28f.dpToPx()
                )
                bottomTabGlassStyleKey = glassStyleKey
            }
            return@run
        }

        val glassLevel = (AppConfig.frostedGlassLevel / 100f)
            .coerceIn(0.45f, 1f)
        val glassStyleKey = "glass:${bottomTabUseDarkGlass()}:${context.accentColor}:${AppConfig.frostedGlassLevel}"
        if (bottomTabGlassStyleKey != glassStyleKey) {
            bottomTabBar.backgroundTintList = null
            bottomTabBar.foreground = null
            bottomTabBar.background = bottomTabGlassFallbackShell(glassLevel)
            bottomTabGlassView.visible()
            bottomTabShellOverlay.visible()
            bottomTabShellOverlay.background = bottomTabGlassShell(glassLevel)
            bottomTabIndicatorContainer.background = bottomTabIndicatorBackground()
            bottomTabGlassStyleKey = glassStyleKey
            bottomTabBar.post {
                setupBottomTabFrostedGlassViews(glassLevel)
            }
        } else {
            if (!bottomTabGlassView.isVisible) {
                bottomTabGlassView.visible()
            }
            if (!bottomTabShellOverlay.isVisible) {
                bottomTabShellOverlay.visible()
            }
            if (!boundBottomTabGlassViewIds.contains(bottomTabGlassView.id)) {
                bottomTabBar.post {
                    setupBottomTabFrostedGlassViews(glassLevel)
                }
            }
        }
    }

    private fun setupBottomTabFrostedGlassViews(glassLevel: Float) = binding.run {
        val target = bottomTabGlassTarget()
        if (!target.isLaidOut || !bottomTabBar.isLaidOut) {
            return@run
        }
        val tintColor = bottomTabGlassTintColor()

        setupBottomTabFrostedGlassView(
            liquidGlassView = bottomTabGlassView,
            target = target,
            cornerRadius = 28f.dpToPx(),
            refractionHeight = (12f + glassLevel * 8f).dpToPx(),
            refractionOffset = (34f + glassLevel * 18f).dpToPx(),
            blurRadius = (22f + glassLevel * 30f).dpToPx(),
            dispersion = (0.18f + glassLevel * 0.16f).coerceAtMost(0.42f),
            tintAlpha = bottomTabGlassTintAlpha(glassLevel),
            tintColor = tintColor
        )
    }

    private fun configureLayoutAdjustFrostedGlass() = binding.run {
        syncLayoutAdjustPopupMetrics()
        val cornerRadius = 28f.dpToPx()
        layoutMarginAdjustPanel.clipToOutline = true
        if (AppConfig.isEInkMode) {
            val styleKey = "layout-eink:$bgColor:$textColor:${context.accentColor}"
            if (layoutAdjustGlassStyleKey != styleKey) {
                layoutMarginAdjustBlurBackdrop.gone()
                layoutMarginAdjustGlassView.gone()
                layoutMarginAdjustShellOverlay.gone()
                layoutMarginAdjustPanel.background = roundedRect(
                    bgColor,
                    cornerRadius,
                    1.dpToPx(),
                    ColorUtils.adjustAlpha(textColor, 0.24f)
                )
                layoutAdjustGlassStyleKey = styleKey
            }
            return@run
        }

        val glassLevel = (AppConfig.frostedGlassLevel / 100f)
            .coerceIn(0.45f, 1f)
        val styleKey =
            "layout-glass:${bottomTabUseDarkGlass()}:${context.accentColor}:${AppConfig.frostedGlassLevel}:$bgColor:$textColor"
        if (layoutAdjustGlassStyleKey != styleKey) {
            layoutMarginAdjustPanel.background = layoutAdjustGlassFallbackShell(glassLevel, cornerRadius)
            layoutMarginAdjustBlurBackdrop.visible()
            layoutMarginAdjustGlassView.visible()
            layoutMarginAdjustShellOverlay.visible()
            layoutMarginAdjustShellOverlay.background = layoutAdjustGlassShell(glassLevel, cornerRadius)
            layoutAdjustGlassStyleKey = styleKey
        } else {
            if (!layoutMarginAdjustBlurBackdrop.isVisible) {
                layoutMarginAdjustBlurBackdrop.visible()
            }
            if (!layoutMarginAdjustGlassView.isVisible) {
                layoutMarginAdjustGlassView.visible()
            }
            if (!layoutMarginAdjustShellOverlay.isVisible) {
                layoutMarginAdjustShellOverlay.visible()
            }
        }
        layoutMarginAdjustPanel.doOnPreDraw {
            updateLayoutAdjustBlurBackdrop()
            setupLayoutAdjustFrostedGlassViews(glassLevel, cornerRadius)
        }
    }

    private fun setupLayoutAdjustFrostedGlassViews(glassLevel: Float, cornerRadius: Float) = binding.run {
        val target = bottomTabGlassTarget()
        if (!target.isLaidOut || !layoutMarginAdjustPanel.isLaidOut || !layoutMarginAdjustGlassView.isLaidOut) {
            return@run
        }
        setupBottomTabFrostedGlassView(
            liquidGlassView = layoutMarginAdjustGlassView,
            target = target,
            cornerRadius = cornerRadius,
            refractionHeight = (12f + glassLevel * 8f).dpToPx(),
            refractionOffset = (34f + glassLevel * 18f).dpToPx(),
            blurRadius = (22f + glassLevel * 30f).dpToPx(),
            dispersion = (0.18f + glassLevel * 0.16f).coerceAtMost(0.42f),
            tintAlpha = bottomTabGlassTintAlpha(glassLevel),
            tintColor = bottomTabGlassTintColor()
        )
    }

    private fun bottomTabGlassTarget(): ViewGroup {
        val readView = activity?.findViewById<View>(R.id.read_view)
        return activity?.findViewById<ViewGroup>(R.id.read_content_container)
            ?: readView?.parent as? ViewGroup
            ?: parent as? ViewGroup
            ?: binding.vwMenuRoot
    }

    private fun setupBottomTabFrostedGlassView(
        liquidGlassView: LiquidGlassView,
        target: ViewGroup,
        cornerRadius: Float,
        refractionHeight: Float,
        refractionOffset: Float,
        blurRadius: Float,
        dispersion: Float,
        tintAlpha: Float,
        tintColor: FloatArray
    ) {
        if (boundBottomTabGlassViewIds.add(liquidGlassView.id)) {
            liquidGlassView.bind(target)
        }
        liquidGlassView.setCornerRadius(cornerRadius)
        liquidGlassView.setRefractionHeight(refractionHeight)
        liquidGlassView.setRefractionOffset(refractionOffset)
        liquidGlassView.setDispersion(dispersion)
        liquidGlassView.setBlurRadius(blurRadius)
        liquidGlassView.setTintAlpha(tintAlpha)
        liquidGlassView.setTintColorRed(tintColor[0])
        liquidGlassView.setTintColorGreen(tintColor[1])
        liquidGlassView.setTintColorBlue(tintColor[2])
        liquidGlassView.setDraggableEnabled(false)
        liquidGlassView.setElasticEnabled(false)
        liquidGlassView.setTouchEffectEnabled(false)
        liquidGlassView.isClickable = false
        liquidGlassView.isFocusable = false
        liquidGlassView.invalidate()
    }

    private fun bottomTabGlassShell(glassLevel: Float): GradientDrawable {
        return readBottomTabGlassShell(glassLevel, 28f.dpToPx())
    }

    private fun readBottomTabGlassShell(glassLevel: Float, cornerRadius: Float): GradientDrawable {
        val isDark = bottomTabUseDarkGlass()
        val surfaceColor = if (isDark) {
            bottomTabDarkGlassSurfaceColor()
        } else {
            ColorUtils.blendColors(Color.WHITE, context.accentColor, 0.05f)
        }
        val topAlpha = if (isDark) {
            0.34f + glassLevel * 0.12f
        } else {
            0.16f + glassLevel * 0.09f
        }
        val centerAlpha = if (isDark) {
            0.28f + glassLevel * 0.10f
        } else {
            0.11f + glassLevel * 0.07f
        }
        val bottomAlpha = if (isDark) {
            0.20f + glassLevel * 0.08f
        } else {
            0.07f + glassLevel * 0.05f
        }
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                ColorUtils.withAlpha(surfaceColor, topAlpha.coerceAtMost(if (isDark) 0.46f else 0.25f)),
                ColorUtils.withAlpha(surfaceColor, centerAlpha.coerceAtMost(if (isDark) 0.38f else 0.18f)),
                ColorUtils.withAlpha(surfaceColor, bottomAlpha.coerceAtMost(if (isDark) 0.28f else 0.12f))
            )
        ).apply {
            this.cornerRadius = cornerRadius
        }
    }

    private fun bottomTabGlassFallbackShell(glassLevel: Float): GradientDrawable {
        return readBottomTabGlassFallbackShell(glassLevel, 28f.dpToPx())
    }

    private fun readBottomTabGlassFallbackShell(glassLevel: Float, cornerRadius: Float): GradientDrawable {
        val isDark = bottomTabUseDarkGlass()
        val surfaceColor = if (isDark) {
            bottomTabDarkGlassSurfaceColor()
        } else {
            ColorUtils.blendColors(Color.WHITE, context.accentColor, 0.05f)
        }
        val alpha = if (isDark) {
            0.36f + glassLevel * 0.10f
        } else {
            0.14f + glassLevel * 0.10f
        }
        return GradientDrawable().apply {
            this.cornerRadius = cornerRadius
            setColor(ColorUtils.withAlpha(surfaceColor, alpha.coerceAtMost(if (isDark) 0.46f else 0.24f)))
        }
    }

    private fun layoutAdjustGlassShell(glassLevel: Float, cornerRadius: Float): GradientDrawable {
        return readBottomTabGlassShell(glassLevel, cornerRadius)
    }

    private fun layoutAdjustGlassFallbackShell(glassLevel: Float, cornerRadius: Float): GradientDrawable {
        return readBottomTabGlassFallbackShell(glassLevel, cornerRadius)
    }

    private fun bottomTabDarkGlassSurfaceColor(): Int {
        return Color.rgb(8, 10, 14)
    }

    private fun bottomTabGlassTintAlpha(glassLevel: Float): Float {
        return if (bottomTabUseDarkGlass()) {
            (0.12f + glassLevel * 0.14f).coerceAtMost(0.26f)
        } else {
            (0.06f + glassLevel * 0.10f).coerceAtMost(0.16f)
        }
    }

    private fun bottomTabGlassTintColor(): FloatArray {
        return if (bottomTabUseDarkGlass()) {
            floatArrayOf(0.08f, 0.10f, 0.14f)
        } else {
            floatArrayOf(0.70f, 0.79f, 0.86f)
        }
    }

    private fun bottomTabIndicatorBackground(): GradientDrawable {
        return roundedRect(context.accentColor, 18f.dpToPx())
    }

    private fun bottomTabUseDarkGlass(): Boolean {
        return AppConfig.isNightTheme && !AppConfig.isEInkMode
    }

    private fun bottomTabContentColor(): Int {
        return if (bottomTabUseDarkGlass()) {
            Color.WHITE
        } else {
            Color.BLACK
        }
    }

    private fun bottomTabSelectedContentColor(): Int = Color.WHITE

    private fun bottomTabSelectedLabelColor(): Int = Color.WHITE

    private fun openFontSelectDialog() {
        hideExpandedPanel(anim = false)
        activity?.showDialogFragment<FontSelectDialog>()
    }

    private fun bindLayoutDecimalSeek(
        seekBar: SeekBar,
        valueView: TextView,
        onStop: (Int) -> Unit
    ) {
        seekBar.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateLayoutDecimalValue(valueView, progress)
                if (fromUser) onStop(progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }

    private fun bindLayoutIntSeek(
        seekBar: SeekBar,
        valueView: TextView,
        onStop: (Int) -> Unit
    ) {
        seekBar.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateLayoutIntValue(valueView, progress)
                if (fromUser) onStop(progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }

    private fun bindLayoutMarginSpinbox(
        increaseButton: View,
        decreaseButton: View,
        side: LayoutMarginSide
    ) {
        increaseButton.setOnClickListener {
            adjustLayoutMargin(side, 1)
        }
        decreaseButton.setOnClickListener {
            adjustLayoutMargin(side, -1)
        }
    }

    private fun adjustLayoutMargin(side: LayoutMarginSide, delta: Int) {
        val margins = currentLayoutMarginValues()
        val current = when (side) {
            LayoutMarginSide.Top -> margins.top
            LayoutMarginSide.Bottom -> margins.bottom
            LayoutMarginSide.Left -> margins.left
            LayoutMarginSide.Right -> margins.right
        }
        setLayoutMarginValue(side, current + delta)
    }

    private fun setLayoutMarginValue(side: LayoutMarginSide, progress: Int) = binding.run {
        when (activeLayoutMarginAdjustMode) {
            LayoutMarginAdjustMode.Body -> when (side) {
                LayoutMarginSide.Top -> setLayoutBodyPadding(
                    progress,
                    seekLayoutPaddingTop,
                    tvLayoutPaddingTopValue
                ) { ReadBookConfig.paddingTop = it }

                LayoutMarginSide.Bottom -> setLayoutBodyPadding(
                    progress,
                    seekLayoutPaddingBottom,
                    tvLayoutPaddingBottomValue
                ) { ReadBookConfig.paddingBottom = it }

                LayoutMarginSide.Left -> setLayoutBodyPadding(
                    progress,
                    seekLayoutPaddingLeft,
                    tvLayoutPaddingLeftValue
                ) { ReadBookConfig.paddingLeft = it }

                LayoutMarginSide.Right -> setLayoutBodyPadding(
                    progress,
                    seekLayoutPaddingRight,
                    tvLayoutPaddingRightValue
                ) { ReadBookConfig.paddingRight = it }
            }

            LayoutMarginAdjustMode.Text -> Unit

            LayoutMarginAdjustMode.Title -> when (side) {
                LayoutMarginSide.Top -> setLayoutTitleTopSpacing(progress)
                LayoutMarginSide.Bottom -> setLayoutTitleBottomSpacing(progress)
                LayoutMarginSide.Left,
                LayoutMarginSide.Right -> Unit
            }

            LayoutMarginAdjustMode.Header -> when (side) {
                LayoutMarginSide.Top -> setLayoutTipPadding(
                    progress,
                    seekLayoutHeaderPaddingTop,
                    tvLayoutHeaderPaddingTopValue
                ) { ReadBookConfig.headerPaddingTop = it }

                LayoutMarginSide.Bottom -> setLayoutTipPadding(
                    progress,
                    seekLayoutHeaderPaddingBottom,
                    tvLayoutHeaderPaddingBottomValue
                ) { ReadBookConfig.headerPaddingBottom = it }

                LayoutMarginSide.Left,
                LayoutMarginSide.Right -> Unit
            }

            LayoutMarginAdjustMode.Footer -> when (side) {
                LayoutMarginSide.Top -> setLayoutTipPadding(
                    progress,
                    seekLayoutFooterPaddingTop,
                    tvLayoutFooterPaddingTopValue
                ) { ReadBookConfig.footerPaddingTop = it }

                LayoutMarginSide.Bottom -> setLayoutTipPadding(
                    progress,
                    seekLayoutFooterPaddingBottom,
                    tvLayoutFooterPaddingBottomValue
                ) { ReadBookConfig.footerPaddingBottom = it }

                LayoutMarginSide.Left,
                LayoutMarginSide.Right -> Unit
            }
        }
    }

    private fun setupBottomNavigationEvents() = binding.run {
        readBottomPrimaryNav.setOnItemSelectedListener { item ->
            if (suppressBottomNavSelection) {
                return@setOnItemSelectedListener true
            }
            flashBottomTabIndicator(readBottomPrimaryNav, item.itemId)
            when (item.itemId) {
                R.id.menu_read_search -> {
                    toggleBottomTab(BottomTab.Search)
                }

                R.id.menu_read_toc -> {
                    toggleBottomTab(BottomTab.Toc)
                }

                R.id.menu_read_aloud -> {
                    toggleBottomTab(BottomTab.Aloud)
                }

                R.id.menu_read_interface -> {
                    if (activeBottomTab?.usesPrimaryNavigation() == true) {
                        hideExpandedPanel(anim = false)
                    }
                    switchBottomTabMode(BottomTabMode.Interface)
                    true
                }

                R.id.menu_read_settings -> {
                    toggleBottomTab(BottomTab.Settings)
                }

                else -> false
            }
        }
        readBottomPrimaryNav.setOnItemReselectedListener { item ->
            if (!suppressBottomNavSelection) {
                when (item.itemId) {
                    R.id.menu_read_search -> toggleBottomTab(BottomTab.Search)
                    R.id.menu_read_toc -> toggleBottomTab(BottomTab.Toc)
                    R.id.menu_read_aloud -> toggleBottomTab(BottomTab.Aloud)
                    R.id.menu_read_settings -> toggleBottomTab(BottomTab.Settings)
                    R.id.menu_read_interface -> {
                        flashBottomTabIndicator(readBottomPrimaryNav, item.itemId)
                        switchBottomTabMode(BottomTabMode.Interface)
                    }
                }
            }
        }
        readBottomInterfaceNav.setOnItemSelectedListener { item ->
            if (suppressBottomNavSelection) {
                return@setOnItemSelectedListener true
            }
            if (item.itemId == R.id.menu_read_interface_back) {
                hideExpandedPanel(returnToPrimary = true)
                return@setOnItemSelectedListener false
            }
            val tab = item.itemId.toBottomTab() ?: return@setOnItemSelectedListener false
            toggleBottomTab(tab)
        }
        readBottomInterfaceNav.setOnItemReselectedListener { item ->
            if (suppressBottomNavSelection) {
                return@setOnItemReselectedListener
            }
            if (item.itemId == R.id.menu_read_interface_back) {
                hideExpandedPanel(returnToPrimary = true)
                return@setOnItemReselectedListener
            }
            item.itemId.toBottomTab()?.let { tab ->
                if (activeBottomTab == tab && flExpandedPanel.isVisible) {
                    hideExpandedPanel(returnToPrimary = true)
                } else {
                    showBottomPanel(tab)
                }
            }
        }
    }

    private fun bindEvent() = binding.run {
        vwMenuBg.setOnClickListener { handleBackgroundDismiss() }
        titleBar.toolbar.setOnClickListener {
            callBack.openBookInfoActivity()
        }
        val chapterViewClickListener = OnClickListener {
            if (ReadBook.isLocalBook) {
                return@OnClickListener
            }
            val url = tvChapterUrl.tag as? String ?: return@OnClickListener
            if (AppConfig.readUrlInBrowser) {
                context.openUrl(url.substringBefore(",{"))
            } else {
                Coroutine.async {
                    context.startActivity<WebViewActivity> {
                        val bookSource = ReadBook.bookSource
                        putExtra("title", tvChapterUrl.text)
                        putExtra("url", url)
                        putExtra("sourceOrigin", bookSource?.bookSourceUrl)
                        putExtra("sourceName", bookSource?.bookSourceName)
                        putExtra("sourceType", bookSource?.getSourceType())
                    }
                }
            }
        }
        val chapterViewLongClickListener = OnLongClickListener {
            if (ReadBook.isLocalBook) {
                return@OnLongClickListener true
            }
            context.alert(R.string.open_fun) {
                setMessage(R.string.use_browser_open)
                okButton {
                    AppConfig.readUrlInBrowser = true
                }
                noButton {
                    AppConfig.readUrlInBrowser = false
                }
            }
            true
        }
        tvChapterName.setOnClickListener(chapterViewClickListener)
        tvChapterName.setOnLongClickListener(chapterViewLongClickListener)
        tvChapterUrl.setOnClickListener(chapterViewClickListener)
        tvChapterUrl.setOnLongClickListener(chapterViewLongClickListener)
        //书源操作
        tvSourceAction.onClick {
            val source = ReadBook.bookSource
            val hasLoginUrl = !source?.loginUrl.isNullOrEmpty()
            sourceMenu.menu.findItem(R.id.menu_login).apply {
                isVisible = source != null
                title = context.getString(
                    if (hasLoginUrl) R.string.login else R.string.open_in_app_webview
                )
            }
            sourceMenu.menu.findItem(R.id.menu_chapter_pay).isVisible =
                hasLoginUrl
                        && ReadBook.curTextChapter?.isVip == true
                        && ReadBook.curTextChapter?.isPay != true
            sourceMenu.show()
        }
        //亮度跟随
        ivBrightnessAuto.setOnClickListener {
            context.putPrefBoolean("brightnessAuto", !brightnessAuto())
            upBrightnessState()
        }
        //亮度调节
        seekBrightness.setOnSeekBarChangeListener(object : SeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateBrightnessValue()
                if (fromUser) {
                    setScreenBrightness(progress.toFloat())
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                AppConfig.readBrightness = seekBar.progress
            }

        })
        //目录
        setupBottomNavigationEvents()

        //朗读
        pageAnimSampleBindings.forEach { sample ->
            sample.binding.root.setOnClickListener {
                applyPageAnimSample(sample.anim)
            }
        }
        panelPageAutoPage.setOnClickListener {
            runMenuOut {
                callBack.autoPage()
            }
        }
        panelPageTouchSlop.setOnClickListener {
            NumberPickerDialog(context)
                .setTitle(context.getString(R.string.page_touch_slop_dialog_title))
                .setMaxValue(9999)
                .setMinValue(0)
                .setValue(AppConfig.pageTouchSlop)
                .show {
                    AppConfig.pageTouchSlop = it
                    postEvent(EventBus.UP_CONFIG, arrayListOf(4))
                    updatePageTurnControls()
                }
        }
        panelPageAnimSpeed.setOnClickListener {
            NumberPickerDialog(context)
                .setTitle(context.getString(R.string.page_animation_speed_dialog_title))
                .setMaxValue(2000)
                .setMinValue(0)
                .setValue(AppConfig.pageAnimationSpeed)
                .setCustomButton(R.string.btn_default_s) {
                    AppConfig.pageAnimationSpeed = 300
                    updatePageTurnControls()
                    persistActiveThemeSuiteChange()
                }
                .show {
                    AppConfig.pageAnimationSpeed = it
                    updatePageTurnControls()
                    persistActiveThemeSuiteChange()
                }
        }
        panelPageVolumeKey.setOnClickListener {
            context.putPrefBoolean(PreferKey.volumeKeyPage, !AppConfig.volumeKeyPage)
            updatePageTurnControls()
        }
        panelPageMouseWheel.setOnClickListener {
            context.putPrefBoolean(PreferKey.mouseWheelPage, !AppConfig.mouseWheelPage)
            updatePageTurnControls()
        }
        panelMoreSearch.setOnClickListener {
            showBottomPanel(BottomTab.Search)
        }
        panelMoreAutoPage.setOnClickListener {
            runMenuOut {
                callBack.autoPage()
            }
        }
        panelMoreReplace.setOnClickListener {
            callBack.openReplaceRule()
        }
        panelMoreSettings.setOnClickListener {
            runMenuOut {
                callBack.showMoreSetting()
            }
        }
        themeCardBindings.zip(themePresets).forEach { (card, preset) ->
            card.root.setOnClickListener {
                applyThemePreset(preset)
            }
        }
        binding.themeCardAdd.root.setOnClickListener { addCurrentThemeSuite() }
        fontSampleBindings.forEach { sample ->
            sample.binding.root.setOnClickListener { sample.onClick() }
        }
        layoutTextStyleEntry.setOnClickListener {
            showLayoutMarginAdjustOverlay(LayoutMarginAdjustMode.Text)
        }
        layoutMarginEntryBody.setOnClickListener {
            showLayoutMarginAdjustOverlay(LayoutMarginAdjustMode.Body)
        }
        layoutMarginEntryTitle.setOnClickListener {
            showLayoutMarginAdjustOverlay(LayoutMarginAdjustMode.Title)
        }
        layoutMarginEntryHeader.setOnClickListener {
            showLayoutMarginAdjustOverlay(LayoutMarginAdjustMode.Header)
        }
        layoutMarginEntryFooter.setOnClickListener {
            showLayoutMarginAdjustOverlay(LayoutMarginAdjustMode.Footer)
        }
        btnLayoutMarginAdjustClose.setOnClickListener {
            hideLayoutMarginAdjustOverlay()
        }
        bindLayoutDecimalSeek(
            seekLayoutLetterSpacing,
            tvLayoutLetterSpacingValue,
            ::setLayoutLetterSpacing
        )
        bindLayoutDecimalSeek(
            seekLayoutLineSpacing,
            tvLayoutLineSpacingValue,
            ::setLayoutLineSpacing
        )
        bindLayoutDecimalSeek(
            seekLayoutParagraphSpacing,
            tvLayoutParagraphSpacingValue,
            ::setLayoutParagraphSpacing
        )
        bindLayoutIntSeek(seekLayoutPaddingTop, tvLayoutPaddingTopValue) {
            setLayoutBodyPadding(it, seekLayoutPaddingTop, tvLayoutPaddingTopValue) { value ->
                ReadBookConfig.paddingTop = value
            }
        }
        bindLayoutMarginSpinbox(
            btnLayoutPaddingTopIncrease,
            btnLayoutPaddingTopDecrease,
            LayoutMarginSide.Top
        )
        bindLayoutIntSeek(seekLayoutPaddingBottom, tvLayoutPaddingBottomValue) {
            setLayoutBodyPadding(it, seekLayoutPaddingBottom, tvLayoutPaddingBottomValue) { value ->
                ReadBookConfig.paddingBottom = value
            }
        }
        bindLayoutMarginSpinbox(
            btnLayoutPaddingBottomIncrease,
            btnLayoutPaddingBottomDecrease,
            LayoutMarginSide.Bottom
        )
        bindLayoutIntSeek(seekLayoutPaddingLeft, tvLayoutPaddingLeftValue) {
            setLayoutBodyPadding(it, seekLayoutPaddingLeft, tvLayoutPaddingLeftValue) { value ->
                ReadBookConfig.paddingLeft = value
            }
        }
        bindLayoutMarginSpinbox(
            btnLayoutPaddingLeftIncrease,
            btnLayoutPaddingLeftDecrease,
            LayoutMarginSide.Left
        )
        bindLayoutIntSeek(seekLayoutPaddingRight, tvLayoutPaddingRightValue) {
            setLayoutBodyPadding(it, seekLayoutPaddingRight, tvLayoutPaddingRightValue) { value ->
                ReadBookConfig.paddingRight = value
            }
        }
        bindLayoutMarginSpinbox(
            btnLayoutPaddingRightIncrease,
            btnLayoutPaddingRightDecrease,
            LayoutMarginSide.Right
        )
        bindLayoutIntSeek(seekLayoutTitleSize, tvLayoutTitleSizeValue, ::setLayoutTitleSize)
        bindLayoutIntSeek(
            seekLayoutTitleTopSpacing,
            tvLayoutTitleTopSpacingValue,
            ::setLayoutTitleTopSpacing
        )
        bindLayoutIntSeek(
            seekLayoutTitleBottomSpacing,
            tvLayoutTitleBottomSpacingValue,
            ::setLayoutTitleBottomSpacing
        )
        tvLayoutTitleModeLeft.setOnClickListener { setLayoutTitleMode(0) }
        tvLayoutTitleModeCenter.setOnClickListener { setLayoutTitleMode(1) }
        tvLayoutTitleModeAdvanced.setOnClickListener {
            setLayoutTitleMode(AdvancedTitleConfig.TITLE_MODE_ADVANCED)
        }
        tvLayoutTitleModeHide.setOnClickListener { setLayoutTitleMode(2) }
        tvLayoutMarginTitleModeLeft.setOnClickListener { setLayoutTitleMode(0) }
        tvLayoutMarginTitleModeCenter.setOnClickListener { setLayoutTitleMode(1) }
        tvLayoutMarginTitleModeAdvanced.setOnClickListener {
            setLayoutTitleMode(AdvancedTitleConfig.TITLE_MODE_ADVANCED)
        }
        tvLayoutMarginTitleModeHide.setOnClickListener { setLayoutTitleMode(2) }
        bindLayoutIntSeek(seekLayoutHeaderPaddingTop, tvLayoutHeaderPaddingTopValue) {
            setLayoutTipPadding(it, seekLayoutHeaderPaddingTop, tvLayoutHeaderPaddingTopValue) { value ->
                ReadBookConfig.headerPaddingTop = value
            }
        }
        bindLayoutIntSeek(seekLayoutHeaderPaddingBottom, tvLayoutHeaderPaddingBottomValue) {
            setLayoutTipPadding(it, seekLayoutHeaderPaddingBottom, tvLayoutHeaderPaddingBottomValue) { value ->
                ReadBookConfig.headerPaddingBottom = value
            }
        }
        bindLayoutIntSeek(seekLayoutFooterPaddingTop, tvLayoutFooterPaddingTopValue) {
            setLayoutTipPadding(it, seekLayoutFooterPaddingTop, tvLayoutFooterPaddingTopValue) { value ->
                ReadBookConfig.footerPaddingTop = value
            }
        }
        bindLayoutIntSeek(seekLayoutFooterPaddingBottom, tvLayoutFooterPaddingBottomValue) {
            setLayoutTipPadding(it, seekLayoutFooterPaddingBottom, tvLayoutFooterPaddingBottomValue) { value ->
                ReadBookConfig.footerPaddingBottom = value
            }
        }
        layoutHeaderDisplayAutoCard.setOnClickListener { setHeaderDisplayMode(0) }
        layoutHeaderDisplayShowCard.setOnClickListener { setHeaderDisplayMode(1) }
        taggedView("layout_header_display_hide_card").setOnClickListener { setHeaderDisplayMode(2) }
        llLayoutHeaderTipLeft.setOnClickListener {
            showHeaderTipItemSelector(R.string.left) { value ->
                ReadTipConfig.tipHeaderLeft = value
            }
        }
        llLayoutHeaderTipMiddle.setOnClickListener {
            showHeaderTipItemSelector(R.string.middle) { value ->
                ReadTipConfig.tipHeaderMiddle = value
            }
        }
        llLayoutHeaderTipRight.setOnClickListener {
            showHeaderTipItemSelector(R.string.right) { value ->
                ReadTipConfig.tipHeaderRight = value
            }
        }
        llLayoutFooterTipLeft.setOnClickListener {
            showHeaderTipItemSelector(R.string.left) { value ->
                ReadTipConfig.tipFooterLeft = value
            }
        }
        llLayoutFooterTipMiddle.setOnClickListener {
            showHeaderTipItemSelector(R.string.middle) { value ->
                ReadTipConfig.tipFooterMiddle = value
            }
        }
        llLayoutFooterTipRight.setOnClickListener {
            showHeaderTipItemSelector(R.string.right) { value ->
                ReadTipConfig.tipFooterRight = value
            }
        }
        taggedView("layout_footer_display_auto_card").setOnClickListener { setFooterDisplayMode(2) }
        layoutFooterDisplayShowCard.setOnClickListener { setFooterDisplayMode(0) }
        layoutFooterDisplayHideCard.setOnClickListener { setFooterDisplayMode(1) }
        tvLayoutHeaderLineToggle.setOnClickListener { setHeaderDividerVisible(true) }
        tvLayoutHeaderLineHide.setOnClickListener { setHeaderDividerVisible(false) }
        tvLayoutFooterLineToggle.setOnClickListener { setFooterDividerVisible(true) }
        tvLayoutFooterLineHide.setOnClickListener { setFooterDividerVisible(false) }
        llLayoutTipColor.setOnClickListener {
            ColorPickerDialog.newBuilder()
                .setColor(ReadBookConfig.durConfig.curTextColor())
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(TEXT_COLOR)
                .show(activity)
        }
        llLayoutTipDividerColor.setOnClickListener {
            context.selector(items = ReadTipConfig.tipDividerColorNames) { _, i ->
                when (i) {
                    0, 1 -> {
                        ReadTipConfig.tipDividerColor = i - 1
                        ReadBookConfig.save()
                        updateTipSettingValues()
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    }

                    2 -> ColorPickerDialog.newBuilder()
                        .setColor(effectiveTipDividerColor())
                        .setShowAlphaSlider(false)
                        .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                        .setDialogId(TIP_DIVIDER_COLOR)
                        .show(activity)
                }
            }
        }
        bindBackgroundSeek(seekBackgroundBrightness, tvBackgroundBrightnessValue) {
            ReadBookConfig.bgBrightness = it.coerceIn(0, 100)
            ReadBookConfig.save()
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 3))
            persistActiveThemeSuiteChange()
        }
        bindBackgroundSeek(seekBackgroundSaturation, tvBackgroundSaturationValue) {
            ReadBookConfig.bgSaturation = it.coerceIn(0, 100)
            ReadBookConfig.save()
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 3))
            persistActiveThemeSuiteChange()
        }
        bindBackgroundSeek(seekBackgroundAlpha, tvBackgroundAlphaValue) {
            ReadBookConfig.bgAlpha = it.coerceIn(0, 100)
            ReadBookConfig.save()
            postEvent(EventBus.UP_CONFIG, arrayListOf(3))
            persistActiveThemeSuiteChange()
        }
        seekThemeFontWeight.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateThemeFontWeightValue(progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                setFontWeight(seekBar.progress)
            }
        })
        seekThemeTextSize.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateThemeTextSizeValue(progress)
                if (fromUser) setTextSize(progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }

    private fun initAnimation() {
        menuTopIn.setAnimationListener(menuInListener)
        menuTopOut.setAnimationListener(menuOutListener)
    }

    fun upBookView() {
        binding.titleBar.title = null
        binding.titleBar.subtitle = null
        binding.tvChapterName.text = ReadBook.book?.name.orEmpty()
        binding.tvChapterName.visible()
        updateTopBarSourceAction()
        updateTopBarLoginAction()
        ReadBook.curTextChapter?.let {
            binding.tvChapterUrl.text = it.title
            if (!ReadBook.isLocalBook) {
                binding.tvChapterUrl.tag = it.chapter.getAbsoluteURL()
            } else {
                binding.tvChapterUrl.tag = null
            }
            binding.tvChapterUrl.visible()
        } ?: let {
            binding.tvChapterUrl.tag = null
            binding.tvChapterUrl.gone()
        }
    }

    fun setAutoPage(autoPage: Boolean) = binding.run {
        if (autoPage) {
            panelMoreAutoPage.text = context.getString(R.string.auto_next_page_stop)
            panelMoreAutoPage.contentDescription = context.getString(R.string.auto_next_page_stop)
            panelPageAutoPage.text = context.getString(R.string.auto_next_page_stop)
            panelPageAutoPage.contentDescription = context.getString(R.string.auto_next_page_stop)
        } else {
            panelMoreAutoPage.text = context.getString(R.string.auto_next_page)
            panelMoreAutoPage.contentDescription = context.getString(R.string.auto_next_page)
            panelPageAutoPage.text = context.getString(R.string.auto_next_page)
            panelPageAutoPage.contentDescription = context.getString(R.string.auto_next_page)
        }
        panelMoreAutoPage.setTextColor(textColor)
        configureOptionButton(panelPageAutoPage, autoPage)
    }

    interface CallBack {
        fun autoPage()
        fun openReplaceRule()
        fun openChapterList()
        fun openSearchActivity(searchWord: String?)
        fun openInlineSearchResult(searchResult: SearchResult, results: List<SearchResult>, index: Int)
        fun openSourceEditActivity()
        fun openBookInfoActivity()
        fun showMoreSetting()
        fun showReadAloudDialog()
        fun upSystemUiVisibility()
        fun onClickReadAloud()
        fun showHelp()
        fun showLogin()
        fun payAction()
        fun disableSource()
        fun skipToChapter(index: Int)
        fun onMenuShow()
        fun onMenuHide()
        fun onReadMenuExpandedPanelVisibilityChanged(isVisible: Boolean)
    }

    private class PageAnimPreviewDrawable(
        private val pageColor: Int,
        private val inkColor: Int,
        private val menuTextColor: Int,
        private val accentColor: Int,
        private val anim: Int?,
        private val selected: Boolean
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val rect = RectF()

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.width() <= 0 || b.height() <= 0) {
                return
            }
            val radius = min(b.width(), b.height()) * 0.16f
            rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
            paint.style = Paint.Style.FILL
            paint.color = ColorUtils.adjustAlpha(menuTextColor, 0.045f)
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (selected) 2.5f else 1.2f
            paint.color = if (selected) accentColor else ColorUtils.adjustAlpha(menuTextColor, 0.14f)
            canvas.drawRoundRect(rect, radius, radius, paint)

            when (anim) {
                null -> drawDefault(canvas, b)
                PageAnim.coverPageAnim -> drawCover(canvas, b)
                PageAnim.linkedCoverPageAnim -> drawLinkedCover(canvas, b)
                PageAnim.slidePageAnim -> drawSlide(canvas, b)
                PageAnim.simulationPageAnim -> drawSimulation(canvas, b)
                PageAnim.scrollPageAnim -> drawScroll(canvas, b)
                PageAnim.noAnim -> drawNone(canvas, b)
                else -> drawCover(canvas, b)
            }
        }

        private fun drawDefault(canvas: Canvas, b: android.graphics.Rect) {
            val page = pageRect(b, 0.25f, 0.18f, 0.78f, 0.82f)
            drawPage(canvas, page, selected)
            drawLines(canvas, page, 3)
            drawAccentDot(canvas, b.centerX().toFloat(), b.bottom - b.height() * 0.22f)
        }

        private fun drawCover(canvas: Canvas, b: android.graphics.Rect) {
            val back = pageRect(b, 0.18f, 0.20f, 0.70f, 0.82f)
            val front = pageRect(b, 0.34f, 0.16f, 0.86f, 0.78f)
            drawPage(canvas, back, false)
            drawPage(canvas, front, selected)
            drawLines(canvas, front, 3)
        }

        private fun drawLinkedCover(canvas: Canvas, b: android.graphics.Rect) {
            val left = pageRect(b, 0.14f, 0.18f, 0.56f, 0.82f)
            val right = pageRect(b, 0.44f, 0.18f, 0.86f, 0.82f)
            drawPage(canvas, left, false)
            drawPage(canvas, right, selected)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = ColorUtils.adjustAlpha(accentColor, 0.72f)
            canvas.drawLine(right.left, right.top + 3f, right.left, right.bottom - 3f, paint)
            drawLines(canvas, right, 2)
        }

        private fun drawSlide(canvas: Canvas, b: android.graphics.Rect) {
            val first = pageRect(b, 0.10f, 0.22f, 0.48f, 0.80f)
            val second = pageRect(b, 0.54f, 0.18f, 0.92f, 0.76f)
            drawPage(canvas, first, false)
            drawPage(canvas, second, selected)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.4f
            paint.color = ColorUtils.adjustAlpha(accentColor, 0.82f)
            val y = b.centerY().toFloat()
            canvas.drawLine(first.right + 4f, y, second.left - 6f, y, paint)
            canvas.drawLine(second.left - 10f, y - 5f, second.left - 5f, y, paint)
            canvas.drawLine(second.left - 10f, y + 5f, second.left - 5f, y, paint)
        }

        private fun drawSimulation(canvas: Canvas, b: android.graphics.Rect) {
            val page = pageRect(b, 0.20f, 0.16f, 0.82f, 0.82f)
            drawPage(canvas, page, selected)
            drawLines(canvas, page, 2)
            path.reset()
            path.moveTo(page.right - page.width() * 0.30f, page.top)
            path.quadTo(page.right - 2f, page.top + 4f, page.right, page.top + page.height() * 0.34f)
            path.lineTo(page.right, page.top)
            path.close()
            paint.style = Paint.Style.FILL
            paint.color = ColorUtils.adjustAlpha(accentColor, 0.32f)
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.6f
            paint.color = ColorUtils.adjustAlpha(accentColor, 0.68f)
            canvas.drawPath(path, paint)
        }

        private fun drawScroll(canvas: Canvas, b: android.graphics.Rect) {
            val strip = pageRect(b, 0.26f, 0.12f, 0.74f, 0.88f)
            drawPage(canvas, strip, selected)
            drawLines(canvas, strip, 4)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.2f
            paint.color = ColorUtils.adjustAlpha(accentColor, 0.82f)
            val x = strip.right + b.width() * 0.09f
            canvas.drawLine(x, strip.top + 6f, x, strip.bottom - 6f, paint)
            canvas.drawLine(x - 5f, strip.bottom - 12f, x, strip.bottom - 6f, paint)
            canvas.drawLine(x + 5f, strip.bottom - 12f, x, strip.bottom - 6f, paint)
        }

        private fun drawNone(canvas: Canvas, b: android.graphics.Rect) {
            val page = pageRect(b, 0.22f, 0.18f, 0.78f, 0.82f)
            drawPage(canvas, page, selected)
            drawLines(canvas, page, 3)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.4f
            paint.color = ColorUtils.adjustAlpha(accentColor, 0.78f)
            val left = page.left + page.width() * 0.28f
            val right = page.right - page.width() * 0.28f
            val y = page.bottom - page.height() * 0.18f
            canvas.drawLine(left, y, right, y, paint)
        }

        private fun pageRect(
            b: android.graphics.Rect,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float
        ): RectF {
            return RectF(
                b.left + b.width() * left,
                b.top + b.height() * top,
                b.left + b.width() * right,
                b.top + b.height() * bottom
            )
        }

        private fun drawPage(canvas: Canvas, page: RectF, highlighted: Boolean) {
            paint.style = Paint.Style.FILL
            paint.color = pageColor
            canvas.drawRoundRect(page, 7f, 7f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (highlighted) 2f else 1.2f
            paint.color = if (highlighted) {
                ColorUtils.adjustAlpha(accentColor, 0.78f)
            } else {
                ColorUtils.adjustAlpha(menuTextColor, 0.22f)
            }
            canvas.drawRoundRect(page, 7f, 7f, paint)
        }

        private fun drawLines(canvas: Canvas, page: RectF, count: Int) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = ColorUtils.adjustAlpha(inkColor, 0.52f)
            val left = page.left + page.width() * 0.18f
            val right = page.right - page.width() * 0.18f
            val firstY = page.top + page.height() * 0.32f
            val gap = page.height() * 0.14f
            repeat(count) { index ->
                val y = firstY + gap * index
                canvas.drawLine(left, y, right - if (index == count - 1) page.width() * 0.16f else 0f, y, paint)
            }
        }

        private fun drawAccentDot(canvas: Canvas, x: Float, y: Float) {
            paint.style = Paint.Style.FILL
            paint.color = ColorUtils.adjustAlpha(accentColor, 0.86f)
            canvas.drawCircle(x, y, 3.5f, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Suppress("DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class ReadMenuTocAdapter(
        private val onChapterClick: (BookChapter) -> Unit
    ) : RecyclerView.Adapter<ReadMenuTocAdapter.Holder>() {

        private val chapters = arrayListOf<BookChapter>()
        private val downloadingChapters = mutableSetOf<Int>()

        fun submit(items: List<BookChapter>) {
            chapters.clear()
            chapters.addAll(items)
            downloadingChapters.removeAll { index ->
                val book = ReadBook.book ?: return@removeAll false
                items.firstOrNull { it.index == index }?.let { BookHelp.hasContent(book, it) } == true
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val context = parent.context
            val row = LinearLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                minimumHeight = 54.dpToPx()
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                setPadding(12.dpToPx(), 6.dpToPx(), 12.dpToPx(), 6.dpToPx())
            }
            val textColumn = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
                orientation = LinearLayout.VERTICAL
            }
            val title = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER_VERTICAL
                includeFontPadding = false
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                textSize = 15f
            }
            val meta = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 4.dpToPx()
                }
                includeFontPadding = false
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                textSize = 12f
            }
            val download = AppCompatImageButton(context).apply {
                layoutParams = LinearLayout.LayoutParams(42.dpToPx(), 42.dpToPx()).apply {
                    marginStart = 8.dpToPx()
                }
                background = null
                setImageResource(R.drawable.ic_lucide_download)
                contentDescription = context.getString(R.string.read_menu_download_chapter)
                scaleType = android.widget.ImageView.ScaleType.CENTER
            }
            textColumn.addView(title)
            textColumn.addView(meta)
            row.addView(textColumn)
            row.addView(download)
            return Holder(row, title, meta, download)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val chapter = chapters[position]
            val context = holder.title.context
            val selected = chapter.index == ReadBook.durChapterIndex
            val book = ReadBook.book
            val downloaded = book != null && BookHelp.hasContent(book, chapter)
            if (downloaded) {
                downloadingChapters.remove(chapter.index)
            }
            val downloading = chapter.index in downloadingChapters
            holder.itemView.background = if (selected) tocSelectedBackground(context) else null
            holder.title.text = chapter.title
            holder.title.setTypeface(null, if (chapter.isVolume) Typeface.BOLD else Typeface.NORMAL)
            holder.title.setTextColor(
                when {
                    selected -> Color.WHITE
                    AppConfig.isNightTheme -> ColorUtils.adjustAlpha(Color.WHITE, 0.86f)
                    else -> ColorUtils.adjustAlpha(Color.BLACK, 0.82f)
                }
            )
            holder.meta.text = buildTocMeta(context, chapter, downloaded, downloading)
            holder.meta.setTextColor(
                when {
                    selected -> ColorUtils.adjustAlpha(Color.WHITE, 0.76f)
                    AppConfig.isNightTheme -> ColorUtils.adjustAlpha(Color.WHITE, 0.52f)
                    else -> ColorUtils.adjustAlpha(Color.BLACK, 0.48f)
                }
            )
            holder.download.isVisible = !chapter.isVolume && !downloaded && !downloading
            holder.download.setColorFilter(
                if (selected) Color.WHITE else holder.title.currentTextColor,
                PorterDuff.Mode.SRC_IN
            )
            holder.download.setOnClickListener {
                downloadingChapters.add(chapter.index)
                notifyTocChapterChanged(chapter.index)
                ReadBook.loadContent(chapter.index, upContent = false) {
                    holder.itemView.post {
                        downloadingChapters.remove(chapter.index)
                        notifyTocChapterChanged(chapter.index)
                    }
                }
            }
            holder.itemView.setOnClickListener {
                onChapterClick(chapter)
            }
        }

        override fun getItemCount(): Int = chapters.size

        private fun tocSelectedBackground(context: Context): GradientDrawable {
            return GradientDrawable().apply {
                cornerRadius = 12f.dpToPx()
                setColor(context.accentColor)
            }
        }

        private fun notifyTocChapterChanged(chapterIndex: Int) {
            val adapterPosition = chapters.indexOfFirst { it.index == chapterIndex }
            if (adapterPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(adapterPosition)
            }
        }

        private fun buildTocMeta(
            context: Context,
            chapter: BookChapter,
            downloaded: Boolean,
            downloading: Boolean
        ): String {
            val parts = arrayListOf<String>()
            chapter.tag?.takeIf { it.isNotBlank() }?.let(parts::add)
            when {
                downloading -> parts.add(context.getString(R.string.downloading))
                downloaded -> {
                chapter.wordCount?.takeIf { it.isNotBlank() }?.let(parts::add)
                }
            }
            return parts.joinToString("  ")
        }

        class Holder(
            itemView: View,
            val title: TextView,
            val meta: TextView,
            val download: AppCompatImageButton
        ) : RecyclerView.ViewHolder(itemView)
    }

    private class ReadMenuBookmarkAdapter(
        private val onBookmarkClick: (Bookmark) -> Unit
    ) : RecyclerView.Adapter<ReadMenuBookmarkAdapter.Holder>() {

        private val bookmarks = arrayListOf<Bookmark>()

        fun submit(items: List<Bookmark>) {
            bookmarks.clear()
            bookmarks.addAll(items)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val context = parent.context
            val row = LinearLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                minimumHeight = 68.dpToPx()
                orientation = LinearLayout.VERTICAL
                setPadding(4.dpToPx(), 8.dpToPx(), 4.dpToPx(), 8.dpToPx())
            }
            val chapter = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                includeFontPadding = false
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                textSize = 15f
            }
            val bookText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 5.dpToPx()
                }
                includeFontPadding = false
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                textSize = 13f
            }
            val note = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 4.dpToPx()
                }
                includeFontPadding = false
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                textSize = 12f
            }
            row.addView(chapter)
            row.addView(bookText)
            row.addView(note)
            return Holder(row, chapter, bookText, note)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val bookmark = bookmarks[position]
            val context = holder.chapter.context
            val selected = bookmark.chapterIndex == ReadBook.durChapterIndex
            val primaryText = when {
                selected -> context.accentColor
                AppConfig.isNightTheme -> ColorUtils.adjustAlpha(Color.WHITE, 0.86f)
                else -> ColorUtils.adjustAlpha(Color.BLACK, 0.82f)
            }
            val secondaryText = when {
                selected -> ColorUtils.adjustAlpha(context.accentColor, 0.72f)
                AppConfig.isNightTheme -> ColorUtils.adjustAlpha(Color.WHITE, 0.56f)
                else -> ColorUtils.adjustAlpha(Color.BLACK, 0.50f)
            }
            holder.chapter.text = bookmark.chapterName.ifBlank {
                context.getString(R.string.chapter)
            }
            holder.chapter.setTextColor(primaryText)
            holder.bookText.text = bookmark.bookText.trim()
            holder.bookText.setTextColor(secondaryText)
            holder.bookText.isGone = bookmark.bookText.isBlank()
            holder.note.text = bookmark.content.trim()
            holder.note.setTextColor(secondaryText)
            holder.note.isGone = bookmark.content.isBlank()
            holder.itemView.setOnClickListener {
                onBookmarkClick(bookmark)
            }
        }

        override fun getItemCount(): Int = bookmarks.size

        class Holder(
            itemView: View,
            val chapter: TextView,
            val bookText: TextView,
            val note: TextView
        ) : RecyclerView.ViewHolder(itemView)
    }

}
