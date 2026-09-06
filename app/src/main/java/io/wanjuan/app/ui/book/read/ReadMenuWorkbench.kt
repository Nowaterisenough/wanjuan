package io.wanjuan.app.ui.book.read

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.wanjuan.app.R
import io.wanjuan.app.constant.EventBus
import io.wanjuan.app.constant.PageAnim
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.BookChapter
import io.wanjuan.app.data.entities.Bookmark
import io.wanjuan.app.help.book.BookHelp
import io.wanjuan.app.help.book.ContentProcessor
import io.wanjuan.app.help.config.BuiltInReadFonts
import io.wanjuan.app.ui.font.ReaderFontLibrary
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.config.ReadBookConfig
import io.wanjuan.app.help.glide.ImageLoader
import io.wanjuan.app.model.ReadAloud
import io.wanjuan.app.model.ReadBook
import io.wanjuan.app.service.BaseReadAloudService
import io.wanjuan.app.ui.book.searchContent.SearchResult
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.getPrefBoolean
import io.wanjuan.app.utils.getPrefInt
import io.wanjuan.app.utils.postEvent
import io.wanjuan.app.utils.putPrefBoolean
import io.wanjuan.app.utils.putPrefInt
import io.wanjuan.app.utils.putPrefString
import io.wanjuan.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/** The reader's persistent navigation and its independently scrolling tool panels. */
class ReadMenuWorkbench(
    context: Context,
    private val callbacks: ReadMenu.CallBack,
    private val dismissMenu: ((() -> Unit)?) -> Unit,
    private val advanced: (Advanced) -> Unit,
    private val setBrightness: (Int) -> Unit
) : LinearLayout(context) {
    enum class Page { MAIN, SEARCH, TOC, ALOUD, LAYOUT, TURN, BACKGROUND, THEME, SETTINGS, SCOPE, SAVE, AUTO, TOUCH, FONTS, LAYOUT_DETAILS, TURN_DETAILS, BACKGROUND_DETAILS, THEME_LIBRARY, ALOUD_DETAILS, SETTINGS_DETAILS, PROGRESS }
    enum class Advanced { FONT_IMPORT, FONT, BODY, TITLE, HEADER, FOOTER, BACKGROUND, TEXT_COLOR, SETTINGS, VOICE, REPLACE }

    private data class Palette(val surface: Int, val text: Int, val secondary: Int, val line: Int, val well: Int, val selected: Int, val accentText: Int)
    private val brand = Color.rgb(0, 110, 255)
    private val palette: Palette get() = when {
        AppConfig.isEInkMode -> Palette(Color.WHITE, Color.BLACK, Color.DKGRAY, Color.GRAY, Color.WHITE, 0xffdddddd.toInt(), Color.BLACK)
        AppConfig.isNightTheme -> Palette(0xff282f35.toInt(), 0xffe0e5e5.toInt(), 0xffa0abb0.toInt(), 0xff414a50.toInt(), 0xff333c43.toInt(), 0xff263e60.toInt(), 0xff80b6ff.toInt())
        else -> Palette(0xfff8faf8.toInt(), 0xff303934.toInt(), 0xff77817e.toInt(), 0xffdce1dd.toInt(), 0xffedf1ee.toInt(), 0xffe5efff.toInt(), brand)
    }
    private val accent get() = if (AppConfig.isEInkMode) Color.BLACK else brand
    val surfaceColor get() = palette.surface
    var page = Page.MAIN
        private set
    val expanded get() = page != Page.MAIN
    private var lastAppearance = Page.LAYOUT
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var dataJob: Job? = null
    private var tracking = false
    private var pendingRefresh = false
    private var currentBookUrl: String? = null
    private var undoConfig: ReadBookConfig.AppearanceSnapshot? = null
    private var query = ""
    private var regexSearch = false
    private var currentChapterOnly = false
    private var searchResults = emptyList<SearchResult>()
    private var searchMessage = "输入关键词，搜索已缓存的章节"
    private var searchOrigin: Triple<String, Int, Int>? = null
    private var chapters = emptyList<BookChapter>()
    private var cachedChapters = emptySet<Int>()
    private var bookmarks = emptyList<Bookmark>()
    private var showBookmarks = false
    private var chapterQuery = ""
    private var reverseChapters = false
    private var backgroundCategory = 1
    private var touchTrial = false
    private var autoRunning = false
    private var chapterProgress = false
    private var managingFonts = false
    private var list: RecyclerView? = null
    private var listAdapter: RowsAdapter? = null
    private var content: LinearLayout? = null
    private var searchCount: TextView? = null
    private var saveName: EditText? = null
    private var saveTypography = true
    private var saveBackground = true
    private var saveTurning = true
    private val panelHost = FrameLayout(context)
    private val dockHost = FrameLayout(context)
    private val navigationDivider = View(context)
    private val navigationItems = ArrayList<LinearLayout>()
    private val updatePanelVisibility = Runnable {
        callbacks.onReadMenuExpandedPanelVisibilityChanged(expanded)
    }

    init {
        orientation = VERTICAL
        clipToOutline = true
        elevation = dp(8).toFloat()
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        addView(panelHost, LayoutParams(-1, -2))
        addView(dockHost, LayoutParams(-1, -2))
        addView(navigationDivider, LayoutParams(-1, dp(1)))
        buildNavigation()
        refresh()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (scope.coroutineContext[Job]?.isActive != true) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        refresh()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(updatePanelVisibility)
        (parent as? ViewGroup)?.let(TransitionManager::endTransitions)
        scope.cancel()
        super.onDetachedFromWindow()
    }

    fun show(next: Page, animate: Boolean = true) {
        hideKeyboard()
        dataJob?.cancel()
        if (animate && isLaidOut && !AppConfig.isEInkMode && page != next) {
            // Animate the panel above the permanent dock; navigation views keep their identity.
            TransitionManager.beginDelayedTransition(parent as? ViewGroup ?: this,
                TransitionSet().addTransition(ChangeBounds()).addTransition(Fade())
                    .setDuration(220).excludeTarget(navigationDivider, true))
        }
        page = next
        if (next in appearancePages) lastAppearance = next
        if (currentBookUrl != ReadBook.book?.bookUrl) {
            currentBookUrl = ReadBook.book?.bookUrl
            query = ""
            searchResults = emptyList()
            searchOrigin = null
            chapters = emptyList()
            bookmarks = emptyList()
            undoConfig = null
        }
        if (undoConfig == null) captureUndo()
        refresh()
        callbacks.onReadMenuExpandedPanelVisibilityChanged(expanded)
        // The minimap uses the final panel bounds after the expansion animation.
        removeCallbacks(updatePanelVisibility)
        postDelayed(updatePanelVisibility, if (animate && !AppConfig.isEInkMode) 240L else 32L)
        if (next == Page.TOC) loadChapters()
    }

    fun beginSession() {
        undoConfig = null
        show(Page.MAIN, animate = false)
    }

    fun goBack() = show(if (page in detailPages) parentPage() else Page.MAIN)

    fun refresh() {
        if (tracking) { pendingRefresh = true; return }
        pendingRefresh = false
        panelHost.removeAllViews()
        dockHost.removeAllViews()
        list = null
        listAdapter = null
        content = null
        background = rounded(palette.surface, 24, palette.line)
        (parent as? View)?.setPadding(dp(11), 0, dp(11), dp(10))
        panelHost.isVisible = expanded
        dockHost.isVisible = !expanded
        if (page == Page.MAIN) {
            buildDock()
        } else {
            buildPanel()
        }
        navigationDivider.setBackgroundColor(palette.line)
        updateNavigation()
    }

    fun setAutoRunning(running: Boolean) {
        autoRunning = running
        if (page == Page.AUTO || page == Page.TURN) refresh()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun text(value: CharSequence, size: Int = 14, color: Int = palette.text, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun column() = LinearLayout(context).apply { orientation = VERTICAL }
    private fun row() = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun rounded(fill: Int, radius: Int = 10, border: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (border != Color.TRANSPARENT) setStroke(dp(1), border)
    }
    private fun icon(res: Int, label: String, tint: Int = palette.secondary, onClick: (() -> Unit)? = null) = ImageView(context).apply {
        setImageResource(res)
        setColorFilter(tint)
        setPadding(dp(11), dp(11), dp(11), dp(11))
        contentDescription = label
        if (onClick != null) { isFocusable = true; setOnClickListener { onClick() } }
    }
    private fun addDivider(parent: LinearLayout, margin: Int = 7) {
        parent.addView(View(context).apply { setBackgroundColor(palette.line) }, LayoutParams(-1, dp(1)).apply { topMargin = dp(margin); bottomMargin = dp(margin) })
    }
    private fun section(parent: LinearLayout, name: String, action: String? = null, onClick: (() -> Unit)? = null) {
        val line = row()
        line.addView(text(name, 14, bold = true), LayoutParams(0, dp(36), 1f))
        if (action != null) line.addView(text(action, 12, palette.secondary).apply {
            minHeight = dp(44); setPadding(dp(8), 0, 0, 0); setOnClickListener { onClick?.invoke() }
        })
        parent.addView(line)
    }
    private fun actionRow(parent: LinearLayout, title: String, summary: String = "", onClick: () -> Unit) {
        val line = row().apply { minimumHeight = dp(44); setOnClickListener { onClick() }; isFocusable = true }
        line.addView(text(title), LayoutParams(0, -2, 1f))
        line.addView(text(summary, 12, palette.secondary).apply { maxLines = 1; maxWidth = dp(145) })
        line.addView(text("›", 22, palette.secondary).apply { gravity = Gravity.CENTER }, LayoutParams(dp(23), dp(44)))
        parent.addView(line)
    }
    private fun switchRow(parent: LinearLayout, title: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
        val line = row().apply { minimumHeight = dp(44) }
        line.addView(text(title), LayoutParams(0, -2, 1f))
        val toggle = SwitchCompat(context).apply {
            isChecked = checked
            contentDescription = title
            thumbTintList = ColorStateList.valueOf(if (AppConfig.isEInkMode) Color.BLACK else Color.WHITE)
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(accent, palette.line))
            minHeight = dp(44)
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        }
        line.addView(toggle)
        line.setOnClickListener { toggle.isChecked = !toggle.isChecked }
        parent.addView(line)
    }
    private fun slider(parent: LinearLayout, title: String, value: Int, min: Int, max: Int, format: (Int) -> String = { "$it" }, change: (Int) -> Unit) {
        val line = row()
        line.addView(text(title, 13), LayoutParams(dp(59), dp(44)))
        val number = text(format(value), 12, palette.secondary).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        val seek = SeekBar(context).apply {
            this.max = max - min
            progress = value.coerceIn(min, max) - min
            contentDescription = title
            progressTintList = ColorStateList.valueOf(accent)
            progressBackgroundTintList = ColorStateList.valueOf(palette.line)
            thumbTintList = ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) { tracking = true }
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    number.text = format(progress + min)
                    if (fromUser) change(progress + min)
                }
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    tracking = false
                    if (pendingRefresh) post { refresh() }
                }
            })
        }
        line.addView(seek, LayoutParams(0, dp(44), 1f))
        line.addView(number, LayoutParams(dp(43), dp(44)))
        parent.addView(line)
    }
    private fun button(parent: LinearLayout, title: String, filled: Boolean = false, onClick: () -> Unit) {
        parent.addView(text(title, 14, if (filled) Color.WHITE else palette.accentText, true).apply {
            gravity = Gravity.CENTER
            background = rounded(if (filled) accent else palette.selected, 10)
            setOnClickListener { onClick() }
            isFocusable = true
        }, LayoutParams(-1, dp(46)).apply { topMargin = dp(12) })
    }
    private fun note(parent: LinearLayout, value: String) {
        parent.addView(text(value, 12, palette.secondary).apply {
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(palette.well)
        }, LayoutParams(-1, -2).apply { topMargin = dp(15) })
    }

    private fun buildNavigation() {
        val nav = row().apply { setPadding(dp(10), dp(8), dp(10), dp(9)) }
        val names = listOf("搜索", "目录", "朗读", "外观", "设置")
        val icons = listOf(R.drawable.ic_lucide_search, R.drawable.ic_lucide_book_open_text, R.drawable.ic_lucide_headphones, R.drawable.ic_lucide_sliders_horizontal, R.drawable.ic_lucide_settings)
        names.forEachIndexed { index, name ->
            val tab = column().apply {
                gravity = Gravity.CENTER
                contentDescription = name
                isFocusable = true
                setOnClickListener {
                    val target = listOf(Page.SEARCH, Page.TOC, Page.ALOUD, lastAppearance, Page.SETTINGS)[index]
                    show(if (selectedNavigation() == index) Page.MAIN else target)
                }
            }
            tab.addView(icon(icons[index], name).apply {
                setPadding(dp(6), dp(5), dp(6), dp(3))
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(dp(30), dp(28)))
            tab.addView(text(name, 11).apply {
                gravity = Gravity.CENTER
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(-1, dp(19)))
            val slot = FrameLayout(context)
            slot.addView(tab, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER))
            nav.addView(slot, LayoutParams(0, dp(52), 1f))
            navigationItems.add(tab)
        }
        addView(nav)
    }

    private fun selectedNavigation() = when (page) {
        Page.SEARCH -> 0
        Page.TOC -> 1
        Page.ALOUD, Page.ALOUD_DETAILS -> 2
        Page.MAIN, Page.PROGRESS -> -1
        Page.SETTINGS, Page.SETTINGS_DETAILS, Page.TOUCH -> 4
        else -> 3
    }

    private fun updateNavigation() {
        navigationItems.forEachIndexed { index, tab ->
            val selected = selectedNavigation() == index
            tab.isSelected = selected
            tab.background = if (selected) rounded(accent, 14) else null
            (tab.getChildAt(0) as ImageView).setColorFilter(if (selected) Color.WHITE else palette.secondary)
            (tab.getChildAt(1) as TextView).setTextColor(if (selected) Color.WHITE else palette.secondary)
        }
    }

    private fun buildDock() {
        val body = row().apply { setPadding(dp(12), dp(3), dp(12), dp(3)) }
        body.addView(icon(R.drawable.ic_lucide_sun, "切换日间或夜间", palette.secondary) {
            AppConfig.isNightTheme = !AppConfig.isNightTheme
            post { changed(true); callbacks.upSystemUiVisibility() }
        }, LayoutParams(dp(38), dp(44)))
        val bright = column()
        slider(bright, "", AppConfig.readBrightness.coerceIn(1, 255), 1, 255, { "" }) {
            context.putPrefBoolean("brightnessAuto", false)
            AppConfig.readBrightness = it
            setBrightness(it)
        }
        val sliderRow = bright.getChildAt(0) as LinearLayout
        sliderRow.getChildAt(0).visibility = GONE
        sliderRow.getChildAt(2).visibility = GONE
        body.addView(bright, LayoutParams(0, dp(44), 1f))
        body.addView(text("自动", 11, if (context.getPrefBoolean("brightnessAuto", true)) palette.accentText else palette.secondary).apply {
            gravity = Gravity.CENTER
            setOnClickListener {
                context.putPrefBoolean("brightnessAuto", !context.getPrefBoolean("brightnessAuto", true))
                setBrightness(AppConfig.readBrightness); refresh()
            }
        }, LayoutParams(dp(42), dp(44)))
        val ratio = (ReadBook.durChapterIndex + (ReadBook.durPageIndex + 1f) / (ReadBook.curTextChapter?.pageSize ?: 1).coerceAtLeast(1)) / ReadBook.chapterSize.coerceAtLeast(1)
        body.addView(text(String.format(Locale.ROOT, "%.1f%% ›", ratio * 100), 11, palette.secondary).apply {
            gravity = Gravity.CENTER
            background = rounded(palette.well, 8)
            contentDescription = "阅读进度，点击调整"
            setOnClickListener { show(Page.PROGRESS) }
        }, LayoutParams(dp(74), dp(32)).apply { marginStart = dp(7) })
        dockHost.addView(body, FrameLayout.LayoutParams(-1, -2))
    }

    private fun buildProgress(body: LinearLayout) {
        val total = ReadBook.chapterSize.coerceAtLeast(1)
        val pages = ReadBook.curTextChapter?.pageSize?.coerceAtLeast(1) ?: 1
        val inChapter = (ReadBook.durPageIndex + 1f) / pages
        val ratio = if (chapterProgress) inChapter else (ReadBook.durChapterIndex + inChapter) / total
        slider(body, if (chapterProgress) "本章 ▾" else "全书 ▾", (ratio * 10000).roundToInt(), 0, 10000,
            { String.format(Locale.ROOT, "%.1f%%", it / 100f) }) {}
        // Commit navigation when the user releases the scrubber, not while dragging.
        val progressRow = body.getChildAt(0) as LinearLayout
        progressRow.getChildAt(0).setOnClickListener { chapterProgress = !chapterProgress; refresh() }
        val seek = progressRow.getChildAt(1) as SeekBar
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) { tracking = true }
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                (progressRow.getChildAt(2) as TextView).text = String.format(Locale.ROOT, "%.1f%%", value / 100f)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                tracking = false
                val target = seekBar.progress / 10000f
                if (chapterProgress) {
                    ReadBook.skipToPage((target * pages).toInt().coerceIn(0, pages - 1))
                } else {
                    val position = target * total
                    val chapter = position.toInt().coerceIn(0, total - 1)
                    ReadBook.openChapter(chapter) {
                        val count = ReadBook.curTextChapter?.pageSize?.coerceAtLeast(1) ?: 1
                        ReadBook.skipToPage(((position - chapter) * count).toInt().coerceIn(0, count - 1))
                    }
                }
                post { refresh() }
            }
        })

        actionRow(body, "上一章") { ReadBook.moveToPrevChapter(upContent = true, toLast = false) }
        actionRow(body, "下一章") { ReadBook.moveToNextChapter(true) }
        note(body, "拖动后松手跳转。右侧缩略条可直接定位本章内容。")
    }

    private fun buildPanel() {
        val panel = column()
        panelHost.addView(panel, FrameLayout.LayoutParams(-1, -2))
        panel.addView(View(context).apply { background = rounded(palette.line, 2) }, LayoutParams(dp(24), dp(3)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(9)
        })
        val header = row().apply { setPadding(dp(18), 0, dp(10), 0) }
        val appearance = page in appearancePages
        val titles = mapOf(Page.SEARCH to "全文搜索", Page.TOC to "目录与书签", Page.ALOUD to "朗读", Page.SETTINGS to "阅读设置", Page.SCOPE to "应用范围", Page.SAVE to "保存为主题", Page.AUTO to "自动翻页", Page.TOUCH to "点击区域", Page.FONTS to "字体", Page.LAYOUT_DETAILS to "排版细节", Page.TURN_DETAILS to "翻页设置", Page.BACKGROUND_DETAILS to "背景调校", Page.THEME_LIBRARY to "我的主题", Page.ALOUD_DETAILS to "朗读设置", Page.SETTINGS_DETAILS to "显示与内容", Page.PROGRESS to "阅读进度")
        if (appearance) {
            val tabs = row()
            appearancePages.forEachIndexed { index, target ->
                val tab = column().apply { gravity = Gravity.CENTER; setOnClickListener { show(target) } }
                tab.addView(text(listOf("排版", "翻页", "背景", "主题")[index], 14, if (page == target) palette.accentText else palette.secondary, page == target).apply { gravity = Gravity.CENTER }, LayoutParams(-1, dp(34)))
                tab.addView(View(context).apply { background = if (page == target) rounded(accent, 1) else null }, LayoutParams(dp(16), dp(2)))
                tabs.addView(tab, LayoutParams(dp(50), dp(42)))
            }
            header.addView(tabs, LayoutParams(0, dp(42), 1f))
        } else if (page == Page.TOC) {
            listOf("目录", "书签").forEachIndexed { index, name ->
                val selected = showBookmarks == (index == 1)
                header.addView(text(name, 16, if (selected) palette.accentText else palette.secondary, selected).apply {
                    gravity = Gravity.CENTER
                    setOnClickListener { showBookmarks = index == 1; refresh() }
                }, LayoutParams(dp(58), dp(42)))
            }
            header.addView(View(context), LayoutParams(0, 1, 1f))
            header.addView(text("定位当前", 12, palette.accentText).apply {
                gravity = Gravity.CENTER; setOnClickListener { locateCurrentChapter() }
            }, LayoutParams(dp(74), dp(42)))
        } else {
            if (page in detailPages) header.addView(icon(R.drawable.ic_lucide_chevron_left, "返回") { show(parentPage()) }, LayoutParams(dp(32), dp(42)))
            header.addView(text(titles[page].orEmpty(), 16, bold = true), LayoutParams(0, dp(42), 1f))
            if (page == Page.FONTS) header.addView(text("＋ 导入", 12, palette.accentText).apply { gravity = Gravity.CENTER; setOnClickListener { advanced(Advanced.FONT_IMPORT) } }, LayoutParams(dp(65), dp(42)))
        }
        header.addView(text("×", 21, palette.secondary).apply { gravity = Gravity.CENTER; contentDescription = "关闭面板"; setOnClickListener { show(Page.MAIN) } }, LayoutParams(dp(40), dp(42)))
        panel.addView(header)
        val screenHeight = rootView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val desired = dp(if (page == Page.FONTS || page == Page.TOC || page == Page.SEARCH) 338 else 308)
        val panelHeight = desired.coerceAtMost((screenHeight * .78f).roundToInt() - dp(90)).coerceAtLeast(dp(180))
        val bodyHeight = panelHeight - dp(54 + if (appearance) 32 else 0)
        val body = column().apply { setPadding(dp(18), 0, dp(18), 0) }
        content = body
        if (page == Page.SEARCH || page == Page.TOC) {
            panel.addView(body, LayoutParams(-1, bodyHeight))
        } else {
            val scroll = ScrollView(context).apply { isFillViewport = false; isVerticalScrollBarEnabled = true; addView(body) }
            panel.addView(scroll, LayoutParams(-1, bodyHeight))
        }
        when (page) {
            Page.LAYOUT -> buildTypography(body)
            Page.TURN -> buildTurning(body)
            Page.BACKGROUND -> buildBackground(body)
            Page.THEME -> buildThemes(body)
            Page.SETTINGS -> buildSettings(body)
            Page.SEARCH -> buildSearch(body)
            Page.TOC -> buildToc(body)
            Page.ALOUD -> buildAloud(body)
            Page.SCOPE -> buildScope(body)
            Page.SAVE -> buildSave(body)
            Page.AUTO -> buildAuto(body)
            Page.TOUCH -> buildTouch(body)
            Page.FONTS -> buildFonts(body)
            Page.LAYOUT_DETAILS -> buildLayoutDetails(body)
            Page.TURN_DETAILS -> buildTurnDetails(body)
            Page.BACKGROUND_DETAILS -> buildBackgroundDetails(body)
            Page.THEME_LIBRARY -> buildThemeLibrary(body)
            Page.ALOUD_DETAILS -> buildAloudDetails(body)
            Page.SETTINGS_DETAILS -> buildSettingsDetails(body)
            Page.PROGRESS -> buildProgress(body)
            Page.MAIN -> Unit
        }
        if (appearance) {
            val footer = row().apply { setPadding(dp(18), 0, dp(18), 0) }
            footer.addView(text(if (ReadBookConfig.hasBookAppearance) "仅本书⌄" else "默认外观⌄", 11, palette.secondary).apply { setOnClickListener { show(Page.SCOPE) } }, LayoutParams(0, dp(32), 1f))
            footer.addView(text("恢复默认", 11, palette.secondary).apply { setOnClickListener { resetCurrentSection() } }, LayoutParams(-2, dp(32)))
            panel.addView(footer)
        }
    }

    private fun parentPage() = when (page) {
        Page.FONTS, Page.LAYOUT_DETAILS -> Page.LAYOUT
        Page.TURN_DETAILS, Page.AUTO -> Page.TURN
        Page.BACKGROUND_DETAILS -> Page.BACKGROUND
        Page.THEME_LIBRARY, Page.SAVE -> Page.THEME
        Page.ALOUD_DETAILS -> Page.ALOUD
        Page.SETTINGS_DETAILS, Page.TOUCH -> Page.SETTINGS
        Page.PROGRESS -> Page.MAIN
        else -> lastAppearance
    }

    private fun resetCurrentSection() {
        confirm("恢复默认？", "恢复当前分类的项目默认设置。其他分类保持当前配置。") {
            val defaults = ReadMenuThemeSuite.fromPreset("默认", ReadMenuThemePreset.defaultPresets().first())
            defaults.copy(includeTypography = page == Page.LAYOUT || page == Page.THEME,
                includeBackground = page == Page.BACKGROUND || page == Page.THEME,
                includeTurning = page == Page.TURN || page == Page.THEME).applyToReader()
            changed(true)
        }
    }

    private fun captureUndo() {
        undoConfig = ReadBookConfig.captureAppearance()
    }

    private fun changed(rebuild: Boolean = false, flags: ArrayList<Int> = arrayListOf(1, 2, 5, 6, 9, 11)) {
        ReadBookConfig.save()
        if (5 in flags) ReadBook.callBack?.upPageAnim(true)
        postEvent(EventBus.UP_CONFIG, flags)
        if (rebuild) refresh()
    }

    private fun buildTypography(body: LinearLayout) {
        val font = row()
        font.addView(text("字体", 12, palette.secondary), LayoutParams(dp(48), dp(44)))
        font.addView(text(currentFontName() + "  ⌄", 13).apply {
            background = rounded(palette.well, 8); setPadding(dp(11), 0, dp(9), 0)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setOnClickListener { show(Page.FONTS) }
        }, LayoutParams(0, dp(36), 1f))
        font.addView(text("＋ 导入", 12, palette.accentText).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; setOnClickListener { advanced(Advanced.FONT_IMPORT) } }, LayoutParams(dp(64), dp(44)))
        body.addView(font)
        slider(body, "字号", ReadBookConfig.textSize, 5, 70) { ReadBookConfig.textSize = it; changed() }
        val sizeRow = body.getChildAt(1) as LinearLayout
        sizeRow.addView(text("−", 20, palette.secondary).apply { gravity = Gravity.CENTER; contentDescription = "减小字号"; setOnClickListener { ReadBookConfig.textSize = (ReadBookConfig.textSize - 1).coerceAtLeast(5); changed(true) } }, 1, LayoutParams(dp(28), dp(44)))
        sizeRow.addView(text("+", 20, palette.secondary).apply { gravity = Gravity.CENTER; contentDescription = "增大字号"; setOnClickListener { ReadBookConfig.textSize = (ReadBookConfig.textSize + 1).coerceAtMost(70); changed(true) } }, LayoutParams(dp(28), dp(44)))
        slider(body, "字重", BuiltInReadFonts.targetWeight(ReadBookConfig.textWeight), 300, 900) {
            ReadBookConfig.textWeight = if (it <= 400) (it - 300) / 2 else 50 + (it - 400) / 10
            ReadBookConfig.textBold = 0
            changed()
        }
        val spacing = row()
        val values = listOf("字间距" to String.format(Locale.ROOT, "%.1f", ReadBookConfig.letterSpacing), "行间距" to "${ReadBookConfig.lineSpacingExtra}", "段间距" to "${ReadBookConfig.paragraphSpacing}")
        values.forEachIndexed { index, (label, value) ->
            spacing.addView(column().apply {
                addView(text(label, 11, palette.secondary), LayoutParams(-1, dp(21)))
                addView(text("$value ＋", 16), LayoutParams(-1, dp(27)))
                setOnClickListener {
                    if (index == 0) {
                        val editor = column().apply { setPadding(dp(20), 0, dp(20), 0) }
                        slider(editor, "字间距", (ReadBookConfig.letterSpacing * 10).roundToInt(), 0, 100, { String.format(Locale.ROOT, "%.1f", it / 10f) }) { ReadBookConfig.letterSpacing = it / 10f; changed() }
                        AlertDialog.Builder(context).setTitle("字间距").setView(editor).setPositiveButton("完成") { _, _ -> refresh() }.setOnDismissListener { refresh() }.show()
                    } else numberDialog(label, if (index == 1) ReadBookConfig.lineSpacingExtra else ReadBookConfig.paragraphSpacing, 0, 100) {
                        if (index == 1) ReadBookConfig.lineSpacingExtra = it else ReadBookConfig.paragraphSpacing = it
                        changed(true)
                    }
                }
            }, LayoutParams(0, dp(48), 1f))
        }
        body.addView(spacing)
        actionRow(body, "页面边距", "左右 ${ReadBookConfig.paddingLeft} / ${ReadBookConfig.paddingRight}") { show(Page.LAYOUT_DETAILS) }
    }

    private fun currentFontName() = if (ReadBookConfig.textFont.isBlank()) when (ReadBookConfig.systemTypeface) {
        1 -> "系统衬线"; 2 -> "系统等宽"; else -> "系统默认"
    } else ReaderFontLibrary.name(context, ReadBookConfig.textFont)

    private fun buildLayoutDetails(body: LinearLayout) {
        actionRow(body, "正文边距", "上下左右") { advanced(Advanced.BODY) }
        actionRow(body, "正文标题", "字号与间距") { advanced(Advanced.TITLE) }
        actionRow(body, "页眉", "内容与边距") { advanced(Advanced.HEADER) }
        actionRow(body, "页脚", "内容与边距") { advanced(Advanced.FOOTER) }
        actionRow(body, "系统字体选项", "默认 / 衬线 / 等宽") {
            choose("系统字体", listOf("系统默认", "系统衬线", "系统等宽")) {
                ReadBookConfig.textFont = ""; ReadBookConfig.systemTypeface = it; changed(true)
            }
        }
    }

    private fun selectReaderFont(path: String) {
        if (path.isNotBlank()) {
            runCatching { ReaderFontLibrary.typeface(context, path) }.onFailure {
                context.toastOnUi("字体不可用，请重新导入"); return
            }
        }
        ReadBookConfig.textFont = path
        ReadBookConfig.systemTypeface = 0
        changed(true)
    }

    private fun buildFonts(body: LinearLayout) {
        body.addView(text("支持 TTF / OTF · 导入后点选使用", 11, palette.secondary), LayoutParams(-1, dp(27)))
        fun addFont(name: String, detail: String, path: String) {
            val selected = ReadBookConfig.textFont == path && (path.isNotBlank() || ReadBookConfig.systemTypeface == 0)
            val line = row().apply {
                setPadding(dp(11), dp(6), dp(6), dp(6))
                background = rounded(if (selected) palette.selected else palette.well, 10, if (selected) accent else Color.TRANSPARENT)
            }
            val sample = column().apply { setOnClickListener { selectReaderFont(path) } }
            val title = text(name, 15).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
            sample.addView(title, LayoutParams(-1, dp(24)))
            sample.addView(text(detail, 11, palette.secondary), LayoutParams(-1, dp(20)))
            line.addView(sample, LayoutParams(0, -2, 1f))
            if (managingFonts && path.isNotBlank()) {
                line.addView(text("移出", 12, palette.secondary).apply { gravity = Gravity.CENTER; setOnClickListener {
                    confirm("移出字体列表？", if (selected) "当前阅读将恢复系统默认字体；其他书籍及已存主题仍可使用这个字体。" else "其他书籍及已存主题仍可使用这个字体，可以通过恢复列表再次显示。") {
                        ReaderFontLibrary.hide(context, path)
                        if (selected) selectReaderFont("") else refresh()
                    }
                } }, LayoutParams(dp(44), dp(44)))
            } else line.addView(text(if (selected) "✓" else "", 17, palette.accentText).apply { gravity = Gravity.CENTER; setOnClickListener { selectReaderFont(path) } }, LayoutParams(dp(30), dp(44)))
            body.addView(line, LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
            if (path.isNotBlank()) scope.launch {
                val face = withContext(Dispatchers.IO) { runCatching { ReaderFontLibrary.typeface(context, path) }.getOrNull() }
                if (content === body) title.typeface = face ?: Typeface.DEFAULT
            }
        }
        addFont("系统默认", "当前项目默认主题使用的字体", "")
        scope.launch {
            val fonts = withContext(Dispatchers.IO) { ReaderFontLibrary.list(context, ReadBookConfig.textFont) }
            if (page != Page.FONTS || content !== body) return@launch
            fonts.forEach { addFont(it.name, "春夜微凉，星光落在书页间", it.path) }
            if (fonts.isEmpty()) body.addView(text("还没有导入字体，点击右上角「导入」添加。", 12, palette.secondary), LayoutParams(-1, dp(46)))
            val actions = row()
            actions.addView(text("使用系统默认", 12, palette.accentText).apply { setOnClickListener { selectReaderFont("") } }, LayoutParams(0, dp(44), 1f))
            actions.addView(text(if (managingFonts) "完成管理" else "管理字体", 12, palette.secondary).apply { setOnClickListener { managingFonts = !managingFonts; refresh() } }, LayoutParams(-2, dp(44)))
            body.addView(actions)
            if (managingFonts) {
                actionRow(body, "恢复移出的字体") { ReaderFontLibrary.restoreHidden(context); refresh() }
                actionRow(body, "从文件夹选择字体") { advanced(Advanced.FONT) }
            }
        }
    }

    private fun stepper(parent: LinearLayout, label: String, initial: Int, min: Int, max: Int, step: Int, action: (Int) -> Unit) {
        var value = initial
        val block = column()
        block.addView(text(label, 13), LayoutParams(-1, dp(27)))
        val line = row().apply { background = rounded(palette.well, 8, palette.line) }
        val number = text("$value", 18).apply { gravity = Gravity.CENTER }
        listOf(-step, step).forEachIndexed { index, delta ->
            if (index == 1) line.addView(number, LayoutParams(0, dp(44), 1f))
            line.addView(text(if (delta < 0) "−" else "+", 20, palette.secondary).apply {
                gravity = Gravity.CENTER; contentDescription = "${if (delta < 0) "减小" else "增大"}$label"
                setOnClickListener { value = (value + delta).coerceIn(min, max); number.text = "$value"; action(value) }
            }, LayoutParams(dp(44), dp(44)))
        }
        block.addView(line)
        parent.addView(block, LayoutParams(0, -2, 1f).apply { marginEnd = dp(10) })
    }

    private fun buildTurning(body: LinearLayout) {
        val modes = listOf("覆盖" to PageAnim.coverPageAnim, "联动" to PageAnim.linkedCoverPageAnim, "平移" to PageAnim.slidePageAnim, "仿真" to PageAnim.simulationPageAnim, "滚动" to PageAnim.scrollPageAnim, "无动画" to PageAnim.noAnim)
        modes.chunked(3).forEach { values ->
            val line = row()
            values.forEach { (name, mode) ->
                val selected = (ReadBook.book?.getPageAnim() ?: ReadBookConfig.pageAnim) == mode
                val card = column().apply {
                    gravity = Gravity.CENTER
                    background = rounded(if (selected) palette.selected else palette.well, 10, if (selected) accent else palette.line)
                    setOnClickListener { ReadBookConfig.pageAnim = mode; ReadBook.book?.setPageAnim(null); ReadBook.saveRead(); changed(true) }
                }
                card.addView(View(context).apply {
                    background = ReadMenu.PageAnimPreviewDrawable(palette.surface, palette.secondary,
                        palette.text, accent, mode, selected, dp(1).toFloat(), dp(1).toFloat())
                }, LayoutParams(dp(52), dp(30)).apply { topMargin = dp(4) })
                card.addView(text(if (selected) "$name ✓" else name, 12, if (selected) palette.accentText else palette.text).apply { gravity = Gravity.CENTER }, LayoutParams(-1, dp(25)))
                line.addView(card, LayoutParams(0, dp(58), 1f).apply { marginEnd = dp(8) })
            }
            body.addView(line, LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        slider(body, "动画速度", ReadBookConfig.animationSpeed, 0, 2000, { "${it}ms" }) { ReadBookConfig.animationSpeed = it; changed(flags = arrayListOf(4)) }
        val options = row()
        options.addView(text(if (autoRunning) "自动翻页 · 运行中 ›" else "自动翻页 ›", 13).apply { setOnClickListener { show(Page.AUTO) } }, LayoutParams(0, dp(44), 1f))
        options.addView(text("更多设置 ›", 12, palette.secondary).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; setOnClickListener { show(Page.TURN_DETAILS) } }, LayoutParams(0, dp(44), 1f))
        body.addView(options)
    }

    private fun buildTurnDetails(body: LinearLayout) {
        switchRow(body, "音量键翻页", AppConfig.volumeKeyPage) { context.putPrefBoolean(PreferKey.volumeKeyPage, it) }
        switchRow(body, "鼠标滚轮翻页", AppConfig.mouseWheelPage) { context.putPrefBoolean(PreferKey.mouseWheelPage, it) }
        actionRow(body, "滑动触发距离", if (AppConfig.pageTouchSlop == 0) "系统默认" else "${AppConfig.pageTouchSlop}px") {
            numberDialog("滑动触发距离（0 为系统默认）", AppConfig.pageTouchSlop, 0, 9999) { AppConfig.pageTouchSlop = it; changed(true, arrayListOf(4)) }
        }
    }

    private fun buildBackground(body: LinearLayout) {
        val categories = row()
        listOf("纯色", "纸张", "图片").forEachIndexed { index, title ->
            categories.addView(text(title, 13, if (backgroundCategory == index) palette.accentText else palette.secondary).apply {
                gravity = Gravity.CENTER; if (backgroundCategory == index) background = rounded(palette.selected, 7)
                setOnClickListener { backgroundCategory = index; refresh() }
            }, LayoutParams(0, dp(40), 1f))
        }
        body.addView(categories)
        val colors = if (AppConfig.isNightTheme) listOf("墨蓝" to "#131C29", "石墨" to "#282D35", "暖灰" to "#332D27", "深绿" to "#20332B", "纯黑" to "#000000")
            else listOf("纸白" to "#F5F1E7", "暖纸" to "#EEE5D0", "青绿" to "#DCE5D4", "雾蓝" to "#DCE5E8", "纯白" to "#FFFFFF")
        val assets = if (backgroundCategory == 1) listOf("素白宣纸.jpg", "暖黄宣纸.png", "羊皮纸4.jpg", "新羊皮纸.jpg", "边彩画布.jpg", "山水墨影.jpg")
            else listOf("午后沙滩.jpg", "宁静夜色.jpg", "护眼漫绿.jpg", "清新时光.jpg", "山水画.jpg")
        val entries = if (backgroundCategory == 0) colors.map { Triple(it.first, 0, it.second) } else assets.map { Triple(it.substringBeforeLast('.'), 1, it) }
        entries.chunked(3).forEach { items ->
            val line = row()
            items.forEach { (name, type, value) ->
                val selected = ReadBookConfig.durConfig.curBgType() == type && ReadBookConfig.durConfig.curBgStr().equals(value, true)
                val card = column().apply { setOnClickListener { ReadBookConfig.durConfig.setCurBg(type, value); changed(true) } }
                val preview = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = rounded(if (type == 0) Color.parseColor(value) else palette.well, 9, if (selected) accent else palette.line)
                    clipToOutline = true
                    contentDescription = name
                    if (type == 1) ImageLoader.load(context, "file:///android_asset/bg/$value".toUri()).into(this)
                    if (AppConfig.isNightTheme && type == 1) alpha = .65f
                }
                card.addView(preview, LayoutParams(-1, dp(42)))
                card.addView(text(if (selected) "$name ✓" else name, 11, if (selected) palette.accentText else palette.secondary).apply { gravity = Gravity.CENTER; maxLines = 1 }, LayoutParams(-1, dp(23)))
                line.addView(card, LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
            }
            repeat(3 - items.size) { line.addView(View(context), LayoutParams(0, 1, 1f)) }
            body.addView(line, LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
        actionRow(body, "自选图片与颜色", "更多调校") { show(Page.BACKGROUND_DETAILS) }
    }

    private fun buildBackgroundDetails(body: LinearLayout) {
        actionRow(body, "选择图片或自定义颜色") { advanced(Advanced.BACKGROUND) }
        slider(body, "亮度", ReadBookConfig.bgBrightness, 0, 100, { "$it%" }) { ReadBookConfig.bgBrightness = it; changed(flags = arrayListOf(1, 3)) }
        slider(body, "饱和度", ReadBookConfig.bgSaturation, 0, 100, { "$it%" }) { ReadBookConfig.bgSaturation = it; changed(flags = arrayListOf(1, 3)) }
        slider(body, "透明度", ReadBookConfig.bgAlpha, 0, 100, { "$it%" }) { ReadBookConfig.bgAlpha = it; changed(flags = arrayListOf(3)) }
        actionRow(body, "文字颜色") { advanced(Advanced.TEXT_COLOR) }
    }

    private fun buildThemes(body: LinearLayout) {
        val modes = row()
        listOf("浅色", "深色", "跟随系统").forEachIndexed { index, name ->
            val mode = listOf("1", "2", "0")[index]
            val selected = AppConfig.themeMode == mode
            modes.addView(text(name, 12, if (selected) palette.accentText else palette.secondary).apply {
                gravity = Gravity.CENTER; background = rounded(if (selected) palette.selected else palette.well, 8)
                setOnClickListener {
                    context.putPrefString(PreferKey.themeMode, mode)
                    post { changed(true); callbacks.upSystemUiVisibility() }
                }
            }, LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(5) })
        }
        body.addView(modes, LayoutParams(-1, -2).apply { topMargin = dp(5); bottomMargin = dp(10) })
        val presets = if (AppConfig.isNightTheme) listOf(Triple("静夜", "#1E2528", "#BEC5C4"), Triple("石墨", "#292E32", "#CDD3D5"), Triple("暖夜", "#322B25", "#CFC1AB"))
            else listOf(Triple("暖纸", "#F1EBDD", "#464B44"), Triple("青庭", "#E7EDE5", "#47584A"), Triple("素白", "#F8FAF8", "#303934"))
        val cards = row()
        presets.forEach { (name, bg, ink) ->
            val selected = ReadBookConfig.durConfig.curBgType() == 0 && ReadBookConfig.durConfig.curBgStr().equals(bg, true)
            val card = column().apply {
                background = rounded(palette.well, 10, if (selected) accent else palette.line)
                clipToOutline = true
                setOnClickListener {
                    ReadBookConfig.durConfig.setCurBg(0, bg); ReadBookConfig.durConfig.setCurTextColor(Color.parseColor(ink))
                    ReadBookConfig.bgAlpha = 100; ReadBookConfig.bgBrightness = 50; ReadBookConfig.bgSaturation = 50
                    changed(true)
                }
            }
            card.addView(text("春夜\n夜色微凉\n星光落在书页", 11, Color.parseColor(ink)).apply {
                setBackgroundColor(Color.parseColor(bg)); setPadding(dp(9), dp(8), dp(9), dp(8)); setLineSpacing(dp(5).toFloat(), 1f)
            }, LayoutParams(-1, dp(73)))
            card.addView(text(if (selected) "$name ✓" else name, 12, if (selected) palette.accentText else palette.text).apply { gravity = Gravity.CENTER }, LayoutParams(-1, dp(30)))
            cards.addView(card, LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        }
        body.addView(cards)
        actionRow(body, "我的主题", "${ReadMenuThemeSuiteStore.load(context).size} 个已保存") { show(Page.THEME_LIBRARY) }
        body.addView(text("颜色预设保留当前字体、字号和间距", 11, palette.secondary), LayoutParams(-1, dp(24)))
    }

    private fun buildThemeLibrary(body: LinearLayout) {
        actionRow(body, "保存当前主题", "＋ 新建") { show(Page.SAVE) }
        val saved = ReadMenuThemeSuiteStore.load(context)
        if (saved.isEmpty()) note(body, "还没有保存的主题。调整好外观后，可保存为自己的阅读习惯。")
        saved.forEach { suite ->
            val line = row()
            line.addView(text(suite.name, 14).apply { minHeight = dp(49); setOnClickListener { suite.applyToReader(); changed(true) } }, LayoutParams(0, -2, 1f))
            line.addView(text("•••", 14, palette.secondary).apply { gravity = Gravity.CENTER; contentDescription = "管理${suite.name}"; setOnClickListener { manageTheme(suite) } }, LayoutParams(dp(44), dp(49)))
            body.addView(line)
        }
    }



    private fun buildScope(body: LinearLayout) {
        note(body, "为本书单独保存字体、间距、背景和翻页方式；设为默认时，其他书籍已有的独立外观会保留。")
        button(body, "${if (ReadBookConfig.hasBookAppearance) "✓ " else ""}仅对本书生效") { ReadBookConfig.useBookAppearance(); changed(true) }
        button(body, "${if (!ReadBookConfig.hasBookAppearance) "✓ " else ""}设为默认阅读外观") { ReadBookConfig.useDefaultAppearance(promoteCurrent = true); changed(true) }
        addDivider(body)
        actionRow(body, "本书恢复为默认外观") {
            confirm("恢复默认外观？", "只清除本书的独立外观，保留全局默认配置。") { ReadBookConfig.useDefaultAppearance(); changed(true) }
        }
        note(body, "当前配置\n${ReadBookConfig.textSize} 号字 · 行间距 ${ReadBookConfig.lineSpacingExtra} · 段间距 ${ReadBookConfig.paragraphSpacing}")
        actionRow(body, "撤销本次菜单中的外观调整") {
            undoConfig?.let {
                ReadBookConfig.restoreAppearance(it)
                changed(true)
            }
        }
        button(body, "完成", true) { show(lastAppearance) }
    }

    private fun buildSave(body: LinearLayout) {
        note(body, "${ReadBookConfig.textSize} 号字 · 行间距 ${ReadBookConfig.lineSpacingExtra} · 段间距 ${ReadBookConfig.paragraphSpacing}\n保存当前实际配置，之后可重命名、复制或导出。")
        section(body, "主题名称")
        saveName = input("例如：午后阅读", "阅读主题 ${ReadMenuThemeSuiteStore.load(context).size + 1}")
        body.addView(saveName, LayoutParams(-1, dp(48)))
        switchRow(body, "包含字体与排版", saveTypography) { saveTypography = it }
        switchRow(body, "包含背景与文字颜色", saveBackground) { saveBackground = it }
        switchRow(body, "包含翻页偏好", saveTurning) { saveTurning = it }
        button(body, "保存主题", true) {
            val name = saveName?.text?.toString()?.trim().orEmpty()
            if (name.isBlank() || name.length > 40) { saveName?.error = "请输入 1 至 40 个字符"; return@button }
            if (!saveTypography && !saveBackground && !saveTurning) { context.toastOnUi("至少选择一类配置"); return@button }
            val suite = ReadMenuThemeSuite.captureCurrent(name).copy(includeTypography = saveTypography, includeBackground = saveBackground, includeTurning = saveTurning)
            ReadMenuThemeSuiteStore.save(context, suite)
            context.toastOnUi("已保存 $name")
            show(Page.THEME)
        }
    }

    private fun manageTheme(suite: ReadMenuThemeSuite) {
        choose(suite.name, listOf("应用", "重命名", "复制", "导出分享", "删除")) { index ->
            when (index) {
                0 -> { suite.applyToReader(); changed(true) }
                1 -> {
                    val name = input("主题名称", suite.name)
                    val dialog = AlertDialog.Builder(context).setTitle("重命名主题").setView(name).setPositiveButton("保存", null).setNegativeButton("取消", null).create()
                    dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val value = name.text.toString().trim()
                        if (value.isEmpty() || value.length > 40) name.error = "请输入 1 至 40 个字符" else {
                            ReadMenuThemeSuiteStore.rename(context, suite, value); dialog.dismiss(); refresh()
                        }
                    } }
                    dialog.show()
                }
                2 -> { ReadMenuThemeSuiteStore.save(context, suite.copy(name = "${suite.name} 副本", createdAt = System.currentTimeMillis())); refresh() }
                3 -> context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_TEXT, GSON.toJson(suite)); putExtra(Intent.EXTRA_SUBJECT, suite.name) }, "导出阅读主题"))
                4 -> confirm("删除主题？", suite.name) { ReadMenuThemeSuiteStore.delete(context, suite); refresh() }
            }
        }
    }

    private fun buildSettings(body: LinearLayout) {
        actionRow(body, "屏幕方向", "跟随系统 / 竖屏 / 横屏") { choose("屏幕方向", listOf("跟随系统", "竖屏", "横屏", "自动旋转")) { context.putPrefString(PreferKey.screenOrientation, "$it"); (callbacks as? BaseReadBookActivity)?.setOrientation() } }
        actionRow(body, "保持屏幕常亮") {
            val values = resources.getStringArray(R.array.screen_time_out_value)
            choose("保持屏幕常亮", resources.getStringArray(R.array.screen_time_out).toList()) {
                context.putPrefString(PreferKey.keepLight, values[it]); postEvent(PreferKey.keepLight, true)
            }
        }
        actionRow(body, "点击区域", "左右手与自定义") { show(Page.TOUCH) }
        switchRow(body, "长按选中文字", AppConfig.textSelectAble) { context.putPrefBoolean(PreferKey.textSelectAble, it); postEvent(PreferKey.textSelectAble, it) }
        actionRow(body, "显示与内容", "状态栏 · 排版 · 净化") { show(Page.SETTINGS_DETAILS) }
        actionRow(body, "高级设置", "按键、渲染、双页") { advanced(Advanced.SETTINGS) }
    }

    private fun buildSettingsDetails(body: LinearLayout) {
        switchRow(body, "隐藏状态栏", ReadBookConfig.hideStatusBar) { context.putPrefBoolean(PreferKey.hideStatusBar, it); ReadBookConfig.hideStatusBar = it; changed(flags = arrayListOf(0, 2, 5)) }
        switchRow(body, "中文排版优化", ReadBookConfig.useZhLayout) { context.putPrefBoolean(PreferKey.useZhLayout, it); ReadBookConfig.useZhLayout = it; changed() }
        actionRow(body, "正文净化与替换") { advanced(Advanced.REPLACE) }
        actionRow(body, "恢复阅读外观", "本书 / 默认") { show(Page.SCOPE) }
    }

    private fun buildAloud(body: LinearLayout) {
        val playing = BaseReadAloudService.isRun && !BaseReadAloudService.pause
        body.addView(text("${if (playing) "正在朗读" else "准备朗读"} · ${ReadBook.book?.name.orEmpty()}", 12, palette.secondary), LayoutParams(-1, dp(28)))
        body.addView(text(ReadBook.curTextChapter?.title.orEmpty(), 17).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(6), 0, dp(12)) })
        val controls = row()
        val buttons = listOf(Triple("停止", R.drawable.ic_stop_black_24dp, { ReadAloud.stop(context) }), Triple("上段", R.drawable.ic_skip_previous, { ReadAloud.prevParagraph(context) }), Triple(if (playing) "暂停" else "播放", if (playing) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp, { callbacks.onClickReadAloud() }), Triple("下段", R.drawable.ic_skip_next, { ReadAloud.nextParagraph(context) }))
        buttons.forEachIndexed { index, (name, res, action) ->
            controls.addView(icon(res, name, if (index == 2) Color.WHITE else palette.secondary) {
                action(); postDelayed({ if (page == Page.ALOUD) refresh() }, 250)
            }.apply {
                background = rounded(if (index == 2) accent else palette.well)
            }, LayoutParams(0, dp(51), 1f).apply { marginEnd = dp(8) })
        }
        body.addView(controls)
        actionRow(body, "朗读声音", "语音设置") { advanced(Advanced.VOICE) }
        slider(body, "语速", AppConfig.ttsSpeechRate, 0, 25, { String.format(Locale.ROOT, "%.1f×", (it + 5) / 10f) }) {
            AppConfig.ttsFlowSys = false; AppConfig.ttsSpeechRate = it; ReadAloud.upTtsSpeechRate(context)
        }
        actionRow(body, "朗读设置", "定时 · 高亮 · 章节") { show(Page.ALOUD_DETAILS) }
    }

    private fun buildAloudDetails(body: LinearLayout) {
        switchRow(body, "语速跟随系统", AppConfig.ttsFlowSys) { AppConfig.ttsFlowSys = it; ReadAloud.upTtsSpeechRate(context) }
        switchRow(body, "高亮正在朗读的段落", AppConfig.readAloudHighlight) {
            AppConfig.readAloudHighlight = it
            ReadBook.curTextChapter?.let { chapter ->
                val pageIndex = ReadBook.durPageIndex
                chapter.getPage(pageIndex)?.upPageAloudSpan(ReadBook.durChapterPos - chapter.getReadLength(pageIndex))
            }
            postEvent(EventBus.UP_CONFIG, arrayListOf(6, 9, 11))
        }
        actionRow(body, "定时停止", if (BaseReadAloudService.timeMinute > 0) "${BaseReadAloudService.timeMinute} 分钟" else "未设置") {
            val minutes = listOf(0, 5, 10, 15, 30, 60, 90)
            choose("定时停止", minutes.map { if (it == 0) "关闭定时" else "$it 分钟" }) { ReadAloud.setTimer(context, minutes[it]); refresh() }
        }
        actionRow(body, "上一章") { ReadBook.moveToPrevChapter(upContent = true, toLast = false) }
        actionRow(body, "下一章") { ReadBook.moveToNextChapter(true) }
        note(body, "朗读支持后台播放；关闭菜单不会停止。点击停止结束本次朗读。")
    }

    private fun buildAuto(body: LinearLayout) {
        val modeRow = row()
        listOf("定时翻页" to ReadBookConfig.AUTO_READ_MODE_TIMED, "连续滚动" to ReadBookConfig.AUTO_READ_MODE_SCROLL).forEach { (name, mode) ->
            modeRow.addView(text(name, 14, if (ReadBookConfig.autoReadMode == mode) palette.accentText else palette.secondary).apply {
                gravity = Gravity.CENTER; background = rounded(if (ReadBookConfig.autoReadMode == mode) palette.selected else palette.well)
                setOnClickListener { ReadBookConfig.autoReadMode = mode; callbacks.updateAutoPageConfig(false); refresh() }
            }, LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
        }
        body.addView(modeRow)
        section(body, if (ReadBookConfig.autoReadMode == ReadBookConfig.AUTO_READ_MODE_TIMED) "翻页间隔" else "滚动一屏用时")
        slider(body, "时间", ReadBookConfig.autoReadSpeed, 1, 120, { "${it}s" }) { ReadBookConfig.autoReadSpeed = it; callbacks.updateAutoPageConfig(false) }
        val presets = row()
        listOf(10, 15, 30).forEach { seconds -> presets.addView(text("$seconds 秒", 13, palette.accentText).apply { gravity = Gravity.CENTER; minimumHeight = dp(44); setOnClickListener { ReadBookConfig.autoReadSpeed = seconds; callbacks.updateAutoPageConfig(false); refresh() } }, LayoutParams(0, -2, 1f)) }
        body.addView(presets)
        val stopMinutes = context.getPrefInt(AUTO_STOP_MINUTES)
        actionRow(body, "定时停止", if (stopMinutes == 0) "不限时" else "$stopMinutes 分钟") {
            val values = listOf(0, 15, 30, 60, 90)
            choose("自动阅读定时停止", values.map { if (it == 0) "不限时" else "$it 分钟" }) { context.putPrefInt(AUTO_STOP_MINUTES, values[it]); callbacks.updateAutoPageConfig(true); refresh() }
        }
        note(body, "打开阅读菜单时自动暂停，关闭后继续。点击正文可打开控制面板，随时停止自动阅读。")
        button(body, if (autoRunning) "停止自动翻页" else "开始自动翻页", true) { dismissMenu { callbacks.autoPage() } }
    }

    private fun buildTouch(body: LinearLayout) {
        note(body, if (touchTrial) "试用模式：点击区域仅提示动作，不修改阅读进度。" else "选择持握习惯，点击任意区域可修改动作。至少保留一个菜单入口；全部移除时会自动恢复中央菜单。")
        val presets = row()
        listOf("右手习惯", "左手习惯", "自定义").forEachIndexed { index, name ->
            presets.addView(text(name, 13, palette.accentText).apply {
                gravity = Gravity.CENTER; minHeight = dp(44)
                setOnClickListener {
                    touchTrial = false
                    if (index < 2) {
                        touchKeys.forEachIndexed { i, key -> context.putPrefInt(key, if (i == 4) 0 else if ((index == 0 && i % 3 == 0) || (index == 1 && i % 3 == 2)) 2 else 1) }
                        AppConfig.detectClickArea()
                        postEvent(EventBus.UP_CONFIG, arrayListOf(12))
                    }
                    refresh()
                }
            }, LayoutParams(0, -2, 1f))
        }
        body.addView(presets)
        val actions = listOf("打开菜单", "下一页", "上一页", "下一章", "上一章", "朗读上一段", "朗读下一段", "添加书签", "编辑内容", "切换净化", "目录", "全文搜索", "同步进度", "朗读暂停 / 继续")
        touchKeys.chunked(3).forEachIndexed { rowIndex, keys ->
            val line = row()
            keys.forEachIndexed { colIndex, key ->
                val index = rowIndex * 3 + colIndex
                val value = context.getPrefInt(key, listOf(2, 2, 1, 2, 0, 1, 2, 1, 1)[index])
                val label = actions.getOrElse(value) { "无操作" }
                line.addView(text(label, 12, if (value == 0) palette.accentText else palette.secondary).apply {
                    gravity = Gravity.CENTER; background = rounded(if (value == 0) palette.selected else palette.well, 8, palette.line)
                    setOnClickListener {
                        if (touchTrial) context.toastOnUi(label) else choose("选择点击动作", actions + "无操作") {
                            context.putPrefInt(key, if (it == actions.size) -1 else it); AppConfig.detectClickArea(); postEvent(EventBus.UP_CONFIG, arrayListOf(12)); refresh()
                        }
                    }
                }, LayoutParams(0, dp(74), 1f).apply { marginEnd = dp(5) })
            }
            body.addView(line, LayoutParams(-1, -2).apply { topMargin = dp(5) })
        }
        switchRow(body, "长按选中文字", AppConfig.textSelectAble) { context.putPrefBoolean(PreferKey.textSelectAble, it); postEvent(PreferKey.textSelectAble, it) }
        button(body, if (touchTrial) "结束试用" else "试一试点击区域", true) { touchTrial = !touchTrial; refresh() }
    }

    private fun input(hint: String, value: String = "") = EditText(context).apply {
        setText(value); this.hint = hint; isSingleLine = true; textSize = 14f
        setTextColor(palette.text); setHintTextColor(palette.secondary)
        background = rounded(palette.well, 10, palette.line)
        setPadding(dp(13), 0, dp(13), 0)
    }
    private fun hideKeyboard() { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(windowToken, 0); clearFocus() }
    private fun choose(title: String, items: List<String>, action: (Int) -> Unit) {
        AlertDialog.Builder(context).setTitle(title).setItems(items.toTypedArray()) { _, index -> action(index) }.setNegativeButton("取消", null).show()
    }
    private fun confirm(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(context).setTitle(title).setMessage(message).setPositiveButton("确定") { _, _ -> action() }.setNegativeButton("取消", null).show()
    }
    private fun numberDialog(title: String, value: Int, min: Int, max: Int, action: (Int) -> Unit) {
        val field = input("$min - $max", "$value").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val dialog = AlertDialog.Builder(context).setTitle(title).setView(field).setPositiveButton("确定", null).setNegativeButton("取消", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val number = field.text.toString().toIntOrNull()
            if (number == null || number !in min..max) field.error = "请输入 $min 至 $max" else { action(number); dialog.dismiss() }
        } }
        dialog.show()
    }

    private fun createList(parent: LinearLayout): RowsAdapter {
        val adapter = RowsAdapter()
        val recycler = RecyclerView(context).apply { layoutManager = LinearLayoutManager(context); this.adapter = adapter; itemAnimator = null }
        parent.addView(recycler, LayoutParams(-1, 0, 1f))
        list = recycler; listAdapter = adapter
        return adapter
    }
    private data class Entry(val title: String, val detail: CharSequence = "", val selected: Boolean = false, val onClick: () -> Unit)
    private inner class RowsAdapter : RecyclerView.Adapter<RowsAdapter.Holder>() {
        var entries = emptyList<Entry>()
            set(value) { field = value; notifyDataSetChanged() }
        inner class Holder(val root: LinearLayout, val title: TextView, val detail: TextView) : RecyclerView.ViewHolder(root)
        override fun getItemCount() = entries.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val compact = page == Page.TOC
            val root = column().apply { setPadding(dp(10), dp(if (compact) 8 else 13), dp(10), dp(if (compact) 8 else 13)); minimumHeight = dp(51); layoutParams = RecyclerView.LayoutParams(-1, -2) }
            val title = text("", 14)
            val detail = text("", if (compact) 11 else 13, palette.secondary).apply { setLineSpacing(dp(if (compact) 0 else 4).toFloat(), 1.1f); setPadding(0, dp(if (compact) 4 else 8), 0, 0) }
            root.addView(title); root.addView(detail)
            return Holder(root, title, detail)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = entries[position]
            holder.title.text = entry.title
            holder.title.setTextColor(if (entry.selected) palette.accentText else palette.text)
            holder.detail.text = entry.detail; holder.detail.isVisible = entry.detail.isNotEmpty()
            holder.root.background = if (entry.selected) rounded(palette.selected, 8) else null
            holder.root.setOnClickListener { entry.onClick() }
        }
    }

    private fun buildSearch(body: LinearLayout) {
        val field = input("搜索已缓存的章节", query).apply { imeOptions = EditorInfo.IME_ACTION_SEARCH }
        val searchLine = row()
        searchLine.addView(field, LayoutParams(0, dp(46), 1f))
        searchLine.addView(icon(R.drawable.ic_lucide_search, "开始搜索", palette.accentText) { query = field.text.toString(); startSearch() }, LayoutParams(dp(44), dp(46)))
        body.addView(searchLine)
        field.setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_SEARCH) { query = field.text.toString(); startSearch(); true } else false }
        field.doAfterTextChanged {
            query = it?.toString().orEmpty()
            dataJob?.cancel()
            searchResults = emptyList()
            searchMessage = "点击搜索或键盘搜索键查看结果"
            searchCount?.text = searchMessage
            renderSearchResults()
        }
        val filters = row()
        filters.addView(text(if (regexSearch) "✓ 正则表达式" else "正则表达式", 12, palette.secondary).apply { minHeight = dp(43); setOnClickListener { regexSearch = !regexSearch; startSearch() } }, LayoutParams(0, -2, 1f))
        filters.addView(text(if (currentChapterOnly) "仅当前章 ▾" else "已缓存章节 ▾", 12, palette.secondary).apply { minHeight = dp(43); setOnClickListener { currentChapterOnly = !currentChapterOnly; startSearch() } })
        body.addView(filters)
        searchCount = text(searchMessage, 12, palette.secondary)
        body.addView(searchCount, LayoutParams(-1, dp(31)))
        createList(body)
        renderSearchResults()
        if (searchOrigin?.first == ReadBook.book?.bookUrl) button(body, "返回搜索前的阅读位置") {
            searchOrigin?.let { origin -> dismissMenu { callbacks.returnFromInlineSearch(origin.second, origin.third) } }
        }
    }

    private fun startSearch() {
        hideKeyboard()
        val book = ReadBook.book ?: return
        dataJob?.cancel()
        val term = query.trim()
        if (term.isEmpty()) { searchResults = emptyList(); searchMessage = "输入关键词，搜索已缓存的章节"; refresh(); return }
        val expression = runCatching { if (regexSearch) Regex(term) else null }.getOrElse { searchResults = emptyList(); searchMessage = "正则表达式无效：${it.message}"; refresh(); return }
        if (searchOrigin == null) searchOrigin = Triple(book.bookUrl, ReadBook.durChapterIndex, ReadBook.durChapterPos)
        val chapterIndex = ReadBook.durChapterIndex
        val onlyCurrent = currentChapterOnly
        val regex = regexSearch
        dataJob?.cancel()
        searchResults = emptyList(); searchMessage = "正在搜索已缓存的章节…"; refresh()
        dataJob = scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val result = arrayListOf<SearchResult>()
                    val processor = ContentProcessor.get(book.name, book.origin)
                    appDb.bookChapterDao.getChapterList(book.bookUrl).forEach { chapter ->
                        ensureActive()
                        if (chapter.isVolume || (onlyCurrent && chapter.index != chapterIndex) || !BookHelp.hasContent(book, chapter)) return@forEach
                        val raw = BookHelp.getContent(book, chapter) ?: return@forEach
                        val value = processor.getContent(book, chapter, raw, useReplace = book.getUseReplaceRule()).toString()
                        ReaderSearchMatches.find(value, term, expression).forEachIndexed { localIndex, match ->
                            ensureActive()
                            if (result.size < 5000) {
                                val start = (match.first - 35).coerceAtLeast(0)
                                val end = (match.last + 71).coerceAtMost(value.length)
                                result.add(SearchResult(resultCount = result.size, resultCountWithinChapter = localIndex, resultText = value.substring(start, end).replace('\n', ' '), chapterTitle = chapter.title, query = term, chapterIndex = chapter.index, queryIndexInResult = match.first - start, queryIndexInChapter = match.first, isRegex = regex))
                            }
                        }
                    }
                    result.toList()
                }
            }.onSuccess {
                if (ReadBook.book?.bookUrl != book.bookUrl) return@onSuccess
                searchResults = it
                searchMessage = if (it.isEmpty()) "已缓存章节中未找到匹配内容" else "${if (it.size >= 5000) "前 " else ""}${it.size} 处匹配 · ${it.map { result -> result.chapterIndex }.distinct().size} 章"
                if (page == Page.SEARCH) refresh()
            }.onFailure { if (it !is kotlinx.coroutines.CancellationException) { searchMessage = "搜索失败，可重试：${it.localizedMessage.orEmpty()}"; searchCount?.text = searchMessage } }
        }
    }

    private fun renderSearchResults() {
        listAdapter?.entries = searchResults.mapIndexed { index, result ->
            val value = SpannableString(result.resultText)
            val start = result.queryIndexInResult.coerceIn(0, value.length)
            val length = if (result.isRegex) runCatching { Regex(result.query).find(value, start)?.value?.length ?: 0 }.getOrDefault(0) else result.query.length
            val end = (start + length).coerceAtMost(value.length)
            if (end > start) {
                value.setSpan(ForegroundColorSpan(palette.accentText), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                value.setSpan(BackgroundColorSpan(palette.selected), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            Entry(result.chapterTitle, value, result.chapterIndex == ReadBook.durChapterIndex) { callbacks.openInlineSearchResult(result, searchResults, index) }
        }
    }

    private fun buildToc(body: LinearLayout) {
        val filter = input("查找章节名称或序号", chapterQuery)
        body.addView(filter, LayoutParams(-1, dp(44)))
        filter.doAfterTextChanged { chapterQuery = it?.toString().orEmpty(); renderChapters() }
        val meta = row()
        meta.addView(text("${chapters.size} 章 · 已缓存 ${cachedChapters.size} 章", 11, palette.secondary), LayoutParams(0, dp(37), 1f))
        meta.addView(text(if (reverseChapters) "倒序 ▾" else "正序 ▾", 12, palette.secondary).apply { minHeight = dp(44); setOnClickListener { reverseChapters = !reverseChapters; renderChapters(); text = if (reverseChapters) "倒序 ▾" else "正序 ▾" } })
        body.addView(meta)
        createList(body)
        renderChapters()
    }

    private fun locateCurrentChapter() {
        showBookmarks = false; chapterQuery = ""; refresh()
        val values = if (reverseChapters) chapters.reversed() else chapters
        val target = values.indexOfFirst { it.index == ReadBook.durChapterIndex }
        if (target >= 0) (list?.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(target, 0)
    }

    private fun loadChapters() {
        val book = ReadBook.book ?: return
        dataJob = scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val values = appDb.bookChapterDao.getChapterList(book.bookUrl)
                Triple(values, values.filter { !it.isVolume && BookHelp.hasContent(book, it) }.map { it.index }.toSet(), appDb.bookmarkDao.getByBook(book.name, book.author))
            } }.onSuccess {
                if (ReadBook.book?.bookUrl != book.bookUrl) return@onSuccess
                chapters = it.first; cachedChapters = it.second; bookmarks = it.third
                if (page == Page.TOC) refresh()
            }.onFailure { if (it !is kotlinx.coroutines.CancellationException) context.toastOnUi("目录加载失败，请重试") }
        }
    }

    private fun renderChapters() {
        listAdapter?.entries = if (showBookmarks) {
            bookmarks.filter { chapterQuery.isBlank() || it.chapterName.contains(chapterQuery, true) }.map { bookmark ->
                Entry(bookmark.chapterName, bookmark.bookText) { dismissMenu { ReadBook.openChapter(bookmark.chapterIndex, bookmark.chapterPos) } }
            }
        } else {
            val values = chapters.filter { chapterQuery.isBlank() || it.title.contains(chapterQuery, true) || (it.index + 1).toString() == chapterQuery }
            (if (reverseChapters) values.reversed() else values).map { chapter ->
                val current = chapter.index == ReadBook.durChapterIndex
                Entry("${chapter.index + 1}  ${chapter.title}", if (current) "正在阅读" else if (chapter.index in cachedChapters) "已缓存" else "未缓存", current) {
                    if (!chapter.isVolume) dismissMenu { callbacks.skipToChapter(chapter.index) }
                }
            }
        }
    }

    companion object {
        const val AUTO_STOP_MINUTES = "readerAutoStopMinutes"
        private val appearancePages = listOf(Page.LAYOUT, Page.TURN, Page.BACKGROUND, Page.THEME)
        private val detailPages = listOf(Page.SCOPE, Page.SAVE, Page.AUTO, Page.TOUCH, Page.FONTS, Page.LAYOUT_DETAILS, Page.TURN_DETAILS, Page.BACKGROUND_DETAILS, Page.THEME_LIBRARY, Page.ALOUD_DETAILS, Page.SETTINGS_DETAILS, Page.PROGRESS)
        private val touchKeys = listOf(PreferKey.clickActionTL, PreferKey.clickActionTC, PreferKey.clickActionTR, PreferKey.clickActionML, PreferKey.clickActionMC, PreferKey.clickActionMR, PreferKey.clickActionBL, PreferKey.clickActionBC, PreferKey.clickActionBR)
    }
}

internal object ReaderSearchMatches {
    fun find(content: String, query: String, regex: Regex? = null): Sequence<IntRange> = sequence {
        if (query.isEmpty()) return@sequence
        if (regex != null) {
            regex.findAll(content).filter { it.value.isNotEmpty() }.take(5000).forEach { yield(it.range) }
        } else {
            var start = 0
            var count = 0
            while (start < content.length && count < 5000) {
                val index = content.indexOf(query, start)
                if (index < 0) break
                yield(index until index + query.length)
                start = index + query.length
                count++
            }
        }
    }
}
