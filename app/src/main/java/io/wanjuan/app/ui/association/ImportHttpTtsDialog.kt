package io.wanjuan.app.ui.association

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import io.wanjuan.app.R
import io.wanjuan.app.base.adapter.ItemViewHolder
import io.wanjuan.app.base.adapter.RecyclerAdapter
import io.wanjuan.app.data.entities.HttpTTS
import io.wanjuan.app.databinding.ItemSourceImportBinding
import io.wanjuan.app.ui.widget.dialog.CodeDialog
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.showDialogFragment
import splitties.views.onClick

class ImportHttpTtsDialog() : ImportPreviewDialog<HttpTTS>(R.string.import_tts),
    CodeDialog.Callback {

    constructor(source: String, finishOnDismiss: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString("source", source)
            putBoolean("finishOnDismiss", finishOnDismiss)
        }
    }

    override val viewModel by viewModels<ImportHttpTtsViewModel>()
    override val adapter by lazy { SourcesAdapter(requireContext()) }

    inner class SourcesAdapter(context: Context) :
        RecyclerAdapter<HttpTTS, ItemSourceImportBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemSourceImportBinding {
            return ItemSourceImportBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemSourceImportBinding,
            item: HttpTTS,
            payloads: MutableList<Any>
        ) {
            binding.apply {
                cbSourceName.isChecked = viewModel.selectStatus[holder.layoutPosition]
                cbSourceName.text = item.name
                val localSource = viewModel.checkSources[holder.layoutPosition]
                tvSourceState.text = when {
                    localSource == null -> "新增"
                    item.lastUpdateTime > localSource.lastUpdateTime -> "更新"
                    else -> "已有"
                }
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemSourceImportBinding) {
            binding.apply {
                cbSourceName.setOnUserCheckedChangeListener { isChecked ->
                    viewModel.selectStatus[holder.layoutPosition] = isChecked
                    updateSelection()
                }
                root.onClick {
                    cbSourceName.isChecked = !cbSourceName.isChecked
                    viewModel.selectStatus[holder.layoutPosition] = cbSourceName.isChecked
                    updateSelection()
                }
                tvOpen.setOnClickListener {
                    val source = viewModel.allSources[holder.layoutPosition]
                    showDialogFragment(
                        CodeDialog(
                            GSON.toJson(source),
                            disableEdit = false,
                            requestId = holder.layoutPosition.toString()
                        )
                    )
                }
            }
        }

    }

    override fun onCodeSave(code: String, requestId: String?) {
        requestId?.toInt()?.let {
            HttpTTS.fromJson(code).getOrNull()?.let { source ->
                viewModel.allSources[it] = source
                adapter.setItem(it, source)
            }
        }
    }
}
