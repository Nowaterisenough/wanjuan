package io.wanjuan.app.ui.book.source.manage

import androidx.fragment.app.activityViewModels
import io.wanjuan.app.ui.widget.dialog.RuleGroupManageDialog

class GroupManageDialog : RuleGroupManageDialog() {
    override val editor by activityViewModels<BookSourceViewModel>()
}
