package io.wanjuan.app.ui.book.read.config

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import io.wanjuan.app.R
import io.wanjuan.app.help.config.AppConfig
import kotlin.math.roundToInt

/** Shared dimensions for reader controls; book typography is configured separately. */
object ReaderUiStyle {
    const val TEXT_CAPTION = 12
    const val TEXT_BODY = 14
    const val TEXT_TITLE = 16
    const val ICON_SMALL = 16
    const val ICON_NORMAL = 20
    const val ICON_LARGE = 24
    const val CONTROL_COMPACT = 36
    const val CONTROL_NORMAL = 44
    const val CONTROL_LARGE = 52
    const val RADIUS_CONTROL = 12
    const val RADIUS_SHEET = 24
    const val INSET = 16
    const val GAP = 8

    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).roundToInt()

    fun rounded(context: Context, fill: Int, radius: Int = RADIUS_CONTROL, stroke: Int = Color.TRANSPARENT) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(context, radius).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(context, 1), stroke)
        }

    fun icon(context: Context, resource: Int, label: String, color: Int, size: Int = ICON_NORMAL) =
        object : ImageView(context) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                val glyph = dp(context, size).coerceAtMost(minOf(w, h))
                setPadding((w - glyph) / 2, (h - glyph) / 2, (w - glyph + 1) / 2, (h - glyph + 1) / 2)
            }
        }.apply {
            setImageResource(resource)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setColorFilter(color)
            contentDescription = label
            val inset = (CONTROL_NORMAL - size) / 2
            setPadding(dp(context, inset), dp(context, inset), dp(context, inset), dp(context, inset))
            minimumWidth = dp(context, CONTROL_NORMAL)
            minimumHeight = dp(context, CONTROL_NORMAL)
        }

    fun tintSwitch(view: SwitchCompat) {
        val colors = ReaderSheetStyle.resolve(view.context)
        view.thumbTintList = ColorStateList.valueOf(if (AppConfig.isEInkMode) Color.BLACK else Color.WHITE)
        view.trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(colors.accentColor, colors.stroke)
        )
        view.minHeight = dp(view.context, CONTROL_NORMAL)
    }

    fun preferenceSheet(context: Context, title: String, close: () -> Unit): View {
        val colors = ReaderSheetStyle.resolve(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(context, colors.surface, RADIUS_SHEET, colors.stroke)
            clipToOutline = true
            addView(View(context).apply { background = rounded(context, colors.stroke) },
                LinearLayout.LayoutParams(dp(context, 24), dp(context, 3)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dp(context, GAP)
                })
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, INSET), 0, dp(context, GAP), 0)
                addView(TextView(context).apply {
                    text = title
                    textSize = TEXT_TITLE.toFloat()
                    gravity = Gravity.CENTER_VERTICAL
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(colors.textColor)
                }, LinearLayout.LayoutParams(0, dp(context, CONTROL_LARGE), 1f))
                addView(icon(context, R.drawable.ic_close_x, "关闭", colors.secondaryTextColor).apply {
                    isFocusable = true
                    setOnClickListener { close() }
                }, LinearLayout.LayoutParams(dp(context, CONTROL_NORMAL), dp(context, CONTROL_NORMAL)))
            })
            addView(FrameLayout(context).apply { id = R.id.tag1 }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    fun configureSheet(window: Window) {
        val context = window.context
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.decorView.setPadding(dp(context, GAP), 0, dp(context, GAP), dp(context, GAP))
        window.setGravity(Gravity.BOTTOM)
        val height = (context.resources.displayMetrics.heightPixels * .72f).roundToInt()
            .coerceAtMost(dp(context, 520))
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height)
    }

    fun styleDialog(dialog: AlertDialog) {
        val colors = ReaderSheetStyle.resolve(dialog.context)
        dialog.window?.setBackgroundDrawable(rounded(dialog.context, colors.surface, RADIUS_SHEET, colors.stroke))
        dialog.window?.decorView?.let { styleLabels(it) }
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { id ->
            dialog.getButton(id)?.apply {
                textSize = TEXT_BODY.toFloat()
                minHeight = dp(context, CONTROL_NORMAL)
                setTextColor(colors.accentColor)
            }
        }
        dialog.listView?.let { list ->
            list.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) { child?.let { styleLabels(it) } }
                override fun onChildViewRemoved(parent: View?, child: View?) = Unit
            })
        }
    }

    fun styleLabels(view: View) {
        val colors = ReaderSheetStyle.resolve(view.context)
        when (view) {
            is TextView -> {
                val sp = view.textSize / view.resources.displayMetrics.scaledDensity
                view.textSize = when {
                    sp <= TEXT_CAPTION -> TEXT_CAPTION
                    sp <= TEXT_BODY -> TEXT_BODY
                    else -> TEXT_TITLE
                }.toFloat()
                view.setTextColor(colors.textColor)
                if (view.isClickable) view.minimumHeight = dp(view.context, CONTROL_NORMAL)
            }
        }
        if (view is SwitchCompat) tintSwitch(view)
        else if (view is CompoundButton) view.buttonTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(colors.accentColor, colors.secondaryTextColor)
        )
        if (view is SeekBar) {
            view.progressTintList = ColorStateList.valueOf(colors.accentColor)
            view.thumbTintList = ColorStateList.valueOf(colors.accentColor)
            view.progressBackgroundTintList = ColorStateList.valueOf(colors.stroke)
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) styleLabels(view.getChildAt(i))
    }
}
