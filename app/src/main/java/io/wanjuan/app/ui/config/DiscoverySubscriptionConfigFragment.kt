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

class DiscoverySubscriptionConfigFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var targetKeyHandled = false
    private var discoveryModePref: NameListPreference? = null
    private var rssModePref: NameListPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_config_discovery_subscription)
        discoveryModePref = findPreference(KEY_DISCOVERY_MODE)
        rssModePref = findPreference(KEY_RSS_MODE)
        bindModePreference(
            preference = discoveryModePref,
            booleanKey = PreferKey.modernDiscoveryPage,
            defaultValue = true
        )
        bindModePreference(
            preference = rssModePref,
            booleanKey = PreferKey.modernRssPage,
            defaultValue = true
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.discovery_subscription_settings_title)
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
            PreferKey.showDiscovery,
            PreferKey.showRss -> postEvent(EventBus.NOTIFY_MAIN, true)

            PreferKey.modernDiscoveryPage,
            PreferKey.modernRssPage,
            PreferKey.mergeDiscoveryRss -> postEvent(EventBus.NOTIFY_MAIN, false)
        }
    }

    private fun bindModePreference(
        preference: NameListPreference?,
        booleanKey: String,
        defaultValue: Boolean
    ) {
        preference ?: return
        preference.isPersistent = false
        val useModern = preferenceManager.sharedPreferences
            ?.getBoolean(booleanKey, defaultValue) ?: defaultValue
        preference.value = if (useModern) PAGE_MODE_MODERN else PAGE_MODE_LEGACY
        preference.setOnPreferenceChangeListener { _, newValue ->
            val nextValue = newValue?.toString().orEmpty()
            val useModernMode = nextValue == PAGE_MODE_MODERN
            preference.value = nextValue
            preferenceManager.sharedPreferences?.edit()
                ?.putBoolean(booleanKey, useModernMode)
                ?.apply()
            postEvent(EventBus.NOTIFY_MAIN, false)
            true
        }
    }

    private fun consumeTargetKey() {
        if (targetKeyHandled) return
        val rawTargetKey = activity?.intent?.getStringExtra("targetKey")?.trim().orEmpty()
        if (rawTargetKey.isBlank()) {
            if (tag == ConfigTag.SUBSCRIPTION_CONFIG) {
                targetKeyHandled = true
                scrollToPreference(PreferKey.showRss)
            }
            return
        }
        val key = rawTargetKey.removePrefix("search_jump_")
        val targetKey = when (key) {
            PreferKey.modernDiscoveryPage -> KEY_DISCOVERY_MODE
            PreferKey.modernRssPage -> KEY_RSS_MODE
            else -> key
        }
        val preference = findPreference<Preference>(targetKey) ?: return
        targetKeyHandled = true
        listView.post {
            scrollToPreference(preference)
            if (preference is SwitchPreferenceCompat) {
                preference.isChecked = !preference.isChecked
            } else if (preference is NameListPreference) {
                onDisplayPreferenceDialog(preference)
            }
            activity?.intent?.removeExtra("targetKey")
        }
    }

    companion object {
        private const val PAGE_MODE_MODERN = "modern"
        private const val PAGE_MODE_LEGACY = "legacy"
        private const val KEY_DISCOVERY_MODE = "modernDiscoveryMode"
        private const val KEY_RSS_MODE = "modernRssMode"
    }
}
