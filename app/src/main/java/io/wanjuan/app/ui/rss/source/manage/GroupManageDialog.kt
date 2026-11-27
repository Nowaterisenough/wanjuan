package io.wanjuan.app.ui.rss.source.manage

import androidx.fragment.app.activityViewModels
import io.wanjuan.app.ui.widget.dialog.RuleGroupManageDialog

class GroupManageDialog : RuleGroupManageDialog(showDoneButton = true) {
    override val editor by activityViewModels<RssSourceViewModel>()
}
