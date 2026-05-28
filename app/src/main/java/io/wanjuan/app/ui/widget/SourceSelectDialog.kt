package io.wanjuan.app.ui.widget

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.wanjuan.app.R
import io.wanjuan.app.lib.theme.UiCorner
import io.wanjuan.app.lib.theme.accentColor
import io.wanjuan.app.lib.theme.applyUiBodyTypeface
import io.wanjuan.app.lib.theme.applyUiLabelStyle
import io.wanjuan.app.lib.theme.applyUiSectionTitleStyle
import io.wanjuan.app.lib.theme.primaryTextColor
import io.wanjuan.app.utils.ColorUtils
import io.wanjuan.app.utils.dpToPx

object SourceSelectDialog {

    fun <T> show(
        context: android.content.Context,
        title: CharSequence,
        items: List<T>,
        selectedKey: String?,
        displayName: (T) -> String,
        searchTexts: (T) -> List<String>,
        searchHint: String? = null,
        itemKey: (T) -> String,
        onSelect: (T) -> Unit
    ) {
        if (items.isEmpty()) return
        var dialog: AlertDialog? = null
        var filteredItems = items.toList()
        lateinit var recyclerView: RecyclerView
        fun scrollToSelectedItem() {
            val selectedIndex = filteredItems.indexOfFirst { itemKey(it) == selectedKey }
            if (selectedIndex >= 0) {
                recyclerView.post {
                    recyclerView.scrollToPosition(selectedIndex)
                }
            }
        }
        val adapter = object : RecyclerView.Adapter<SourceViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceViewHolder {
                return SourceViewHolder(SourceOptionView(parent.context))
            }

            override fun getItemCount(): Int = filteredItems.size

            override fun onBindViewHolder(holder: SourceViewHolder, position: Int) {
                val item = filteredItems[position]
                val selected = itemKey(item) == selectedKey
                holder.bind(displayName(item), selected) {
                    dialog?.dismiss()
                    onSelect(item)
                }
            }
        }
        val searchView = SearchView(context).apply {
            queryHint = searchHint ?: context.getString(R.string.screen)
            setIconifiedByDefault(false)
            isIconified = false
            isSubmitButtonEnabled = false
            background = GradientDrawable().apply {
                cornerRadius = UiCorner.searchRadius(10f)
                setColor(ContextCompat.getColor(context, R.color.background_menu))
            }
            setPadding(4.dpToPx(), 0, 4.dpToPx(), 0)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = true

                override fun onQueryTextChange(newText: String?): Boolean {
                    val key = newText.orEmpty().trim()
                    filteredItems = if (key.isBlank()) {
                        items
                    } else {
                        items.filter { item ->
                            searchTexts(item).any { text -> text.contains(key, true) }
                        }
                    }
                    adapter.notifyDataSetChanged()
                    scrollToSelectedItem()
                    return true
                }
            })
            setOnCloseListener {
                setQuery("", false)
                isIconified = false
                true
            }
        }
        searchView.applyUiBodyTypeface(context)
        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                360.dpToPx()
            ).apply {
                topMargin = 10.dpToPx()
            }
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            background = UiCorner.opaqueRounded(
                ContextCompat.getColor(context, R.color.background_card),
                UiCorner.panelRadius(context)
            )
            setPadding(14.dpToPx(), 14.dpToPx(), 14.dpToPx(), 12.dpToPx())
            addView(
                TextView(context).apply {
                    text = title
                    applyUiSectionTitleStyle(context)
                    textSize = 18f
                    includeFontPadding = false
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(2.dpToPx(), 0, 2.dpToPx(), 12.dpToPx())
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    32.dpToPx()
                )
            )
            addView(
                searchView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    42.dpToPx()
                )
            )
            addView(recyclerView)
        }
        dialog = AlertDialog.Builder(context)
            .setView(container)
            .create()
        dialog.setOnShowListener {
            container.requestFocus()
            searchView.clearFocus()
            scrollToSelectedItem()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private class SourceOptionView(context: android.content.Context) : TextView(context) {
        private val accentColor = context.accentColor
        private val normalTextColor = context.primaryTextColor
        private val selectedBackground = UiCorner.actionSelector(
            ColorUtils.adjustAlpha(accentColor, 0.16f),
            ColorUtils.adjustAlpha(accentColor, 0.24f),
            UiCorner.actionRadius(context)
        )
        private val normalBackground = UiCorner.actionSelector(
            Color.TRANSPARENT,
            ContextCompat.getColor(context, R.color.background_menu),
            UiCorner.actionRadius(context)
        )

        init {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            minHeight = 48.dpToPx()
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            applyUiLabelStyle(context)
            textSize = 15f
            setPadding(18.dpToPx(), 0, 18.dpToPx(), 0)
            isClickable = true
            isFocusable = true
        }

        fun bindSelection(selected: Boolean) {
            setTextColor(if (selected) accentColor else normalTextColor)
            background = if (selected) selectedBackground else normalBackground
        }
    }

    private class SourceViewHolder(private val rowView: SourceOptionView) : RecyclerView.ViewHolder(rowView) {
        fun bind(title: CharSequence, selected: Boolean, onClick: () -> Unit) {
            rowView.text = title
            rowView.bindSelection(selected)
            rowView.setOnClickListener { onClick() }
        }
    }
}
