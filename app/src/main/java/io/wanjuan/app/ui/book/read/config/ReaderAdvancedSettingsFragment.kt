package io.wanjuan.app.ui.book.read.config

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.ViewConfiguration
import androidx.preference.Preference
import io.wanjuan.app.R
import io.wanjuan.app.constant.EventBus
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.config.ReadBookConfig
import io.wanjuan.app.model.ReadBook
import io.wanjuan.app.ui.book.read.ReadBookActivity
import io.wanjuan.app.ui.book.read.page.provider.ChapterProvider
import io.wanjuan.app.ui.widget.number.NumberPickerDialog
import io.wanjuan.app.utils.canvasrecorder.CanvasRecorderFactory
import io.wanjuan.app.utils.getPrefBoolean
import io.wanjuan.app.utils.postEvent
import io.wanjuan.app.utils.removePref
import io.wanjuan.app.utils.setEdgeEffectColor

class ReaderAdvancedSettingsFragment : ReaderPreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val slopSquare by lazy { ViewConfiguration.get(requireContext()).scaledTouchSlop }

    @SuppressLint("RestrictedApi")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_config_read)
        upPreferenceSummary(PreferKey.pageTouchSlop, slopSquare.toString())
        upPreferenceSummary(PreferKey.readMenuAlpha, AppConfig.readMenuAlpha.toString())
        if (!CanvasRecorderFactory.isSupport) {
            removePref(PreferKey.optimizeRender)
            preferenceScreen.removePreferenceRecursively(PreferKey.optimizeRender)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.background = null
        listView.clipToPadding = true
        listView.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        listView.setEdgeEffectColor(ReaderSheetStyle.resolve(requireContext()).accentColor)
    }

    override fun onResume() {
        super.onResume()
        preferenceManager
            .sharedPreferences
            ?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        preferenceManager
            .sharedPreferences
            ?.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?
    ) {
        when (key) {
            PreferKey.readBodyToLh -> activity?.recreate()
            PreferKey.hideStatusBar -> {
                ReadBookConfig.hideStatusBar = getPrefBoolean(PreferKey.hideStatusBar)
                postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
            }

            PreferKey.hideNavigationBar -> {
                ReadBookConfig.hideNavigationBar = getPrefBoolean(PreferKey.hideNavigationBar)
                postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
            }

            PreferKey.keepLight -> postEvent(key, true)
            PreferKey.textSelectAble -> postEvent(key, getPrefBoolean(key))
            PreferKey.screenOrientation -> {
                (activity as? ReadBookActivity)?.setOrientation()
            }

            PreferKey.textFullJustify,
            PreferKey.textBottomJustify,
            PreferKey.useZhLayout,
            PreferKey.adaptSpecialStyle-> {
                postEvent(EventBus.UP_CONFIG, arrayListOf(5))
            }

            PreferKey.showBrightnessView -> {
                postEvent(PreferKey.showBrightnessView, "")
            }

            PreferKey.expandTextMenu -> {
                (activity as? ReadBookActivity)?.textActionMenu?.upMenu()
            }
            PreferKey.contentSelectActions,
            PreferKey.contentSelectDefaultOpen -> {
                (activity as? ReadBookActivity)?.textActionMenu?.upMenu()
            }

            PreferKey.doublePageHorizontal -> {
                ChapterProvider.upLayout()
                ReadBook.loadContent(false)
            }

            PreferKey.showReadTitleAddition,
            PreferKey.readBarStyleFollowPage,
            PreferKey.readMenuAlpha -> {
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            PreferKey.progressBarBehavior -> {
                postEvent(EventBus.UP_SEEK_BAR, true)
            }

            PreferKey.noAnimScrollPage -> {
                ReadBook.callBack?.upPageAnim()
            }

            PreferKey.optimizeRender -> {
                ChapterProvider.upStyle()
                ReadBook.callBack?.upPageAnim(true)
                ReadBook.loadContent(false)
            }

            PreferKey.paddingDisplayCutouts -> {
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "customPageKey" -> PageKeyDialog(requireContext()).show()
            "clickRegionalConfig" -> {
                (activity as? ReadBookActivity)?.showClickRegionalConfig()
            }
            PreferKey.contentSelectMenuConfig -> {
                ContentSelectMenuConfigDialog().show(parentFragmentManager, "contentSelectMenuConfig")
            }
            PreferKey.pageTouchSlop -> {
                NumberPickerDialog(requireContext(), styleDialog = ReaderUiStyle::styleDialog)
                    .setTitle(getString(R.string.page_touch_slop_dialog_title))
                    .setMaxValue(9999)
                    .setMinValue(0)
                    .setValue(AppConfig.pageTouchSlop)
                    .show {
                        AppConfig.pageTouchSlop = it
                        postEvent(EventBus.UP_CONFIG, arrayListOf(4))
                    }
            }

            PreferKey.pageTouchClick -> {
                NumberPickerDialog(requireContext(), styleDialog = ReaderUiStyle::styleDialog)
                    .setTitle(getString(R.string.page_touch_click_dialog_title))
                    .setMaxValue(399)
                    .setMinValue(0)
                    .setValue(AppConfig.pageTouchClick)
                    .show {
                        AppConfig.pageTouchClick = it
                        postEvent(EventBus.UP_CONFIG, arrayListOf(12))
                    }
            }

            PreferKey.readMenuAlpha -> {
                NumberPickerDialog(requireContext(), styleDialog = ReaderUiStyle::styleDialog)
                    .setTitle(getString(R.string.read_menu_alpha))
                    .setMaxValue(100)
                    .setMinValue(35)
                    .setValue(AppConfig.readMenuAlpha)
                    .setCustomButton(R.string.btn_default_s) {
                        AppConfig.readMenuAlpha = 100
                        upPreferenceSummary(PreferKey.readMenuAlpha, AppConfig.readMenuAlpha.toString())
                        postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                    }
                    .show {
                        AppConfig.readMenuAlpha = it.coerceIn(35, 100)
                        upPreferenceSummary(PreferKey.readMenuAlpha, AppConfig.readMenuAlpha.toString())
                        postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                    }
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    @Suppress("SameParameterValue")
    private fun upPreferenceSummary(preferenceKey: String, value: String?) {
        val preference = findPreference<Preference>(preferenceKey) ?: return
        when (preferenceKey) {
            PreferKey.pageTouchSlop -> preference.summary =
                getString(R.string.page_touch_slop_summary, value)
            PreferKey.readMenuAlpha -> preference.summary =
                getString(R.string.ui_layout_alpha_value, AppConfig.readMenuAlpha)
        }
    }

}
