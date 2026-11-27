package io.wanjuan.app.ui.association

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import io.wanjuan.app.R
import io.wanjuan.app.base.adapter.ItemViewHolder
import io.wanjuan.app.base.adapter.RecyclerAdapter
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.ReplaceRule
import io.wanjuan.app.databinding.DialogCustomGroupBinding
import io.wanjuan.app.databinding.ItemSourceImportBinding
import io.wanjuan.app.lib.dialogs.alert
import io.wanjuan.app.ui.widget.dialog.CodeDialog
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.dpToPx
import io.wanjuan.app.utils.fromJsonObject
import io.wanjuan.app.utils.putPrefBoolean
import io.wanjuan.app.utils.showDialogFragment
import splitties.views.onClick

class ImportReplaceRuleDialog() : ImportPreviewDialog<ReplaceRule>(R.string.import_replace_rule),
    Toolbar.OnMenuItemClickListener,
    CodeDialog.Callback {

    constructor(source: String, finishOnDismiss: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString("source", source)
            putBoolean("finishOnDismiss", finishOnDismiss)
        }
    }

    override val viewModel by viewModels<ImportReplaceRuleViewModel>()
    override val adapter by lazy { SourcesAdapter(requireContext()) }

    override fun initMenu() {
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.toolBar.inflateMenu(R.menu.import_replace)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_new_group -> alertCustomGroup(item)
            R.id.menu_keep_original_name -> {
                item.isChecked = !item.isChecked
                putPrefBoolean(PreferKey.importKeepName, item.isChecked)
            }
        }
        return true
    }

    private fun alertCustomGroup(item: MenuItem) {
        alert(R.string.diy_edit_source_group) {
            val alertBinding = DialogCustomGroupBinding.inflate(layoutInflater).apply {
                val groups = appDb.replaceRuleDao.allGroups()
                textInputLayout.setHint(R.string.group_name)
                editView.setFilterValues(groups.toList())
                editView.dropDownHeight = 180.dpToPx()
            }
            customView {
                alertBinding.root
            }
            okButton {
                viewModel.isAddGroup = alertBinding.swAddGroup.isChecked
                viewModel.groupName = alertBinding.editView.text?.toString()
                if (viewModel.groupName.isNullOrBlank()) {
                    item.title = getString(R.string.diy_source_group)
                } else {
                    val group = getString(R.string.diy_edit_source_group_title, viewModel.groupName)
                    if (viewModel.isAddGroup) {
                        item.title = "+$group"
                    } else {
                        item.title = group
                    }
                }
            }
            noButton()
        }
    }

    override fun onCodeSave(code: String, requestId: String?) {
        requestId?.toInt()?.let {
            GSON.fromJsonObject<ReplaceRule>(code).getOrNull()?.let { rule ->
                viewModel.allSources[it] = rule
                adapter.setItem(it, rule)
            }
        }
    }

    inner class SourcesAdapter(context: Context) :
        RecyclerAdapter<ReplaceRule, ItemSourceImportBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemSourceImportBinding {
            return ItemSourceImportBinding.inflate(inflater, parent, false)
        }

        @SuppressLint("SetTextI18n")
        override fun convert(
            holder: ItemViewHolder,
            binding: ItemSourceImportBinding,
            item: ReplaceRule,
            payloads: MutableList<Any>
        ) {
            binding.run {
                cbSourceName.isChecked = viewModel.selectStatus[holder.layoutPosition]
                cbSourceName.text = if (item.group.isNullOrBlank()) {
                    item.name
                } else {
                    "${item.name}(${item.group})"
                }
                val localRule = viewModel.checkRules[holder.layoutPosition]
                tvSourceState.text = when {
                    localRule == null -> "新增"
                    item.pattern != localRule.pattern
                            || item.replacement != localRule.replacement
                            || item.isRegex != localRule.isRegex
                            || item.scope != localRule.scope -> "更新"

                    else -> "已有"
                }
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemSourceImportBinding) {
            binding.run {
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

}
