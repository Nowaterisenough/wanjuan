package io.wanjuan.app.ui.book.read.config

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceScreen
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView
import io.wanjuan.app.R
import io.wanjuan.app.lib.prefs.fragment.PreferenceFragment

/** Apply the reader's presentation after each preference bind, including recycled rows. */
abstract class ReaderPreferenceFragment : PreferenceFragment() {
    private val dialogStyle = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentStarted(manager: FragmentManager, fragment: Fragment) {
            @Suppress("DEPRECATION")
            if (fragment is DialogFragment && fragment.targetFragment === this@ReaderPreferenceFragment) {
                (fragment.dialog as? AlertDialog)?.let(ReaderUiStyle::styleDialog)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.setPadding(0, 0, 0, ReaderUiStyle.dp(requireContext(), ReaderUiStyle.INSET))
        listView.clipToPadding = false
        parentFragmentManager.registerFragmentLifecycleCallbacks(dialogStyle, false)
    }

    override fun onDestroyView() {
        parentFragmentManager.unregisterFragmentLifecycleCallbacks(dialogStyle)
        super.onDestroyView()
    }

    @SuppressLint("RestrictedApi")
    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> =
        object : PreferenceGroupAdapter(preferenceScreen) {
            override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                val item = holder.itemView
                val colors = ReaderSheetStyle.resolve(item.context)
                val dp = { value: Int -> ReaderUiStyle.dp(item.context, value) }
                val hasSummary = !getItem(position)?.summary.isNullOrEmpty()
                item.minimumHeight = dp(if (hasSummary) ReaderUiStyle.CONTROL_LARGE else ReaderUiStyle.CONTROL_NORMAL)
                val verticalPadding = if (hasSummary) dp(ReaderUiStyle.GAP) else 0
                item.setPadding(0, verticalPadding, 0, verticalPadding)
                item.background = ReaderUiStyle.rounded(item.context, colors.surface)
                (item.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                    setMargins(dp(ReaderUiStyle.INSET), 0, dp(ReaderUiStyle.INSET), 0)
                    item.layoutParams = this
                }
                (holder.findViewById(R.id.preference_title) as? TextView)?.apply {
                    textSize = ReaderUiStyle.TEXT_BODY.toFloat()
                    setTextColor(colors.textColor)
                    isSingleLine = false
                    maxLines = 3
                }
                (holder.findViewById(R.id.preference_desc) as? TextView)?.apply {
                    textSize = ReaderUiStyle.TEXT_CAPTION.toFloat()
                    setTextColor(colors.secondaryTextColor)
                    (layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                        it.topMargin = dp(4)
                        layoutParams = it
                    }
                }
                (holder.findViewById(R.id.preference_icon) as? ImageView)?.apply {
                    setColorFilter(colors.secondaryTextColor)
                    layoutParams = layoutParams.apply {
                        width = dp(ReaderUiStyle.ICON_NORMAL)
                        height = dp(ReaderUiStyle.ICON_NORMAL)
                    }
                }
                (holder.findViewById(R.id.text_view) as? TextView)?.apply {
                    textSize = ReaderUiStyle.TEXT_BODY.toFloat()
                    setTextColor(colors.accentTextColor)
                    minHeight = dp(ReaderUiStyle.CONTROL_COMPACT)
                    maxWidth = dp(160)
                    background = ReaderUiStyle.rounded(context, colors.panelStrong)
                }
                (holder.findViewById(R.id.preference_widget)?.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                    topMargin = 0
                    bottomMargin = 0
                }
                (holder.findViewById(androidx.preference.R.id.switchWidget) as? SwitchCompat)?.let(ReaderUiStyle::tintSwitch)
            }
        }
}
