package io.wanjuan.app.ui.association

import android.os.Bundle
import io.wanjuan.app.base.BaseActivity
import io.wanjuan.app.constant.SourceType
import io.wanjuan.app.databinding.ActivityTranslucenceBinding
import io.wanjuan.app.utils.showDialogFragment
import io.wanjuan.app.utils.viewbindingdelegate.viewBinding

private const val JAVDB_SOURCE_ORIGIN = "https://javdb.com/"

class OpenUrlConfirmActivity :
    BaseActivity<ActivityTranslucenceBinding>() {

    override val binding by viewBinding(ActivityTranslucenceBinding::inflate)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val uri = intent.getStringExtra("uri") ?: return finish()
        val mimeType = intent.getStringExtra("mimeType")
        val sourceOrigin = intent.getStringExtra("sourceOrigin")
        val sourceName = intent.getStringExtra("sourceName")
        val sourceType = intent.getIntExtra("sourceType", SourceType.book)
        if (shouldSkipAppConfirm(sourceOrigin)) {
            OpenUrlLauncher.open(this, uri, mimeType)
            finish()
        } else {
            showDialogFragment(OpenUrlConfirmDialog(uri, mimeType, sourceOrigin, sourceName, sourceType))
        }
    }

    private fun shouldSkipAppConfirm(sourceOrigin: String?): Boolean {
        return sourceOrigin == JAVDB_SOURCE_ORIGIN
    }

}
