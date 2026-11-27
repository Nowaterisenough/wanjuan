package io.wanjuan.app.ui.association

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import io.wanjuan.app.R
import io.wanjuan.app.base.adapter.ItemViewHolder
import io.wanjuan.app.base.adapter.RecyclerAdapter
import io.wanjuan.app.data.entities.DictRule
import io.wanjuan.app.databinding.ItemSourceImportBinding
import io.wanjuan.app.ui.widget.dialog.CodeDialog
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject
import io.wanjuan.app.utils.showDialogFragment
import splitties.views.onClick

class ImportDictRuleDialog() : ImportPreviewDialog<DictRule>(R.string.import_dict_rule),
    CodeDialog.Callback {

    constructor(source: String, finishOnDismiss: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString("source", source)
            putBoolean("finishOnDismiss", finishOnDismiss)
        }
    }

    override val viewModel by viewModels<ImportDictRuleViewModel>()
    override val adapter by lazy { SourcesAdapter(requireContext()) }

    inner class SourcesAdapter(context: Context) :
        RecyclerAdapter<DictRule, ItemSourceImportBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemSourceImportBinding {
            return ItemSourceImportBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemSourceImportBinding,
            item: DictRule,
            payloads: MutableList<Any>
        ) {
            binding.apply {
                cbSourceName.isChecked = viewModel.selectStatus[holder.layoutPosition]
                cbSourceName.text = item.name
                val localSource = viewModel.checkSources[holder.layoutPosition]
                tvSourceState.text = when (localSource) {
                    null -> "新增"
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
            GSON.fromJsonObject<DictRule>(code).getOrNull()?.let { source ->
                viewModel.allSources[it] = source
                adapter.setItem(it, source)
            }
        }
    }
}
