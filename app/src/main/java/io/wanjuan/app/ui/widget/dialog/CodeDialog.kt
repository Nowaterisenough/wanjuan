package io.wanjuan.app.ui.widget.dialog

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.wanjuan.app.R
import io.wanjuan.app.base.BaseDialogFragment
import io.wanjuan.app.databinding.DialogCodeViewBinding
import io.wanjuan.app.help.IntentData
import io.wanjuan.app.lib.theme.primaryColor
import io.wanjuan.app.ui.widget.code.addJsPattern
import io.wanjuan.app.ui.widget.code.addJsonPattern
import io.wanjuan.app.ui.widget.code.addWanjuanPattern
import io.wanjuan.app.utils.applyTint
import io.wanjuan.app.utils.disableEdit
import io.wanjuan.app.utils.setLayout
import io.wanjuan.app.utils.viewbindingdelegate.viewBinding

class CodeDialog() : BaseDialogFragment(R.layout.dialog_code_view) {

    constructor(code: String, disableEdit: Boolean = true, requestId: String? = null) : this() {
        arguments = Bundle().apply {
            putBoolean("disableEdit", disableEdit)
            putString("code", IntentData.put(code))
            putString("requestId", requestId)
        }
    }

    val binding by viewBinding(DialogCodeViewBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        if (arguments?.getBoolean("disableEdit") == true) {
            binding.toolBar.title = "code view"
            binding.codeView.disableEdit()
        } else {
            initMenu()
        }
        binding.codeView.addWanjuanPattern()
        binding.codeView.addJsonPattern()
        binding.codeView.addJsPattern()
        arguments?.getString("code")?.let {
            binding.codeView.text = IntentData.get(it)
        }
    }

    private fun initMenu() {
        binding.toolBar.inflateMenu(R.menu.code_edit)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_save -> {
                    binding.codeView.text?.toString()?.let { code ->
                        val requestId = arguments?.getString("requestId")
                        (parentFragment as? Callback)?.onCodeSave(code, requestId)
                            ?: (activity as? Callback)?.onCodeSave(code, requestId)
                    }
                    dismiss()
                }
            }
            return@setOnMenuItemClickListener true
        }
    }


    interface Callback {

        fun onCodeSave(code: String, requestId: String?)

    }

}