package io.wanjuan.app.ui.config

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import io.wanjuan.app.R
import io.wanjuan.app.constant.EventBus
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.lib.prefs.NameListPreference
import io.wanjuan.app.lib.prefs.fragment.PreferenceFragment
import io.wanjuan.app.lib.theme.primaryColor
import io.wanjuan.app.utils.postEvent
import io.wanjuan.app.utils.setEdgeEffectColor

class SubscriptionConfigFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var targetKeyHandled = false
    private var rssModePref: NameListPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_config_subscription)
        rssModePref = findPreference(KEY_RSS_MODE)
        val useModern = preferenceManager.sharedPreferences
            ?.getBoolean(PreferKey.modernRssPage, true) ?: true
        rssModePref?.value = if (useModern) PAGE_MODE_MODERN else PAGE_MODE_LEGACY
        updateModeSummary(rssModePref)
        rssModePref?.setOnPreferenceChangeListener { _, newValue ->
            val nextValue = newValue?.toString().orEmpty()
            val useModernMode = nextValue == PAGE_MODE_MODERN
            rssModePref?.value = nextValue
            preferenceManager.sharedPreferences?.edit()
                ?.putBoolean(PreferKey.modernRssPage, useModernMode)
                ?.apply()
            updateModeSummary(rssModePref)
            postEvent(EventBus.NOTIFY_MAIN, false)
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.subscription_settings_title)
        listView.setEdgeEffectColor(primaryColor)
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        consumeTargetKey()
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PreferKey.showRss -> postEvent(EventBus.NOTIFY_MAIN, true)
            PreferKey.modernRssPage,
            PreferKey.mergeDiscoveryRss -> postEvent(EventBus.NOTIFY_MAIN, false)
        }
    }

    private fun consumeTargetKey() {
        if (targetKeyHandled) return
        val rawTargetKey = activity?.intent?.getStringExtra("targetKey")?.trim().orEmpty()
        val targetKey = when (rawTargetKey) {
            KEY_MODERN_RSS_PAGE,
            KEY_SEARCH_JUMP_MODERN_RSS_PAGE,
            KEY_SEARCH_JUMP_RSS_MODE -> KEY_RSS_MODE
            else -> rawTargetKey
        }
        if (targetKey.isBlank()) return
        val preference = findPreference<Preference>(targetKey) ?: return
        targetKeyHandled = true
        listView.post {
            scrollToPreference(preference)
            if (preference is SwitchPreferenceCompat) {
                preference.isChecked = !preference.isChecked
            } else {
                onPreferenceTreeClick(preference)
            }
            activity?.intent?.removeExtra("targetKey")
        }
    }

    private fun updateModeSummary(modePref: NameListPreference?) {
        modePref ?: return
        val index = modePref.findIndexOfValue(modePref.value)
        modePref.summary = if (index >= 0) modePref.entries[index] else modePref.summary
    }

    companion object {
        private const val PAGE_MODE_MODERN = "modern"
        private const val PAGE_MODE_LEGACY = "legacy"
        private const val KEY_RSS_MODE = "modernRssMode"
        private const val KEY_MODERN_RSS_PAGE = "modernRssPage"
        private const val KEY_SEARCH_JUMP_MODERN_RSS_PAGE = "search_jump_modernRssPage"
        private const val KEY_SEARCH_JUMP_RSS_MODE = "search_jump_modernRssMode"
    }
}
