package io.wanjuan.app.ui.book.search

import android.content.Context
import android.view.ViewGroup
import io.wanjuan.app.base.adapter.ItemViewHolder
import io.wanjuan.app.base.adapter.RecyclerAdapter
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.databinding.ItemSearchHistoryChipBinding


class BookAdapter(context: Context, val callBack: CallBack) :
    RecyclerAdapter<Book, ItemSearchHistoryChipBinding>(context) {

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getViewBinding(parent: ViewGroup): ItemSearchHistoryChipBinding {
        return ItemSearchHistoryChipBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemSearchHistoryChipBinding,
        item: Book,
        payloads: MutableList<Any>
    ) {
        binding.run {
            textView.text = item.name
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemSearchHistoryChipBinding) {
        holder.itemView.apply {
            setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    callBack.showBookInfo(it)
                }
            }
        }
    }

    interface CallBack {
        fun showBookInfo(book: Book)
    }
}
