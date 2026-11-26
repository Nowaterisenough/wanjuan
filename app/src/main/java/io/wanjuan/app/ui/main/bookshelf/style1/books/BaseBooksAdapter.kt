package io.wanjuan.app.ui.main.bookshelf.style1.books

import io.wanjuan.app.ui.main.bookshelf.BookSeries
import io.wanjuan.app.ui.main.bookshelf.SeriesShelfView
import android.view.ViewGroup
import android.content.Context
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.DiffUtil
import androidx.viewbinding.ViewBinding
import io.wanjuan.app.base.adapter.DiffRecyclerAdapter
import io.wanjuan.app.base.adapter.ItemViewHolder
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.help.book.isUpError

abstract class BaseBooksAdapter<VB : ViewBinding>(context: Context, private val seriesCallback: CallBack, private val grid: Boolean = false) :
    DiffRecyclerAdapter<Any, VB>(context) {

    override val keepScrollPosition = true

    override val diffItemCallback: DiffUtil.ItemCallback<Any> =
        object : DiffUtil.ItemCallback<Any>() {

            override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
                if (oldItem is BookSeries && newItem is BookSeries) return oldItem.key == newItem.key
                if (oldItem !is Book || newItem !is Book) return false
                return oldItem.name == newItem.name
                        && oldItem.author == newItem.author
            }

            override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
                if (oldItem !is Book || newItem !is Book) return false
                return when {
                    oldItem.durChapterTime != newItem.durChapterTime -> false
                    oldItem.name != newItem.name -> false
                    oldItem.author != newItem.author -> false
                    oldItem.durChapterTitle != newItem.durChapterTitle -> false
                    oldItem.latestChapterTitle != newItem.latestChapterTitle -> false
                    oldItem.lastCheckCount != newItem.lastCheckCount -> false
                    oldItem.isUpError != newItem.isUpError -> false
                    oldItem.getDisplayCover() != newItem.getDisplayCover() -> false
                    oldItem.getUnreadChapterNum() != newItem.getUnreadChapterNum() -> false
                    else -> true
                }
            }

            override fun getChangePayload(oldItem: Any, newItem: Any): Any? {
                if (oldItem !is Book || newItem !is Book) return null
                val bundle = bundleOf()
                if (oldItem.name != newItem.name) {
                    bundle.putString("name", newItem.name)
                }
                if (oldItem.author != newItem.author) {
                    bundle.putString("author", newItem.author)
                }
                if (oldItem.durChapterTitle != newItem.durChapterTitle) {
                    bundle.putString("dur", newItem.durChapterTitle)
                }
                if (oldItem.latestChapterTitle != newItem.latestChapterTitle) {
                    bundle.putString("last", newItem.latestChapterTitle)
                }
                if (oldItem.getDisplayCover() != newItem.getDisplayCover()) {
                    bundle.putString("cover", newItem.getDisplayCover())
                }
                if (oldItem.lastCheckCount != newItem.lastCheckCount
                    || oldItem.durChapterTime != newItem.durChapterTime
                    || oldItem.getUnreadChapterNum() != newItem.getUnreadChapterNum()
                    || oldItem.isUpError != newItem.isUpError
                    || oldItem.lastCheckCount != newItem.lastCheckCount
                ) {
                    bundle.putBoolean("refresh", true)
                }
                if (oldItem.latestChapterTime != newItem.latestChapterTime) {
                    bundle.putBoolean("lastUpdateTime", true)
                }
                if (bundle.isEmpty) return null
                return bundle
            }

        }

    // Distinct types also keep the grid and list series cards apart in the shared holder pools.
    override fun getItemViewType(position: Int): Int = if (getItem(position) is BookSeries) {
        if (grid) 3 else 2
    } else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        if (viewType == 0) return super.onCreateViewHolder(parent, viewType)
        val view = SeriesShelfView(context, grid)
        return ItemViewHolder(ViewBinding { view })
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int, payloads: MutableList<Any>) {
        val series = getItem(position) as? BookSeries
        if (series == null) super.onBindViewHolder(holder, position, payloads)
        else (holder.itemView as SeriesShelfView).bind(series,
            { seriesCallback.openSeries(series, false) }, { seriesCallback.openSeries(series, true) })
    }

    final override fun convert(holder: ItemViewHolder, binding: VB, item: Any, payloads: MutableList<Any>) {
        bindBook(holder, binding, item as Book, payloads)
    }

    protected abstract fun bindBook(holder: ItemViewHolder, binding: VB, item: Book, payloads: MutableList<Any>)

    override fun onViewRecycled(holder: ItemViewHolder) {
        super.onViewRecycled(holder)
        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnLongClickListener(null)
    }

    fun notification(bookUrl: String) {
        getItems().forEachIndexed { i, it ->
            if (it is Book && it.bookUrl == bookUrl) {
                notifyItemChanged(i, bundleOf(Pair("refresh", null), Pair("lastUpdateTime", null)))
                return
            }
        }
    }

    fun upLastUpdateTime() {
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("lastUpdateTime", null)))
    }

    interface CallBack {
        fun openSeries(series: BookSeries, details: Boolean)
        fun open(book: Book)
        fun openBookInfo(book: Book)
        fun isUpdate(bookUrl: String): Boolean
        fun isWaitingUpdate(bookUrl: String): Boolean
    }
}
