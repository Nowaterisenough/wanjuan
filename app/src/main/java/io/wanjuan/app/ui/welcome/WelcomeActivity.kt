package io.wanjuan.app.ui.welcome

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.postDelayed
import io.wanjuan.app.R
import io.wanjuan.app.base.BaseActivity
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.data.appDb
import io.wanjuan.app.databinding.ActivityWelcomeBinding
import io.wanjuan.app.lib.theme.backgroundColor
import io.wanjuan.app.ui.book.read.ReadBookActivity
import io.wanjuan.app.ui.main.MainActivity
import io.wanjuan.app.utils.fullScreen
import io.wanjuan.app.utils.getPrefBoolean
import io.wanjuan.app.utils.getPrefInt
import io.wanjuan.app.utils.setStatusBarColorAuto
import io.wanjuan.app.utils.startActivity
import io.wanjuan.app.utils.viewbindingdelegate.viewBinding

open class WelcomeActivity : BaseActivity<ActivityWelcomeBinding>() {

    override val binding by viewBinding(ActivityWelcomeBinding::inflate)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        if (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0) {
            // 避免从桌面启动程序后，会重新实例化入口类的activity
            finish()
        } else {
            val welcomeShowTime = getPrefInt(PreferKey.welcomeShowTime, 500)
            if (welcomeShowTime == 0) {
                startMainActivity()
            } else {
                binding.root.postDelayed(welcomeShowTime.toLong()) { startMainActivity() }
            }
        }
        binding.tvWanjuan.visibility = View.GONE
        binding.ivBook.visibility = View.GONE
        binding.tvGzh.visibility = View.GONE
    }

    override fun setupSystemBar() {
        fullScreen()
        setStatusBarColorAuto(backgroundColor, true, fullScreen)
        upNavigationBarColor()
    }

    override fun upBackgroundImage() {
        window.decorView.setBackgroundResource(R.drawable.bg_welcome_preview)
    }

    private fun startMainActivity() {
        startActivity<MainActivity>()
        if (getPrefBoolean(PreferKey.defaultToRead) && appDb.bookDao.lastReadBook != null) {
            startActivity<ReadBookActivity>()
        }
        finish()
    }

}

class Launcher1 : WelcomeActivity()
class Launcher2 : WelcomeActivity()
class Launcher3 : WelcomeActivity()
class Launcher4 : WelcomeActivity()
class Launcher5 : WelcomeActivity()
class Launcher6 : WelcomeActivity()
class Launcher7 : WelcomeActivity()
