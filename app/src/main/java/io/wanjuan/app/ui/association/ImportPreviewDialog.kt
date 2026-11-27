package io.wanjuan.app.ui.association

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import io.wanjuan.app.R
import io.wanjuan.app.base.BaseDialogFragment
import io.wanjuan.app.base.adapter.RecyclerAdapter
import io.wanjuan.app.databinding.DialogRecyclerViewBinding
import io.wanjuan.app.databinding.ItemSourceImportBinding
import io.wanjuan.app.lib.theme.primaryColor
import io.wanjuan.app.utils.setLayout
import io.wanjuan.app.utils.viewbindingdelegate.viewBinding

abstract class ImportPreviewDialog<T>(@StringRes private val titleRes: Int) :
    BaseDialogFragment(R.layout.dialog_recycler_view) {

    protected val binding by viewBinding(DialogRecyclerViewBinding::bind)
    protected abstract val viewModel: ImportPreviewViewModel<T>
    protected abstract val adapter: RecyclerAdapter<T, ItemSourceImportBinding>
    protected open fun initMenu() = Unit

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (arguments?.getBoolean("finishOnDismiss") == true && activity?.isChangingConfigurations == false) {
            activity?.finish()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val source = arguments?.getString("source")
        if (source.isNullOrEmpty()) {
            dismissAllowingStateLoss()
            return
        }
        binding.toolBar.setTitle(titleRes)
        binding.toolBar.setBackgroundColor(primaryColor)
        initMenu()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.tvCancel.isVisible = true
        binding.tvCancel.setOnClickListener { dismissAllowingStateLoss() }
        binding.tvOk.isVisible = true
        binding.tvOk.isEnabled = false
        binding.tvOk.setOnClickListener { viewModel.confirm() }
        binding.tvFooterLeft.setOnClickListener {
            val select = viewModel.selectStatus.any { !it }
            viewModel.selectStatus.fill(select)
            adapter.notifyDataSetChanged()
            updateSelection()
        }
        viewModel.state.observe(viewLifecycleOwner) { state ->
            val ready = state == ImportPreviewState.Ready
            isCancelable = state != ImportPreviewState.Saving
            binding.tvCancel.isEnabled = isCancelable
            binding.rotateLoading.isVisible = state == ImportPreviewState.Loading || state == ImportPreviewState.Saving
            binding.recyclerView.isVisible = ready
            binding.tvFooterLeft.isVisible = ready
            binding.toolBar.menu.setGroupEnabled(0, ready)
            binding.tvMsg.isVisible = state is ImportPreviewState.Error
            binding.tvOk.isEnabled = false
            when (state) {
                ImportPreviewState.Ready -> {
                    adapter.setItems(viewModel.allSources)
                    updateSelection()
                }
                is ImportPreviewState.Error -> binding.tvMsg.text = state.message
                ImportPreviewState.Finished -> dismissAllowingStateLoss()
                else -> Unit
            }
        }
        viewModel.load(source)
    }

    protected fun updateSelection() {
        val selected = viewModel.selectStatus.count { it }
        val total = viewModel.selectStatus.size
        val label = if (selected == total) R.string.select_cancel_count else R.string.select_all_count
        binding.tvFooterLeft.text = getString(label, selected, total)
        binding.tvOk.isEnabled = viewModel.state.value == ImportPreviewState.Ready && selected > 0
    }
}
