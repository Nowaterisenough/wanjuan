package io.wanjuan.app.ui.about

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import androidx.core.content.ContextCompat
import io.wanjuan.app.R
import io.wanjuan.app.base.BaseActivity
import io.wanjuan.app.databinding.ActivityAboutBinding
import io.wanjuan.app.lib.theme.UiCorner
import io.wanjuan.app.lib.theme.accentColor
import io.wanjuan.app.utils.openUrl
import io.wanjuan.app.utils.share
import io.wanjuan.app.utils.viewbindingdelegate.viewBinding


class AboutActivity : BaseActivity<ActivityAboutBinding>() {

    override val binding by viewBinding(ActivityAboutBinding::inflate)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.llAbout.background = UiCorner.opaqueRounded(
            ContextCompat.getColor(this, R.color.background_card),
            UiCorner.panelRadius(this)
        )
        val fTag = "aboutFragment"
        var aboutFragment = supportFragmentManager.findFragmentByTag(fTag)
        if (aboutFragment == null) aboutFragment = AboutFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fl_fragment, aboutFragment, fTag)
            .commit()
        binding.tvAppSummary.post {
            kotlin.runCatching {
                val span = ForegroundColorSpan(accentColor)
                val spannableString = SpannableString(binding.tvAppSummary.text)
                val gzh = getString(R.string.wanjuan_gzh)
                val start = spannableString.indexOf(gzh)
                spannableString.setSpan(
                    span, start, start + gzh.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                binding.tvAppSummary.text = spannableString
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.about, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_scoring -> openUrl("market://details?id=$packageName")
            R.id.menu_share_it -> share(
                getString(R.string.app_share_description_sigma),
                getString(R.string.app_name)
            )
        }
        return super.onCompatOptionsItemSelected(item)
    }

}
