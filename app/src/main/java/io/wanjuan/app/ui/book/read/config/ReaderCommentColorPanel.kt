package io.wanjuan.app.ui.book.read.config

import android.content.Context
import android.graphics.Color
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import io.wanjuan.app.R
import io.wanjuan.app.constant.EventBus
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.config.ThemeConfig
import io.wanjuan.app.utils.CommentIndicatorSvg
import io.wanjuan.app.utils.SvgUtils
import io.wanjuan.app.utils.postEvent
import io.wanjuan.app.utils.putPrefString
import java.util.Locale
import io.wanjuan.app.ui.book.read.config.ReaderUiStyle as Ui

/** Color editing stays inside the reader dock; day and night use the shared theme settings. */
class ReaderCommentColorPanel(
    context: Context,
    private var isNight: Boolean,
    private val onModeChanged: (Boolean) -> Unit
) : LinearLayout(context) {
    private val colors = ReaderSheetStyle.resolve(context)
    private val previewSvg = resources.openRawResource(R.raw.comment_indicator_preview)
        .bufferedReader().use { it.readText() }

    init {
        orientation = VERTICAL
        render()
    }

    private fun dp(value: Int) = Ui.dp(context, value)
    private fun label(value: String, size: Int = Ui.TEXT_BODY, color: Int = colors.textColor) = TextView(context).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun row() = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private fun block(view: android.view.View, height: Int, gap: Int = Ui.GAP) {
        addView(view, LayoutParams(-1, if (height < 0) height else dp(height)).apply { topMargin = dp(gap) })
    }

    private fun render() {
        removeAllViews()
        val value = ThemeConfig.getCommentIndicatorColor(context, isNight)
        val parsed = value?.let { runCatching { Color.parseColor(it) }.getOrNull() }
        val modes = row()
        listOf(R.string.day, R.string.night).forEachIndexed { index, title ->
            val selected = isNight == (index == 1)
            modes.addView(label(context.getString(title), color = if (selected) Color.WHITE else colors.secondaryTextColor).apply {
                gravity = Gravity.CENTER
                isSelected = selected
                isFocusable = true
                background = Ui.rounded(context, if (selected) colors.accentColor else colors.panel)
                setOnClickListener {
                    dismissKeyboard()
                    isNight = index == 1
                    onModeChanged(isNight)
                    render()
                }
            }, LayoutParams(0, dp(Ui.CONTROL_NORMAL), 1f).apply { if (index == 0) marginEnd = dp(Ui.GAP) })
        }
        block(modes, Ui.CONTROL_NORMAL, 0)

        val preview = row().apply {
            setPadding(dp(12), 0, dp(12), 0)
            background = Ui.rounded(context, if (isNight) 0xff1e2528.toInt() else 0xfff1ebdd.toInt())
        }
        val svg = parsed?.let { CommentIndicatorSvg.recolor(previewSvg, hex(it)) } ?: previewSvg
        preview.addView(label(context.getString(R.string.theme_comment_indicator_preview), Ui.TEXT_CAPTION,
            if (isNight) 0xffbec5c4.toInt() else 0xff464b44.toInt()), LayoutParams(0, -2, 1f))
        preview.addView(ImageView(context).apply {
            contentDescription = context.getString(R.string.preview)
            svg.byteInputStream().use { setImageBitmap(SvgUtils.createBitmap(it, 144, 108)) }
        }, LayoutParams(dp(48), dp(36)))
        block(preview, 64)

        val swatches = row()
        val presets = if (isNight) listOf("#80B6FF", "#A0ABB0", "#E0E5E5", "#81C7AC", "#D8B887", "#E6A0A0")
            else listOf("#006EFF", "#77817E", "#303934", "#438B72", "#A87B35", "#C96060")
        presets.forEachIndexed { index, color ->
            val tint = Color.parseColor(color)
            val selected = parsed == tint
            val swatch = FrameLayout(context).apply {
                contentDescription = color
                isSelected = selected
                isFocusable = true
                background = Ui.rounded(context, colors.surface, stroke = if (selected) colors.accentColor else colors.stroke)
                addView(label(if (selected) "\u2713" else "", Ui.TEXT_TITLE,
                    if (ColorUtils.calculateLuminance(tint) > .45) Color.BLACK else Color.WHITE).apply {
                    gravity = Gravity.CENTER
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                    background = Ui.rounded(context, tint, 8)
                }, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) })
                setOnClickListener { save(color) }
            }
            swatches.addView(swatch, LayoutParams(0, dp(Ui.CONTROL_NORMAL), 1f).apply {
                if (index < presets.lastIndex) marginEnd = dp(6)
            })
        }
        block(swatches, Ui.CONTROL_NORMAL)

        val custom = row()
        val input = EditText(context).apply {
            textSize = Ui.TEXT_BODY.toFloat()
            setTextColor(colors.textColor)
            setHintTextColor(colors.secondaryTextColor)
            hint = "#RRGGBB"
            contentDescription = context.getString(R.string.theme_comment_indicator_hex)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(7))
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            isFocusableInTouchMode = true
            showSoftInputOnFocus = true
            background = Ui.rounded(context, colors.panel, stroke = colors.stroke)
            setPadding(dp(12), 0, dp(12), 0)
            setText(parsed?.let(::hex).orEmpty())
            setOnClickListener {
                requestFocus()
                post {
                    if (isAttachedToWindow && hasFocus()) {
                        val keyboard = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        keyboard.showSoftInput(this, 0)
                    }
                }
            }
        }
        fun applyInput() {
            val hex = input.text.toString().trim().removePrefix("#")
            if (!hex.matches(Regex("[0-9a-fA-F]{6}"))) {
                input.error = context.getString(R.string.color_format_error)
                return
            }
            save("#${hex.uppercase(Locale.ROOT)}")
        }
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { applyInput(); true } else false
        }
        custom.addView(input, LayoutParams(0, dp(Ui.CONTROL_NORMAL), 1f))
        custom.addView(label(context.getString(R.string.theme_comment_indicator_apply), color = Color.WHITE).apply {
            gravity = Gravity.CENTER
            isFocusable = true
            background = Ui.rounded(context, colors.accentColor)
            setOnClickListener { applyInput() }
        }, LayoutParams(dp(72), dp(Ui.CONTROL_NORMAL)).apply { marginStart = dp(Ui.GAP) })
        block(custom, Ui.CONTROL_NORMAL)

        block(label(context.getString(R.string.theme_color_follow_source), color = colors.accentTextColor).apply {
            gravity = Gravity.CENTER
            isSelected = value == null
            isFocusable = true
            background = Ui.rounded(context, colors.panelStrong, stroke = if (value == null) colors.accentColor else Color.TRANSPARENT)
            setOnClickListener { save(null) }
        }, Ui.CONTROL_NORMAL)
        block(label(context.getString(R.string.theme_comment_indicator_description), Ui.TEXT_CAPTION, colors.secondaryTextColor).apply {
            setPadding(0, 0, 0, dp(Ui.GAP))
        }, -2)
    }

    private fun save(color: String?) {
        dismissKeyboard()
        context.putPrefString(if (isNight) PreferKey.commentIndicatorColorNight else PreferKey.commentIndicatorColor, color)
        render()
        if (isNight == AppConfig.isNightTheme) postEvent(EventBus.UP_CONFIG, arrayListOf(9, 6))
    }

    private fun dismissKeyboard() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(windowToken, 0)
        findFocus()?.clearFocus()
    }

    private fun hex(color: Int) = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)
}
