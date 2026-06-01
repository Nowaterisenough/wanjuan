package io.wanjuan.app.ui.main.bookshelf.style1.books

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.wanjuan.app.base.adapter.ItemViewHolder
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.databinding.ItemBookshelfList2Binding
import io.wanjuan.app.help.book.isUpError
import io.wanjuan.app.help.book.isLocal
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.utils.gone
import io.wanjuan.app.utils.invisible
import io.wanjuan.app.utils.toTimeAgo
import io.wanjuan.app.utils.visible
import splitties.views.onLongClick

/**
紧凑列表布局
*/
class BooksAdapterList2(
    context: Context,
    private val fragment: Fragment,
    private val callBack: CallBack,
    private val lifecycle: Lifecycle
) : BaseBooksAdapter<ItemBookshelfList2Binding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemBookshelfList2Binding {
        return ItemBookshelfList2Binding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookshelfList2Binding,
        item: Book,
        payloads: MutableList<Any>
    ) = binding.run {
        if (payloads.isEmpty()) {
            tvName.text = item.name
            tvAuthor.text = item.author
            tvRead.text = item.durChapterTitle
            tvLast.text = item.latestChapterTitle
            ivCover.loadThumb(item, false, fragment, lifecycle)
            upRefresh(binding, item)
            upLastUpdateTime(binding, item)
        } else {
            for (i in payloads.indices) {
                val bundle = payloads[i] as Bundle
                bundle.keySet().forEach {
                    when (it) {
                        "name" -> tvName.text = item.name
                        "author" -> tvAuthor.text = item.author
                        "dur" -> tvRead.text = item.durChapterTitle
                        "last" -> tvLast.text = item.latestChapterTitle
                        "cover" -> ivCover.loadThumb(
                            item,
                            false,
                            fragment,
                            lifecycle
                        )

                        "refresh" -> upRefresh(binding, item)
                        "lastUpdateTime" -> upLastUpdateTime(binding, item)
                    }
                }
            }
        }
    }

    private fun upRefresh(binding: ItemBookshelfList2Binding, item: Book) {
        val updating = !item.isLocal && callBack.isUpdate(item.bookUrl)
        binding.vwCoverPendingOverlay.visible(!updating && !item.isLocal && callBack.isWaitingUpdate(item.bookUrl))
        if (updating) {
            binding.bvUnread.invisible()
            binding.rlLoading.visible()
        } else {
            binding.rlLoading.gone()
            if (item.isUpError) {
                binding.bvUnread.setUpdateError()
            } else if (AppConfig.showUnread) {
                binding.bvUnread.setHighlight(item.lastCheckCount > 0)
                binding.bvUnread.setBadgeCount(item.getUnreadChapterNum())
            } else {
                binding.bvUnread.invisible()
            }
        }
    }

    private fun upLastUpdateTime(binding: ItemBookshelfList2Binding, item: Book) {
        if (AppConfig.showLastUpdateTime && !item.isLocal) {
            val time = item.latestChapterTime.toTimeAgo()
            if (binding.tvLastUpdateTime.text != time) {
                binding.tvLastUpdateTime.text = time
            }
        } else {
            binding.tvLastUpdateTime.text = ""
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemBookshelfList2Binding) {
        holder.itemView.apply {
            setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    callBack.open(it)
                }
            }

            onLongClick {
                getItem(holder.layoutPosition)?.let {
                    callBack.openBookInfo(it)
                }
            }
        }
    }
}
