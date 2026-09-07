package io.wanjuan.app.ui.book.read

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.wanjuan.app.R
import io.wanjuan.app.data.entities.BookChapter
import io.wanjuan.app.ui.book.read.config.ReaderSheetStyle
import io.wanjuan.app.ui.book.read.config.ReaderUiStyle as Ui
import io.wanjuan.app.utils.hideSoftInput

class ReaderChapterListPanel(
    context: Context,
    private val currentChapter: Int,
    close: () -> Unit,
    private val select: (BookChapter) -> Unit
) : LinearLayout(context) {
    private val colors = ReaderSheetStyle.resolve(context)
    private var chapters = emptyList<BookChapter>()
    private var displayed = emptyList<BookChapter>()
    private val filter = EditText(context)
    private val status = label("正在加载章节", Ui.TEXT_CAPTION)
    private val adapter = ChapterAdapter()
    private val list = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        adapter = this@ReaderChapterListPanel.adapter
        itemAnimator = null
    }

    init {
        orientation = VERTICAL
        background = Ui.rounded(context, colors.surface, Ui.RADIUS_SHEET, colors.stroke)
        clipToOutline = true
        isClickable = true
        setPadding(dp(Ui.INSET), dp(Ui.GAP), dp(Ui.INSET), dp(Ui.GAP))
        addView(android.view.View(context).apply { background = Ui.rounded(context, colors.stroke) },
            LayoutParams(dp(24), dp(3)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label("目录", Ui.TEXT_TITLE).apply { setTypeface(typeface, Typeface.BOLD) },
                LayoutParams(0, dp(Ui.CONTROL_NORMAL), 1f))
            addView(label("定位当前", Ui.TEXT_CAPTION).apply {
                gravity = Gravity.CENTER
                setTextColor(colors.accentTextColor)
                setOnClickListener { locateCurrent() }
            }, LayoutParams(dp(74), dp(Ui.CONTROL_NORMAL)))
            addView(Ui.icon(context, R.drawable.ic_close_x, "关闭面板", colors.secondaryTextColor).apply {
                setOnClickListener { close() }
            }, LayoutParams(dp(Ui.CONTROL_NORMAL), dp(Ui.CONTROL_NORMAL)))
        })
        filter.apply {
            hint = "查找章节名称或序号"
            textSize = Ui.TEXT_BODY.toFloat()
            isSingleLine = true
            setTextColor(colors.textColor)
            setHintTextColor(colors.secondaryTextColor)
            background = Ui.rounded(context, colors.panel, Ui.RADIUS_CONTROL, colors.stroke)
            setPadding(dp(13), 0, dp(13), 0)
            doAfterTextChanged { render() }
        }
        addView(filter, LayoutParams(-1, dp(Ui.CONTROL_NORMAL)))
        addView(status, LayoutParams(-1, dp(Ui.CONTROL_COMPACT)))
        addView(list, LayoutParams(-1, 0, 1f))
    }

    fun submitChapters(values: List<BookChapter>) {
        chapters = values
        render()
        locateCurrent()
    }

    fun showLoadError() {
        status.text = "目录加载失败，请关闭后重试"
    }

    private fun render() {
        val query = filter.text.toString().trim()
        displayed = chapters.filter { query.isEmpty() || it.title.contains(query, true) || (it.index + 1).toString() == query }
        status.text = if (displayed.isEmpty()) "没有匹配的章节" else "${displayed.size} 章"
        adapter.notifyDataSetChanged()
    }

    private fun locateCurrent() {
        filter.setText("")
        val index = displayed.indexOfFirst { it.index == currentChapter }
        if (index >= 0) (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(index, 0)
    }

    fun closeKeyboard() {
        filter.hideSoftInput()
        filter.clearFocus()
    }

    private fun dp(value: Int) = Ui.dp(context, value)
    private fun label(value: String, size: Int = Ui.TEXT_BODY) = TextView(context).apply {
        text = value
        textSize = size.toFloat()
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(if (size == Ui.TEXT_CAPTION) colors.secondaryTextColor else colors.textColor)
    }

    private inner class ChapterAdapter : RecyclerView.Adapter<ChapterHolder>() {
        override fun getItemCount() = displayed.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterHolder {
            val root = LinearLayout(context).apply {
                orientation = VERTICAL
                minimumHeight = dp(Ui.CONTROL_NORMAL)
                setPadding(dp(10), dp(Ui.GAP), dp(10), dp(Ui.GAP))
                layoutParams = RecyclerView.LayoutParams(-1, -2)
                isFocusable = true
            }
            val title = label("")
            val detail = label("正在阅读", Ui.TEXT_CAPTION)
            root.addView(title)
            root.addView(detail, LayoutParams(-1, -2).apply { topMargin = dp(4) })
            return ChapterHolder(root, title, detail)
        }

        override fun onBindViewHolder(holder: ChapterHolder, position: Int) {
            val chapter = displayed[position]
            val selected = chapter.index == currentChapter
            holder.title.text = "${chapter.index + 1}  ${chapter.title}"
            holder.title.setTextColor(if (selected) colors.accentTextColor else colors.textColor)
            holder.detail.isVisible = selected
            holder.root.background = if (selected) Ui.rounded(context, colors.panelStrong) else null
            holder.root.setOnClickListener { if (!chapter.isVolume) select(chapter) }
        }
    }

    private class ChapterHolder(val root: LinearLayout, val title: TextView, val detail: TextView) : RecyclerView.ViewHolder(root)
}
