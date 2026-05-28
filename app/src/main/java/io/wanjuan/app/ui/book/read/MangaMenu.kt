package io.wanjuan.app.ui.book.read

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import com.qmdeve.liquidglass.widget.LiquidGlassView
import io.wanjuan.app.R
import io.wanjuan.app.databinding.ViewMangaMenuBinding
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.source.getSourceType
import io.wanjuan.app.lib.dialogs.alert
import io.wanjuan.app.lib.theme.accentColor
import io.wanjuan.app.lib.theme.bottomBackground
import io.wanjuan.app.lib.theme.getPrimaryTextColor
import io.wanjuan.app.model.ReadManga
import io.wanjuan.app.ui.browser.WebViewActivity
import io.wanjuan.app.utils.ColorUtils
import io.wanjuan.app.utils.applyStatusBarPadding
import io.wanjuan.app.utils.activity
import io.wanjuan.app.utils.dpToPx
import io.wanjuan.app.utils.gone
import io.wanjuan.app.utils.invisible
import io.wanjuan.app.utils.loadAnimation
import io.wanjuan.app.utils.openUrl
import io.wanjuan.app.utils.startActivity
import io.wanjuan.app.utils.visible

class MangaMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val binding = ViewMangaMenuBinding.inflate(LayoutInflater.from(context), this, true)
    private val callBack: CallBack get() = activity as CallBack
    var canShowMenu: Boolean = false
    private val menuTopIn: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_top_in)
    }
    private val menuTopOut: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_top_out)
    }
    private var isMenuOutAnimating = false
    private var bgColor = context.bottomBackground
    private var toolbarIconColor = Color.WHITE
    private var topBarGlassStyleKey: String? = null
    private val boundTopBarGlassViewIds = hashSetOf<Int>()

    private val menuOutListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            isMenuOutAnimating = true
            binding.vwMenuBg.setOnClickListener(null)
        }

        override fun onAnimationEnd(animation: Animation) {
            this@MangaMenu.invisible()
            binding.titleBarShell.invisible()
            binding.bottomMenu.invisible()
            isMenuOutAnimating = false
            canShowMenu = false
            callBack.upSystemUiVisibility(false)
        }

        override fun onAnimationRepeat(animation: Animation) = Unit
    }
    private val menuInListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            binding.tvSourceAction.text =
                ReadManga.bookSource?.bookSourceName ?: context.getString(R.string.book_source)
            callBack.upSystemUiVisibility(true)
            binding.tvSourceAction.isGone = false
            updateTopBarLoginAction()
            syncToolbarActionIconSize()
            configureTopBarFrostedGlass()
        }

        @SuppressLint("RtlHardcoded")
        override fun onAnimationEnd(animation: Animation) {
            binding.run {
                vwMenuBg.setOnClickListener { runMenuOut() }
            }
        }

        override fun onAnimationRepeat(animation: Animation) = Unit
    }

    init {
        binding.titleBar.applyStatusBarPadding(withInitialPadding = true)
        initView()
        bindEvent()
    }

    private fun initView() = binding.run {
        initAnimation()
        val textColor = context.getPrimaryTextColor(ColorUtils.isColorLight(bgColor))
        val secondaryTextColor = ColorUtils.withAlpha(textColor, 0.78f)
        toolbarIconColor = Color.WHITE
        configureCompactMangaToolbar()
        setupTopBarLoginAction()
        titleBar.setTextColor(textColor)
        titleBar.setColorFilter(toolbarIconColor)
        tvChapterName.setTextColor(secondaryTextColor)
        tvChapterUrl.setTextColor(secondaryTextColor)
        if (AppConfig.isEInkMode) {
            titleBarGlassView.gone()
            titleBarShellOverlay.gone()
            titleBar.setBackgroundResource(R.drawable.bg_eink_border_bottom)
            titleBar.toolbar.background = null
            titleBarAddition.background = null
            llTitleInfo.background = null
        } else {
            titleBar.setBackgroundColor(Color.TRANSPARENT)
            titleBar.toolbar.background = null
            titleBarAddition.background = null
            llTitleInfo.background = null
            configureTopBarFrostedGlass()
        }
        if (AppConfig.showReadTitleBarAddition) {
            titleBarAddition.visible()
        } else {
            titleBarAddition.gone()
        }
        /**
         * 确保视图不被导航栏遮挡
         */
    }

    private fun configureCompactMangaToolbar() = binding.run {
        titleBar.toolbar.updateLayoutParams<ViewGroup.LayoutParams> {
            height = topBarButtonSize()
        }
        titleBarAddition.updateLayoutParams<ViewGroup.LayoutParams> {
            height = topBarButtonSize()
        }
        titleBar.toolbar.minimumHeight = topBarButtonSize()
        titleBar.toolbar.contentInsetStartWithNavigation = 0
        titleBar.toolbar.contentInsetEndWithActions = 0
        titleBar.toolbar.navigationIcon = toolbarLucideIcon(R.drawable.ic_lucide_arrow_left)
        titleBar.toolbar.overflowIcon = toolbarLucideIcon(R.drawable.ic_lucide_more_vertical)
        syncToolbarActionIconSize()
    }

    private fun toolbarLucideIcon(@DrawableRes resId: Int): Drawable? {
        return AppCompatResources.getDrawable(context, resId)?.mutate()?.also(::fitToolbarIcon)
    }

    private fun syncToolbarActionIconSize() = binding.run {
        titleBar.toolbar.navigationIcon?.let(::fitToolbarIcon)
        titleBar.toolbar.overflowIcon?.let(::fitToolbarIcon)
        titleBar.toolbar.menu.children.forEach { item ->
            item.icon = item.icon?.mutate()?.also(::fitToolbarIcon)
        }
    }

    private fun fitToolbarIcon(drawable: Drawable) {
        val iconSize = topBarIconSize()
        drawable.setBounds(0, 0, iconSize, iconSize)
        drawable.colorFilter = PorterDuffColorFilter(toolbarIconColor, PorterDuff.Mode.SRC_ATOP)
    }

    fun refreshMenuColorFilter() {
        toolbarIconColor = Color.WHITE
        binding.titleBar.setColorFilter(toolbarIconColor)
        syncToolbarActionIconSize()
    }

    private fun setupTopBarLoginAction() = binding.run {
        val loginItem = titleBar.menu.findItem(R.id.menu_login)
            ?: titleBar.menu.add(0, R.id.menu_login, 0, R.string.login).apply {
                setIcon(R.drawable.ic_lucide_link_2)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
        loginItem.setIcon(R.drawable.ic_lucide_link_2)
        loginItem.title = context.getString(R.string.login)
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
        val source = ReadManga.bookSource
        val hasLoginUrl = !source?.loginUrl.isNullOrBlank()
        titleBar.menu.findItem(R.id.menu_login)?.run {
            isVisible = source != null
            setIcon(if (hasLoginUrl) R.drawable.ic_lucide_user else R.drawable.ic_lucide_link_2)
            title = context.getString(
                if (hasLoginUrl) R.string.login else R.string.open_in_app_webview
            )
        }
        syncToolbarActionIconSize()
    }

    private fun topBarButtonSize(): Int {
        return resources.getDimensionPixelSize(R.dimen.read_top_bar_button_size)
    }

    private fun topBarIconSize(): Int {
        return resources.getDimensionPixelSize(R.dimen.read_top_bar_icon_size)
    }

    private fun configureTopBarFrostedGlass() = binding.run {
        if (AppConfig.isEInkMode) {
            return@run
        }
        val glassLevel = (AppConfig.frostedGlassLevel / 100f).coerceIn(0.45f, 1f)
        val glassStyleKey =
            "manga-top:${topBarUseDarkGlass()}:${context.accentColor}:${AppConfig.frostedGlassLevel}:$bgColor"
        if (topBarGlassStyleKey != glassStyleKey) {
            titleBarShell.background = topBarGlassFallbackShell(glassLevel)
            titleBarGlassView.visible()
            titleBarShellOverlay.visible()
            titleBarShellOverlay.background = topBarGlassShell(glassLevel)
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
        val target = topBarGlassTarget()
        if (!target.isLaidOut || !titleBarShell.isLaidOut || syncTopBarGlassLayerHeight() <= 0) {
            return@run
        }
        setupTopBarFrostedGlassView(
            liquidGlassView = titleBarGlassView,
            target = target,
            cornerRadius = 0f,
            refractionHeight = (12f + glassLevel * 8f).dpToPx(),
            refractionOffset = (34f + glassLevel * 18f).dpToPx(),
            blurRadius = (22f + glassLevel * 30f).dpToPx(),
            dispersion = (0.18f + glassLevel * 0.16f).coerceAtMost(0.42f),
            tintAlpha = topBarGlassTintAlpha(glassLevel),
            tintColor = topBarGlassTintColor()
        )
    }

    private fun topBarGlassTarget(): ViewGroup {
        return activity?.findViewById<ViewGroup>(R.id.webtoon_frame)
            ?: parent as? ViewGroup
            ?: binding.vwMenuRoot
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
        }
    }

    private fun setupTopBarFrostedGlassView(
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
        if (boundTopBarGlassViewIds.add(liquidGlassView.id)) {
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

    private fun topBarGlassShell(glassLevel: Float): GradientDrawable {
        val isDark = topBarUseDarkGlass()
        val surfaceColor = if (isDark) {
            Color.rgb(8, 10, 14)
        } else {
            ColorUtils.blendColors(Color.WHITE, context.accentColor, 0.05f)
        }
        val topAlpha = if (isDark) 0.34f + glassLevel * 0.12f else 0.16f + glassLevel * 0.09f
        val centerAlpha = if (isDark) 0.28f + glassLevel * 0.10f else 0.11f + glassLevel * 0.07f
        val bottomAlpha = if (isDark) 0.20f + glassLevel * 0.08f else 0.07f + glassLevel * 0.05f
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                ColorUtils.withAlpha(surfaceColor, topAlpha.coerceAtMost(if (isDark) 0.46f else 0.25f)),
                ColorUtils.withAlpha(surfaceColor, centerAlpha.coerceAtMost(if (isDark) 0.38f else 0.18f)),
                ColorUtils.withAlpha(surfaceColor, bottomAlpha.coerceAtMost(if (isDark) 0.28f else 0.12f))
            )
        )
    }

    private fun topBarGlassFallbackShell(glassLevel: Float): GradientDrawable {
        val isDark = topBarUseDarkGlass()
        val surfaceColor = if (isDark) {
            Color.rgb(8, 10, 14)
        } else {
            ColorUtils.blendColors(Color.WHITE, context.accentColor, 0.05f)
        }
        val alpha = if (isDark) 0.36f + glassLevel * 0.10f else 0.14f + glassLevel * 0.10f
        return GradientDrawable().apply {
            setColor(ColorUtils.withAlpha(surfaceColor, alpha.coerceAtMost(if (isDark) 0.46f else 0.24f)))
        }
    }

    private fun topBarGlassTintAlpha(glassLevel: Float): Float {
        return if (topBarUseDarkGlass()) {
            (0.12f + glassLevel * 0.14f).coerceAtMost(0.26f)
        } else {
            (0.06f + glassLevel * 0.10f).coerceAtMost(0.16f)
        }
    }

    private fun topBarGlassTintColor(): FloatArray {
        return if (topBarUseDarkGlass()) {
            floatArrayOf(0.08f, 0.10f, 0.14f)
        } else {
            floatArrayOf(0.70f, 0.79f, 0.86f)
        }
    }

    private fun topBarUseDarkGlass(): Boolean {
        return AppConfig.isNightTheme && !AppConfig.isEInkMode
    }

    private fun initAnimation() {
        menuTopIn.setAnimationListener(menuInListener)
        menuTopOut.setAnimationListener(menuOutListener)
    }

    fun runMenuOut(anim: Boolean = !AppConfig.isEInkMode) {
        if (isMenuOutAnimating) {
            return
        }
        if (this.isVisible) {
            if (anim) {
                binding.titleBarShell.startAnimation(menuTopOut)
            } else {
                menuOutListener.onAnimationStart(menuTopOut)
                menuOutListener.onAnimationEnd(menuTopOut)
            }
        }
    }

    fun runMenuIn(anim: Boolean = !AppConfig.isEInkMode) {
        this.visible()
        binding.titleBarShell.visible()
        if (anim) {
            binding.titleBarShell.startAnimation(menuTopIn)
        } else {
            menuInListener.onAnimationStart(menuTopIn)
            menuInListener.onAnimationEnd(menuTopIn)
        }
    }


    private fun bindEvent() = binding.run {
        vwMenuBg.setOnClickListener { runMenuOut() }
        titleBar.toolbar.setOnClickListener {
            callBack.openBookInfoActivity()
        }
        val chapterViewClickListener = OnClickListener {
            val url = tvChapterUrl.tag as? String ?: return@OnClickListener
            if (url.isBlank()) return@OnClickListener
            context.startActivity<WebViewActivity> {
                val bookSource = ReadManga.bookSource
                putExtra("title", tvChapterName.text)
                putExtra("url", url)
                putExtra("sourceOrigin", bookSource?.bookSourceUrl)
                putExtra("sourceName", bookSource?.bookSourceName)
                putExtra("sourceType", bookSource?.getSourceType())
            }
        }
        val chapterViewLongClickListener = OnLongClickListener {
            val url = tvChapterUrl.tag as? String ?: return@OnLongClickListener true
            if (url.isNotBlank()) {
                context.alert(R.string.open_fun) {
                    setMessage(R.string.use_browser_open)
                    okButton {
                        context.openUrl(url)
                    }
                    noButton()
                }
            }
            true
        }
        tvChapterName.setOnClickListener(chapterViewClickListener)
        tvChapterName.setOnLongClickListener(chapterViewLongClickListener)
        tvChapterUrl.setOnClickListener(chapterViewClickListener)
        tvChapterUrl.setOnLongClickListener(chapterViewLongClickListener)

    }

    fun upBookView() = binding.run {
        syncToolbarActionIconSize()
        updateTopBarLoginAction()
        titleBar.title = null
        tvChapterName.text = ReadManga.book?.name.orEmpty()
        tvChapterName.visible(tvChapterName.text.isNotBlank())
        ReadManga.curMangaChapter?.let {
            tvChapterUrl.text = it.chapter.title
            tvChapterUrl.tag = it.chapter.getAbsoluteURL()
            tvChapterUrl.visible()
        } ?: let {
            tvChapterUrl.tag = null
            tvChapterUrl.gone()
        }
    }

    interface CallBack {
        fun openBookInfoActivity()
        fun upSystemUiVisibility(menuIsVisible: Boolean)
        fun showLogin()
    }
}
