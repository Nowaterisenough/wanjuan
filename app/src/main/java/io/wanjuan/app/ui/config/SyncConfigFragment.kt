package io.wanjuan.app.ui.config

import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.fragment.app.activityViewModels
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import io.wanjuan.app.R
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.lib.prefs.fragment.PreferenceFragment
import io.wanjuan.app.lib.theme.primaryColor
import io.wanjuan.app.sync.SyncManager
import io.wanjuan.app.sync.remote.SyncSettings
import io.wanjuan.app.ui.about.AppLogDialog
import io.wanjuan.app.utils.applyTint
import io.wanjuan.app.utils.getPrefString
import io.wanjuan.app.utils.setEdgeEffectColor
import io.wanjuan.app.utils.showDialogFragment
import io.wanjuan.app.utils.showHelp
import io.wanjuan.app.utils.toastOnUi
import splitties.init.appCtx

class SyncConfigFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener, MenuProvider {

    private val viewModel by activityViewModels<ConfigViewModel>()
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_config_sync)
        listOf(PreferKey.webDavPassword, PreferKey.gitHubSyncToken).forEach { key ->
            findPreference<EditTextPreference>(key)?.setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                it.setSelection(it.text.length)
            }
        }
        updatePreferences()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.sync_settings_title)
        listView.setEdgeEffectColor(primaryColor)
        activity?.addMenuProvider(this, viewLifecycleOwner)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroyView() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroyView()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        updatePreferences()
        if (key in setOf(PreferKey.webDavUrl, PreferKey.webDavAccount, PreferKey.webDavPassword, PreferKey.webDavDir)) {
            viewModel.upWebDavConfig()
        }
    }

    private fun updatePreferences() {
        val github = SyncSettings.provider == SyncSettings.GitHub
        findPreference<Preference>("webdav_config")?.isVisible = !github
        findPreference<Preference>("github_config")?.isVisible = github
        listOf(
            PreferKey.webDavUrl, PreferKey.webDavAccount, PreferKey.webDavPassword,
            PreferKey.webDavDir, PreferKey.webDavDeviceName,
            PreferKey.gitHubSyncRepository, PreferKey.gitHubSyncToken,
            PreferKey.gitHubSyncBranch, PreferKey.gitHubSyncDir
        ).forEach { key ->
            val preference = findPreference<EditTextPreference>(key) ?: return@forEach
            val value = appCtx.getPrefString(key)
            preference.summary = when {
                key == PreferKey.gitHubSyncToken || key == PreferKey.webDavPassword ->
                    getString(if (value.isNullOrBlank()) R.string.sync_secret_empty else R.string.sync_secret_saved)
                !value.isNullOrBlank() -> value
                key == PreferKey.webDavDir || key == PreferKey.gitHubSyncDir ->
                    if (value == null) "wanjuan" else getString(R.string.sync_root_directory)
                key == PreferKey.gitHubSyncBranch -> getString(R.string.github_sync_default_branch)
                key == PreferKey.gitHubSyncRepository -> getString(R.string.github_sync_repository_hint)
                key == PreferKey.webDavDeviceName -> android.os.Build.MODEL
                key == PreferKey.webDavUrl -> getString(R.string.web_dav_url_s)
                else -> getString(R.string.web_dav_account_s)
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "sync_now") {
            if (!SyncSettings.isConfigured) {
                toastOnUi(R.string.sync_configure_first)
                return true
            }
            preference.isEnabled = false
            SyncManager.syncNow(force = true) { result ->
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    preference.isEnabled = true
                    toastOnUi(if (result.isSuccess) {
                        getString(R.string.sync_complete_counts, result.uploaded, result.downloaded,
                            result.inserted, result.updated, result.deleted)
                    } else {
                        getString(R.string.sync_incomplete, result.errorMessage ?: getString(R.string.sync_pending_count, result.pending))
                    })
                }
            }
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.sync_config, menu)
        menu.applyTint(requireContext())
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
        R.id.menu_help -> { showHelp("syncHelp"); true }
        R.id.menu_log -> { showDialogFragment<AppLogDialog>(); true }
        else -> false
    }
}
