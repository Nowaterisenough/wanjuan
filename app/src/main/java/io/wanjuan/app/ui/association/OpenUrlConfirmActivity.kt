package io.wanjuan.app.ui.association

import android.os.Bundle
import io.wanjuan.app.base.BaseActivity
import io.wanjuan.app.constant.SourceType
import io.wanjuan.app.databinding.ActivityTranslucenceBinding
import io.wanjuan.app.utils.showDialogFragment
import io.wanjuan.app.utils.viewbindingdelegate.viewBinding

class OpenUrlConfirmActivity :
    BaseActivity<ActivityTranslucenceBinding>() {

    override val binding by viewBinding(ActivityTranslucenceBinding::inflate)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        intent.getStringExtra("uri")?.let {
            val mimeType = intent.getStringExtra("mimeType")
            val sourceOrigin = intent.getStringExtra("sourceOrigin")
            val sourceName = intent.getStringExtra("sourceName")
            val sourceType = intent.getIntExtra("sourceType", SourceType.book)
            showDialogFragment(OpenUrlConfirmDialog(it, mimeType, sourceOrigin, sourceName, sourceType))
        } ?: finish()
    }

}
