package io.wanjuan.app.ui.book.read.config

import android.app.Dialog
import android.content.Context
import android.view.KeyEvent
import android.view.ViewGroup
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.databinding.DialogPageKeyBinding
import io.wanjuan.app.lib.theme.dialogSurfaceBackground
import io.wanjuan.app.utils.getPrefString
import io.wanjuan.app.utils.hideSoftInput
import io.wanjuan.app.utils.putPrefString
import io.wanjuan.app.utils.setLayout
import splitties.views.onClick


class PageKeyDialog(context: Context) : Dialog(context) {

    private val binding = DialogPageKeyBinding.inflate(layoutInflater)

    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    init {
        setContentView(binding.root)
        binding.run {
            contentView.background = context.dialogSurfaceBackground
            contentView.clipToOutline = true
            etPrev.setText(context.getPrefString(PreferKey.prevKeys))
            etNext.setText(context.getPrefString(PreferKey.nextKeys))
            tvReset.onClick {
                etPrev.setText("")
                etNext.setText("")
            }
            tvOk.setOnClickListener {
                context.putPrefString(PreferKey.prevKeys, etPrev.text?.toString())
                context.putPrefString(PreferKey.nextKeys, etNext.text?.toString())
                dismiss()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_DEL) {
            if (binding.etPrev.hasFocus()) {
                val editableText = binding.etPrev.editableText
                if (editableText.isEmpty() or editableText.endsWith(",")) {
                    editableText.append(keyCode.toString())
                } else {
                    editableText.append(",").append(keyCode.toString())
                }
                return true
            } else if (binding.etNext.hasFocus()) {
                val editableText = binding.etNext.editableText
                if (editableText.isEmpty() or editableText.endsWith(",")) {
                    editableText.append(keyCode.toString())
                } else {
                    editableText.append(",").append(keyCode.toString())
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun dismiss() {
        super.dismiss()
        currentFocus?.hideSoftInput()
    }

}
