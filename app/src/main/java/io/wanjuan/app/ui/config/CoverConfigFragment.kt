package io.wanjuan.app.ui.config

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import io.wanjuan.app.R
import io.wanjuan.app.constant.EventBus
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.help.config.CoverCollectionManager
import io.wanjuan.app.lib.dialogs.selector
import io.wanjuan.app.lib.prefs.SwitchPreference
import io.wanjuan.app.lib.prefs.fragment.PreferenceFragment
import io.wanjuan.app.lib.theme.primaryColor
import io.wanjuan.app.model.BookCover
import io.wanjuan.app.ui.file.HandleFileContract
import io.wanjuan.app.utils.FileUtils
import io.wanjuan.app.utils.MD5Utils
import io.wanjuan.app.utils.externalFiles
import io.wanjuan.app.utils.getPrefBoolean
import io.wanjuan.app.utils.getPrefString
import io.wanjuan.app.utils.inputStream
import io.wanjuan.app.utils.postEvent
import io.wanjuan.app.utils.putPrefString
import io.wanjuan.app.utils.readUri
import io.wanjuan.app.utils.removePref
import io.wanjuan.app.utils.setEdgeEffectColor
import io.wanjuan.app.utils.showDialogFragment
import io.wanjuan.app.utils.startActivity
import io.wanjuan.app.utils.toastOnUi
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.io.FileOutputStream
import java.util.UUID

class CoverConfigFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val requestCodeCover = 111
    private val requestCodeCoverDark = 112
    private val selectImage = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            when (it.requestCode) {
                requestCodeCover -> setCoverFromUri(PreferKey.defaultCover, uri)
                requestCodeCoverDark -> setCoverFromUri(PreferKey.defaultCoverDark, uri)
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_config_cover)
        upPreferenceSummary(PreferKey.defaultCover, getPrefString(PreferKey.defaultCover))
        upPreferenceSummary(PreferKey.defaultCoverDark, getPrefString(PreferKey.defaultCoverDark))
        upPreferenceSummary(PreferKey.coverCollectionDay, getPrefString(PreferKey.coverCollectionDay))
        upPreferenceSummary(PreferKey.coverCollectionNight, getPrefString(PreferKey.coverCollectionNight))
        findPreference<SwitchPreference>(PreferKey.coverShowAuthor)
            ?.isEnabled = getPrefBoolean(PreferKey.coverShowName)
        findPreference<SwitchPreference>(PreferKey.coverShowAuthorN)
            ?.isEnabled = getPrefBoolean(PreferKey.coverShowNameN)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.cover_config)
        listView.setEdgeEffectColor(primaryColor)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        sharedPreferences ?: return
        when (key) {
            PreferKey.defaultCover,
            PreferKey.defaultCoverDark,
            PreferKey.coverCollectionDay,
            PreferKey.coverCollectionNight -> {
                upPreferenceSummary(key, getPrefString(key))
            }

            PreferKey.coverShowName -> {
                findPreference<SwitchPreference>(PreferKey.coverShowAuthor)
                    ?.isEnabled = getPrefBoolean(key)
                BookCover.upDefaultCover()
            }

            PreferKey.coverShowNameN -> {
                findPreference<SwitchPreference>(PreferKey.coverShowAuthorN)
                    ?.isEnabled = getPrefBoolean(key)
                BookCover.upDefaultCover()
            }

            PreferKey.coverShowAuthor,
            PreferKey.coverShowAuthorN,
            PreferKey.coverCollectionModeDay,
            PreferKey.coverCollectionModeNight -> {
                refreshCoverCollection()
            }
        }
    }

    @SuppressLint("PrivateResource")
    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "coverRule" -> showDialogFragment(CoverRuleConfigDialog())
            "coverCollectionManage" -> startActivity<CoverCollectionManageActivity>()
            PreferKey.coverCollectionDay -> selectCoverCollection(false)
            PreferKey.coverCollectionNight -> selectCoverCollection(true)
            PreferKey.defaultCover ->
                if (getPrefString(preference.key).isNullOrEmpty()) {
                    selectImage.launch {
                        requestCode = requestCodeCover
                        mode = HandleFileContract.IMAGE
                    }
                } else {
                    context?.selector(
                        items = arrayListOf(
                            getString(R.string.delete),
                            getString(R.string.select_image)
                        )
                    ) { _, i ->
                        if (i == 0) {
                            removePref(preference.key)
                            BookCover.upDefaultCover()
                        } else {
                            selectImage.launch {
                                requestCode = requestCodeCover
                                mode = HandleFileContract.IMAGE
                            }
                        }
                    }
                }

            PreferKey.defaultCoverDark ->
                if (getPrefString(preference.key).isNullOrEmpty()) {
                    selectImage.launch {
                        requestCode = requestCodeCoverDark
                        mode = HandleFileContract.IMAGE
                    }
                } else {
                    context?.selector(
                        items = arrayListOf(
                            getString(R.string.delete),
                            getString(R.string.select_image)
                        )
                    ) { _, i ->
                        if (i == 0) {
                            removePref(preference.key)
                            BookCover.upDefaultCover()
                        } else {
                            selectImage.launch {
                                requestCode = requestCodeCoverDark
                                mode = HandleFileContract.IMAGE
                            }
                        }
                    }
                }
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun upPreferenceSummary(preferenceKey: String, value: String?) {
        val preference = findPreference<Preference>(preferenceKey) ?: return
        when (preferenceKey) {
            PreferKey.defaultCover,
            PreferKey.defaultCoverDark -> preference.summary = if (value.isNullOrBlank()) {
                getString(R.string.select_image)
            } else {
                value
            }

            PreferKey.coverCollectionDay,
            PreferKey.coverCollectionNight -> {
                val isNight = preferenceKey == PreferKey.coverCollectionNight
                lifecycleScope.launch {
                    val collection = CoverCollectionManager.get(isNight, value)
                    preference.summary = collection?.name ?: getString(R.string.cover_collection_none)
                }
            }

            else -> preference.summary = value
        }
    }

    private fun selectCoverCollection(isNight: Boolean) {
        lifecycleScope.launch {
            val collections = CoverCollectionManager.load(isNight)
            val items = arrayListOf(getString(R.string.cover_collection_none))
            items.addAll(collections.map { "${it.name} (${it.images.size})" })
            context?.selector(items = items) { _, index ->
                val selected = if (index <= 0) null else collections.getOrNull(index - 1)
                CoverCollectionManager.setSelected(isNight, selected?.id)
                upPreferenceSummary(
                    if (isNight) PreferKey.coverCollectionNight else PreferKey.coverCollectionDay,
                    selected?.id
                )
                refreshCoverCollection()
            }
        }
    }

    private fun refreshCoverCollection() {
        BookCover.upDefaultCover()
        postEvent(EventBus.BOOKSHELF_REFRESH, UUID.randomUUID().toString())
        postEvent(EventBus.REFRESH_BOOK_INFO, false)
    }

    private fun setCoverFromUri(preferenceKey: String, uri: Uri) {
        readUri(uri) { fileDoc, inputStream ->
            kotlin.runCatching {
                var file = requireContext().externalFiles
                val suffix = if (fileDoc.name.contains(".9.png", true)) {
                    ".9.png"
                } else {
                    "." + fileDoc.name.substringAfterLast(".")
                }
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + suffix
                }
                file = FileUtils.createFileIfNotExist(file, "covers", fileName)
                FileOutputStream(file).use {
                    inputStream.copyTo(it)
                }
                putPrefString(preferenceKey, file.absolutePath)
                BookCover.upDefaultCover()
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }

}
