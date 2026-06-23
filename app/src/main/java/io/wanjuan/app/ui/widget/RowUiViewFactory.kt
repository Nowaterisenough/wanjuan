package io.wanjuan.app.ui.widget

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.wanjuan.app.R
import io.wanjuan.app.data.entities.rule.RowUi
import io.wanjuan.app.databinding.ItemFilletTextBinding
import io.wanjuan.app.databinding.ItemSelectorSingleBinding
import io.wanjuan.app.lib.theme.applyUiBodyTypeface
import io.wanjuan.app.lib.theme.dialogSurfaceBackground
import io.wanjuan.app.utils.dpToPx
import io.wanjuan.app.utils.setSelectionSafely

object RowUiViewFactory {

    fun applyModernRowUiStyle(rowUi: RowUi, view: View) {
        rowUi.applyModernStyle(view)
        view.minimumHeight = 44.dpToPx()
        if (view.paddingLeft == 0 && view.paddingRight == 0) {
            view.setPadding(12.dpToPx(), 4.dpToPx(), 12.dpToPx(), 4.dpToPx())
        }
    }

    fun applyModernTextButtonStyle(rowUi: RowUi, textView: TextView) {
        rowUi.applyModernStyle(textView)
        textView.minHeight = 44.dpToPx()
        textView.maxLines = 2
        textView.ellipsize = android.text.TextUtils.TruncateAt.END
        textView.includeFontPadding = false
        textView.setPadding(14.dpToPx(), 0, 14.dpToPx(), 0)
    }

