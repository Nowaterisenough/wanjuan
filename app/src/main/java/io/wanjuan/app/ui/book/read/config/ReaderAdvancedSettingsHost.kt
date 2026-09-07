package io.wanjuan.app.ui.book.read.config

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import io.wanjuan.app.R
import io.wanjuan.app.utils.activity

/** A stable container lets preference dialogs and restored fragments keep their owner. */
class ReaderAdvancedSettingsHost(context: Context) : FrameLayout(context) {
    private var initialized = false
    private val syncFragment = Runnable {
        val manager = activity?.supportFragmentManager
            ?: return@Runnable
        if (!isAttachedToWindow || manager.isDestroyed || manager.isStateSaved) return@Runnable
        val fragment = manager.findFragmentByTag(FRAGMENT_TAG)
        if (isShown) {
            if (fragment == null) {
                manager.beginTransaction()
                    .add(id, ReaderAdvancedSettingsFragment(), FRAGMENT_TAG)
                    .commitNow()
            }
        } else if (fragment != null) {
            manager.beginTransaction().remove(fragment).commitNow()
        }
    }

    init {
        id = R.id.reader_advanced_settings_host
        visibility = GONE
        initialized = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleSync()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (initialized) scheduleSync()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(syncFragment)
        super.onDetachedFromWindow()
    }

    fun refreshPreferences() {
        val fragment = activity?.supportFragmentManager
            ?.findFragmentByTag(FRAGMENT_TAG) as? ReaderPreferenceFragment
        if (fragment?.view != null) fragment.listView.adapter?.notifyDataSetChanged()
        scheduleSync()
    }

    private fun scheduleSync() {
        removeCallbacks(syncFragment)
        post(syncFragment)
    }

    private companion object {
        const val FRAGMENT_TAG = "readerAdvancedSettings"
    }
}
