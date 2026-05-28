package io.wanjuan.app.ui.widget

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import io.wanjuan.app.R
import io.wanjuan.app.lib.theme.UiCorner
import io.wanjuan.app.utils.dpToPx

object GridColumnsPopup {

    fun show(
        anchor: View,
        @StringRes titleRes: Int,
        minColumns: Int,
        maxColumns: Int,
        initialColumns: Int,
        previousPopup: PopupWindow? = null,
        onColumnsChanging: ((Int) -> Unit)? = null,
        onColumnsChanged: (Int) -> Unit,
        initialSpacing: Int? = null,
        spacingTitleRes: Int? = null,
        minSpacing: Int = 0,
        maxSpacing: Int = 60,
        onSpacingChanging: ((Int) -> Unit)? = null,
        onSpacingChanged: ((Int) -> Unit)? = null
    ): PopupWindow? {
        if (maxColumns < minColumns) return previousPopup
        previousPopup?.dismiss()
        val content = createContent(
            anchor = anchor,
            titleRes = titleRes,
            minColumns = minColumns,
            maxColumns = maxColumns,
            initialColumns = initialColumns,
            onColumnsChanging = onColumnsChanging,
            onColumnsChanged = onColumnsChanged,
            initialSpacing = initialSpacing,
            spacingTitleRes = spacingTitleRes,
            minSpacing = minSpacing,
            maxSpacing = maxSpacing,
            onSpacingChanging = onSpacingChanging,
            onSpacingChanged = onSpacingChanged
        )
        val width = 284.dpToPx()
        content.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.UNSPECIFIED
        )
        return PopupWindow(
            content,
            width,
            content.measuredHeight,
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = 0f
            }
            showAnchored(anchor)
        }
    }

    private fun createContent(
        anchor: View,
        @StringRes titleRes: Int,
        minColumns: Int,
        maxColumns: Int,
        initialColumns: Int,
        onColumnsChanging: ((Int) -> Unit)?,
        onColumnsChanged: (Int) -> Unit,
        initialSpacing: Int?,
        spacingTitleRes: Int?,
        minSpacing: Int,
        maxSpacing: Int,
        onSpacingChanging: ((Int) -> Unit)?,
        onSpacingChanged: ((Int) -> Unit)?
    ): View {
        val context = anchor.context
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dpToPx(), 10.dpToPx(), 12.dpToPx(), 10.dpToPx())
            background = UiCorner.opaqueRounded(
                ContextCompat.getColor(context, R.color.background_card),
                UiCorner.panelRadius(context)
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                clipToOutline = true
            }
            addSlider(
                titleRes = titleRes,
                minColumns = minColumns,
                maxColumns = maxColumns,
                initialColumns = initialColumns,
                onValueChanging = onColumnsChanging,
                onValueChanged = onColumnsChanged
            )
            if (initialSpacing != null && spacingTitleRes != null && maxSpacing >= minSpacing) {
                addSlider(
                    titleRes = spacingTitleRes,
                    minColumns = minSpacing,
                    maxColumns = maxSpacing,
                    initialColumns = initialSpacing,
                    onValueChanging = onSpacingChanging,
                    onValueChanged = onSpacingChanged
                )
            }
        }
    }

    private fun LinearLayout.addSlider(
        @StringRes titleRes: Int,
        minColumns: Int,
        maxColumns: Int,
        initialColumns: Int,
        onValueChanging: ((Int) -> Unit)?,
        onValueChanged: ((Int) -> Unit)?
    ) {
        val seekBar = DetailSeekBar(context).apply {
            max = maxColumns - minColumns
            valueFormat = { (it + minColumns).toString() }
            progress = (initialColumns - minColumns).coerceIn(0, max)
            findViewById<TextView>(R.id.tv_seek_title)?.setText(titleRes)
            onChanging = { progress ->
                onValueChanging?.invoke(progress + minColumns)
            }
            onChanged = { progress ->
                onValueChanged?.invoke(progress + minColumns)
            }
        }
        addView(
            seekBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun PopupWindow.showAnchored(anchor: View) {
        val gap = 4.dpToPx()
        val location = IntArray(2)
        val visibleFrame = Rect()
        anchor.getLocationOnScreen(location)
        anchor.rootView.getWindowVisibleDisplayFrame(visibleFrame)

        val popupWidth = width
        val popupHeight = height
        val desiredX = location[0] + anchor.width - popupWidth
        val x = desiredX.coerceIn(
            visibleFrame.left + gap,
            (visibleFrame.right - popupWidth - gap).coerceAtLeast(visibleFrame.left + gap)
        )
        val belowY = location[1] + anchor.height + gap
        val aboveY = location[1] - popupHeight - gap
        val hasRoomBelow = belowY + popupHeight <= visibleFrame.bottom - gap
        val y = if (hasRoomBelow || aboveY < visibleFrame.top + gap) {
            belowY
        } else {
            aboveY
        }.coerceIn(
            visibleFrame.top + gap,
            (visibleFrame.bottom - popupHeight - gap).coerceAtLeast(visibleFrame.top + gap)
        )
        showAtLocation(anchor.rootView, Gravity.NO_GRAVITY, x, y)
    }
}