    fun selectView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        rowUi: RowUi,
        chars: List<String>,
        selectedValue: String?,
        onSelected: (String) -> Unit,
        onLoadMore: (((List<String>, String?) -> Unit) -> Unit)? = null
    ): ItemSelectorSingleBinding {
        val binding = ItemSelectorSingleBinding.inflate(inflater, parent, false)
        applyModernRowUiStyle(rowUi, binding.root)
        bindSelectTitle(rowUi, binding)
        val selector = binding.spType
        val options = normalizeSelectOptions(chars, selectedValue ?: rowUi.default)
            .toMutableList()
        var popupWindow: PopupWindow? = null
        var popupAdapter: ArrayAdapter<String>? = null
        var loadMoreInFlight = false
        var requestedAtCount = -1
        var suppressNextSelectionCallback = false
        var suppressedSelectionPosition = -1
        fun setSelectionSilently(position: Int) {
            val count = selector.adapter?.count ?: 0
            if (count <= 0) return
            val safePosition = position.coerceIn(0, count - 1)
            suppressNextSelectionCallback = selector.selectedItemPosition != safePosition
            suppressedSelectionPosition = if (suppressNextSelectionCallback) safePosition else -1
            selector.setSelectionSafely(safePosition)
        }
        val adapter = object : ArrayAdapter<String>(
            parent.context,
            R.layout.item_text_common,
            options.toMutableList()
        ) {
            fun updateOptions(chars: List<String>, selectedValue: String?) {
                val newOptions = normalizeSelectOptions(chars, selectedValue)
                val currentValue = selectedValue
                    ?: selector.selectedItem?.toString()
                    ?: rowUi.default.orEmpty()
                options.clear()
                options.addAll(newOptions.ifEmpty { listOf(currentValue).filter { it.isNotBlank() } })
                clear()
                addAll(options)
                notifyDataSetChanged()
                popupAdapter?.clear()
                popupAdapter?.addAll(options)
                popupAdapter?.notifyDataSetChanged()
                val selectedIndex = getPosition(currentValue).takeIf { it >= 0 } ?: 0
                setSelectionSilently(selectedIndex)
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getView(position, convertView, parent).apply {
                    applyUiBodyTypeface(context)
                }
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getDropDownView(position, convertView, parent).apply {
                    applyDropdownRowSurface(this)
                }
            }
        }
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        selector.adapter = adapter
        selector.setSelectionSafely(options.indexOf(selectedValue ?: rowUi.default ?: ""))
        fun selectOption(value: String) {
            val selectedIndex = adapter.getPosition(value).takeIf { it >= 0 } ?: return
            setSelectionSilently(selectedIndex)
            onSelected(value)
        }
        selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            var isInitializing = true
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isInitializing) {
                    isInitializing = false
                    return
                }
                if (suppressNextSelectionCallback) {
                    val shouldSuppress = position == suppressedSelectionPosition
                    suppressNextSelectionCallback = false
                    suppressedSelectionPosition = -1
                    if (shouldSuppress) return
                }
                adapter.getItem(position)?.let(onSelected)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        if (onLoadMore != null) {
            selector.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    popupWindow?.dismiss()
                    requestedAtCount = -1
                    val listView = ListView(parent.context).apply {
                        divider = null
                        background = dropdownSurfaceBackground(context)
                        cacheColorHint = ContextCompat.getColor(context, R.color.dialog_surface)
                        isVerticalScrollBarEnabled = true
                        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                    }
                    popupAdapter = object : ArrayAdapter<String>(
                        parent.context,
                        R.layout.item_spinner_dropdown,
                        options.toMutableList()
                    ) {
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                            return super.getView(position, convertView, parent).apply {
                                applyDropdownRowSurface(this)
                            }
                        }
                    }
                    listView.adapter = popupAdapter
                    fun requestMoreIfAtBottom() {
                        if (options.isEmpty()
                            || loadMoreInFlight
                            || requestedAtCount == options.size
                        ) {
                            return
                        }
                        if (listView.lastVisiblePosition < options.lastIndex) return
                        loadMoreInFlight = true
                        requestedAtCount = options.size
                        onLoadMore.invoke { updatedChars, updatedSelectedValue ->
                            loadMoreInFlight = false
                            adapter.updateOptions(updatedChars, updatedSelectedValue)
                            listView.postDelayed({ requestMoreIfAtBottom() }, 250L)
                        }
                    }
                    listView.setOnItemClickListener { _, _, position, _ ->
                        val value = popupAdapter?.getItem(position) ?: return@setOnItemClickListener
                        popupWindow?.dismiss()
                        selectOption(value)
                    }
                    listView.setOnScrollListener(object : AbsListView.OnScrollListener {
                        override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                            if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                                requestMoreIfAtBottom()
                            }
                        }

                        override fun onScroll(
                            view: AbsListView?,
                            firstVisibleItem: Int,
                            visibleItemCount: Int,
                            totalItemCount: Int
                        ) {
                            if (visibleItemCount > 0 && firstVisibleItem + visibleItemCount >= totalItemCount) {
                                requestMoreIfAtBottom()
                            }
                        }
                    })
                    val popupWidth = selector.width
                        .takeIf { it > 0 }
                        ?: binding.root.width.takeIf { it > 0 }
                        ?: 220.dpToPx()
                    popupWindow = PopupWindow(
                        listView,
                        popupWidth.coerceAtLeast(180.dpToPx()),
                        360.dpToPx(),
                        true
                    ).apply {
                        isOutsideTouchable = true
                        setBackgroundDrawable(parent.context.dialogSurfaceBackground)
                        setOnDismissListener {
                            popupAdapter = null
                        }
                        showAsDropDown(selector)
                    }
                    listView.post { requestMoreIfAtBottom() }
                    listView.postDelayed({ requestMoreIfAtBottom() }, 250L)
                }
                true
            }
        }
        return binding
    }

    private fun normalizeSelectOptions(chars: List<String>, fallback: String?): List<String> {
        val options = chars
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return options.ifEmpty { listOf(fallback.orEmpty()).filter { it.isNotBlank() } }
    }

    private fun applyDropdownRowSurface(view: View) {
        val context = view.context
        view.background = dropdownSurfaceBackground(context)
        view.applyUiBodyTypeface(context)
    }

    private fun dropdownSurfaceBackground(context: Context): ColorDrawable {
        return ColorDrawable(ContextCompat.getColor(context, R.color.dialog_surface))
    }

    fun bindSelectTitle(rowUi: RowUi, binding: ItemSelectorSingleBinding) {
        val displayName = (rowUi.viewName ?: rowUi.name).takeIf { it.isNotBlank() }
        binding.spName.text = displayName?.let { "$it:" }.orEmpty()
        binding.spName.visibility = if (displayName.isNullOrBlank()) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    fun buttonView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        rowUi: RowUi,
        text: CharSequence,
        onClick: (View) -> Unit
    ): ItemFilletTextBinding {
        val binding = ItemFilletTextBinding.inflate(inflater, parent, false)
        applyModernTextButtonStyle(rowUi, binding.textView)
        binding.textView.text = text
        binding.root.setOnClickListener(onClick)
        return binding
    }

    fun applyJustify(rowUi: RowUi, view: View, textView: TextView? = view as? TextView) {
        rowUi.style().apply {
            when (layout_justifySelf) {
                "flex_start" -> textView?.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                "flex_end" -> textView?.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            }
            apply(view)
        }
    }
}
