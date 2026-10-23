package io.wanjuan.app.ui.config

import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import io.wanjuan.app.R
import io.wanjuan.app.constant.EventBus
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.databinding.DialogAiProviderEditBinding
import io.wanjuan.app.databinding.DialogEditTextBinding
import io.wanjuan.app.help.ai.AiChatService
import io.wanjuan.app.help.ai.AiToolRegistry
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.lib.dialogs.alert
import io.wanjuan.app.lib.dialogs.selector
import io.wanjuan.app.lib.prefs.SwitchPreference
import io.wanjuan.app.lib.prefs.fragment.PreferenceFragment
import io.wanjuan.app.lib.theme.primaryColor
import io.wanjuan.app.ui.main.ai.AiModelConfig
import io.wanjuan.app.ui.main.ai.AiProviderConfig
import io.wanjuan.app.utils.postEvent
import io.wanjuan.app.utils.setEdgeEffectColor
import io.wanjuan.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiConfigFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_config_ai)
        refreshUi()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.ai_setting)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        listView.setEdgeEffectColor(primaryColor)
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "aiAddProvider" -> showEditProviderDialog()
            "aiManageProviders" -> showManageProvidersDialog()
            "aiAddModel" -> showAddModelOptionsDialog()
            "aiFetchModels" -> fetchModelsFromCurrentProvider(showSelector = true)
            "aiManageModels" -> showManageModelsDialog()
            "aiManageNativeTools" -> showManageNativeToolsDialog()
            PreferKey.aiSystemPrompt -> showSystemPromptDialog()
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PreferKey.aiAssistantEnabled) {
            refreshUi(notifyMain = true)
        }
    }

    private fun showEditProviderDialog(provider: AiProviderConfig? = null) {
        val binding = DialogAiProviderEditBinding.inflate(layoutInflater).apply {
            editProviderName.setText(provider?.name.orEmpty())
            editProviderBaseUrl.setText(provider?.baseUrl ?: "https://api.openai.com/v1")
            editProviderApiKey.setText(provider?.apiKey.orEmpty())
            editProviderHeaders.setText(provider?.headers.orEmpty())
        }
        alert(
            title = getString(
                if (provider == null) R.string.ai_add_provider else R.string.ai_edit_provider
            )
        ) {
            customView { binding.root }
            okButton {
                val name = binding.editProviderName.text?.toString()?.trim().orEmpty()
                val baseUrl = binding.editProviderBaseUrl.text?.toString()?.trim().orEmpty()
                val apiKey = binding.editProviderApiKey.text?.toString()?.trim().orEmpty()
                val headers = binding.editProviderHeaders.text?.toString()?.trim().orEmpty()
                when {
                    name.isEmpty() -> {
                        toastOnUi(R.string.ai_provider_name_required)
                        return@okButton
                    }

                    baseUrl.isEmpty() -> {
                        toastOnUi(R.string.ai_provider_url_required)
                        return@okButton
                    }
                }
                val providers = AppConfig.aiProviderList.toMutableList()
                val updated = provider?.copy(
                    name = name,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    headers = headers
                ) ?: AiProviderConfig(
                    name = name,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    headers = headers
                )
                val targetIndex = providers.indexOfFirst { it.id == updated.id }
                if (targetIndex >= 0) {
                    providers[targetIndex] = updated
                } else {
                    providers.add(updated)
                }
                AppConfig.aiProviderList = providers
                AppConfig.aiCurrentProviderId = updated.id
                refreshUi()
                toastOnUi(R.string.ai_provider_saved)
            }
            cancelButton()
        }
    }

    private fun showManageProvidersDialog() {
        val providers = AppConfig.aiProviderList
        if (providers.isEmpty()) {
            toastOnUi(R.string.ai_no_providers)
            return
        }
        context?.selector(
            getString(R.string.ai_manage_providers),
            providers.map { it.name }
        ) { _, _, index ->
            val provider = providers[index]
            context?.selector(
                provider.name,
                arrayListOf(
                    getString(R.string.ai_set_current_provider),
                    getString(R.string.ai_edit_provider),
                    getString(R.string.ai_remove_provider)
                )
            ) { _, action ->
                when (action) {
                    0 -> {
                        AppConfig.aiCurrentProviderId = provider.id
                        refreshUi()
                    }

                    1 -> showEditProviderDialog(provider)
                    2 -> confirmRemoveProvider(provider)
                }
            }
        }
    }

    private fun confirmRemoveProvider(provider: AiProviderConfig) {
        val relatedModelCount = AppConfig.aiModelConfigList.count { it.providerId == provider.id }
        alert(
            title = provider.name,
            message = getString(
                if (relatedModelCount > 0) {
                    R.string.ai_remove_provider_confirm_with_models
                } else {
                    R.string.ai_remove_provider_confirm
                },
                relatedModelCount
            )
        ) {
            okButton {
                AppConfig.aiProviderList = AppConfig.aiProviderList.filterNot { it.id == provider.id }
                refreshUi()
                toastOnUi(R.string.ai_provider_removed)
            }
            cancelButton()
        }
    }

    private fun showEditModelDialog(model: AiModelConfig? = null) {
        val provider = AppConfig.aiCurrentProvider
        if (provider == null) {
            toastOnUi(R.string.ai_no_providers)
            return
        }
        val binding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = getString(R.string.ai_model_input_hint)
            editView.inputType = InputType.TYPE_CLASS_TEXT
            editView.setText(model?.modelId.orEmpty())
            editView.setSelection(editView.text?.length ?: 0)
        }
        alert(
            title = getString(
                if (model == null) R.string.ai_add_model else R.string.ai_edit_model
            )
        ) {
            customView { binding.root }
            okButton {
                val modelId = binding.editView.text?.toString()?.trim().orEmpty()
                if (modelId.isEmpty()) {
                    return@okButton
                }
                val models = AppConfig.aiModelConfigList.toMutableList()
                val exists = models.any {
                    it.providerId == provider.id && it.modelId == modelId && it.id != model?.id
                }
                if (exists) {
                    toastOnUi(R.string.ai_model_exists)
                    return@okButton
                }
                val updated = model?.copy(
                    providerId = provider.id,
                    modelId = modelId
                ) ?: AiModelConfig(
                    providerId = provider.id,
                    modelId = modelId
                )
                val targetIndex = models.indexOfFirst { it.id == updated.id }
                if (targetIndex >= 0) {
                    models[targetIndex] = updated
                } else {
                    models.add(updated)
                }
                AppConfig.aiModelConfigList = models
                AppConfig.aiCurrentModelId = updated.id
                refreshUi()
                toastOnUi(
                    if (model == null) R.string.ai_model_added else R.string.ai_model_saved
                )
            }
            cancelButton()
        }
    }

    private fun showAddModelOptionsDialog() {
        if (AppConfig.aiCurrentProvider == null) {
            toastOnUi(R.string.ai_no_providers)
            return
        }
        context?.selector(
            getString(R.string.ai_add_model),
            listOf(
                getString(R.string.ai_add_model_from_list),
                getString(R.string.ai_add_model_manual)
            )
        ) { _, _, index ->
            when (index) {
                0 -> fetchModelsFromCurrentProvider(showSelector = true)
                1 -> showEditModelDialog()
            }
        }
    }

    private fun showManageModelsDialog() {
        if (AppConfig.aiCurrentProvider == null) {
            toastOnUi(R.string.ai_no_providers)
            return
        }
        val models = currentProviderModels()
        if (models.isEmpty()) {
            toastOnUi(R.string.ai_no_models)
            return
        }
        context?.selector(
            getString(R.string.ai_manage_models),
            models.map { it.modelId }
        ) { _, _, index ->
            val model = models[index]
            context?.selector(
                model.modelId,
                arrayListOf(
                    getString(R.string.ai_set_current),
                    getString(R.string.ai_edit_model),
                    getString(R.string.ai_remove_model)
                )
            ) { _, action ->
                when (action) {
                    0 -> {
                        AppConfig.aiCurrentModelId = model.id
                        refreshUi()
                    }

                    1 -> showEditModelDialog(model)
                    2 -> confirmRemoveModel(model)
                }
            }
        }
    }

    private fun fetchModelsFromCurrentProvider(showSelector: Boolean = false) {
        val provider = AppConfig.aiCurrentProvider
        if (provider == null) {
            toastOnUi(R.string.ai_no_providers)
            return
        }
        toastOnUi(R.string.ai_fetch_models_loading)
        lifecycleScope.launch {
            val result = withContext(IO) {
                runCatching { AiChatService.fetchModels(provider) }
            }
            result.onSuccess { modelIds ->
                if (modelIds.isEmpty()) {
                    toastOnUi(R.string.ai_fetch_models_empty)
                    return@onSuccess
                }
                if (showSelector) {
                    showFetchedModelSelector(provider.id, modelIds)
                } else {
                    appendFetchedModels(provider.id, modelIds)
                }
            }.onFailure {
                toastOnUi(getString(R.string.ai_fetch_models_failed, it.localizedMessage ?: "Error"))
            }
        }
    }

    private fun showFetchedModelSelector(providerId: String, modelIds: List<String>) {
        val items = buildList {
            add(getString(R.string.ai_add_all_models))
            addAll(modelIds)
        }
        context?.selector(
            getString(R.string.ai_add_model_from_list),
            items
        ) { _, _, index ->
            if (index == 0) {
                appendFetchedModels(providerId, modelIds)
            } else {
                val selectedModelId = items[index]
                val existing = AppConfig.aiModelConfigList.firstOrNull {
                    it.providerId == providerId && it.modelId == selectedModelId
                }
                if (existing != null) {
                    AppConfig.aiCurrentModelId = existing.id
                    refreshUi()
                    toastOnUi(R.string.ai_model_saved)
                } else {
                    appendFetchedModels(providerId, listOf(selectedModelId))
                }
            }
        }
    }

    private fun appendFetchedModels(providerId: String, modelIds: List<String>) {
        val oldModels = AppConfig.aiModelConfigList
        val existingIds = oldModels
            .filter { it.providerId == providerId }
            .map { it.modelId }
            .toSet()
        val newModels = modelIds
            .distinct()
            .filterNot { it in existingIds }
            .map { AiModelConfig(providerId = providerId, modelId = it) }
        if (newModels.isEmpty()) {
            toastOnUi(R.string.ai_fetch_models_no_new)
            return
        }
        AppConfig.aiModelConfigList = oldModels + newModels
        if (AppConfig.aiCurrentProviderId == providerId && AppConfig.aiCurrentModelId.isNullOrBlank()) {
            AppConfig.aiCurrentModelId = newModels.first().id
        }
        refreshUi()
        toastOnUi(getString(R.string.ai_fetch_models_success, newModels.size))
    }

    private fun confirmRemoveModel(model: AiModelConfig) {
        alert(
            title = model.modelId,
            message = getString(R.string.ai_remove_model_confirm)
        ) {
            okButton {
                AppConfig.aiModelConfigList =
                    AppConfig.aiModelConfigList.filterNot { it.id == model.id }
                refreshUi()
                toastOnUi(R.string.ai_model_removed)
            }
            cancelButton()
        }
    }

    private fun currentProviderModels(): List<AiModelConfig> {
        val providerId = AppConfig.aiCurrentProviderId ?: return emptyList()
        return AppConfig.aiModelConfigList.filter { it.providerId == providerId }
    }

    private fun showSystemPromptDialog() {
        val binding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = getString(R.string.ai_system_prompt_hint)
            editView.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            editView.minLines = 8
            editView.setText(AppConfig.aiSystemPrompt)
            editView.setSelection(editView.text?.length ?: 0)
        }
        alert(titleResource = R.string.ai_system_prompt) {
            customView { binding.root }
            okButton {
                AppConfig.aiSystemPrompt = binding.editView.text?.toString().orEmpty()
                refreshUi()
            }
            neutralButton(R.string.restore_default) {
                AppConfig.aiSystemPrompt = AppConfig.DEFAULT_AI_SYSTEM_PROMPT
                refreshUi()
            }
            cancelButton()
        }
    }

    private fun showManageNativeToolsDialog() {
        lifecycleScope.launch {
            val tools = runCatching { AiToolRegistry.resolveAllToolNamesForManage() }
                .getOrDefault(emptyList())
            if (tools.isEmpty()) {
                toastOnUi(R.string.not_available)
                return@launch
            }
            val enabled = AppConfig.aiEnabledToolNames.toMutableSet()
            val checked = BooleanArray(tools.size) {
                val name = tools[it]
                if (enabled.isEmpty()) name in AiToolRegistry.defaultEnabledTools else name in enabled
            }
            val labels = tools.map(::toolDisplayName).toTypedArray()
            alert(getString(R.string.ai_manage_native_tools)) {
                multiChoiceItems(labels, checked) { _, which, isChecked ->
                    if (isChecked) enabled.add(tools[which]) else enabled.remove(tools[which])
                }
                okButton {
                    AppConfig.aiEnabledToolNames = enabled
                    refreshUi()
                }
                negativeButton(R.string.select_all) {
                    AppConfig.aiEnabledToolNames = tools.toSet()
                    refreshUi()
                }
                neutralButton(R.string.restore_default) {
                    AppConfig.aiEnabledToolNames = emptySet()
                    refreshUi()
                }
                cancelButton()
            }
        }
    }

    private fun toolDisplayName(name: String): String {
        val group = AiToolRegistry.groupLabelOfTool(name)
        return "[${toolGroupZh(group)}] ${toolNameZh(name)}"
    }

    private fun toolGroupZh(group: String): String {
        return when (group) {
            "MCP" -> "MCP"
            "书架" -> "书架"
            "书源" -> "书源"
            "阅读" -> "阅读"
            "联网搜索" -> "联网搜索"
            "设置" -> "设置"
            else -> "其他"
        }
    }

    private fun toolNameZh(name: String): String {
        return when (name) {
            "query_bookshelf" -> "查询书架书籍"
            "get_bookshelf_book_info" -> "获取书籍详情"
            "manage_bookshelf_group" -> "管理书架分组"
            "manage_bookshelf_tag" -> "管理书架标签"
            "set_bookshelf_book_group" -> "设置书籍分组"
            "set_bookshelf_book_tags" -> "设置书籍标签"
            "query_read_records" -> "查询阅读记录"
            "list_book_chapters" -> "获取章节列表"
            "read_book_chapter_content" -> "读取章节正文"
            "list_book_sources" -> "列出书源"
            "search_book_source" -> "搜索书源内容"
            "create_book_source" -> "新增书源"
            "get_book_source" -> "获取书源详情"
            "update_book_source" -> "更新书源"
            "fetch_source_html" -> "抓取网页源码"
            "debug_book_source" -> "调试书源规则"
            "get_app_settings" -> "读取设置项"
            "set_app_setting" -> "修改单个设置"
            "set_app_settings_batch" -> "批量修改设置"
            else -> name
        }
    }

    private fun refreshUi(notifyMain: Boolean = false) {
        val currentProvider = AppConfig.aiCurrentProvider
        val providerModels = currentProviderModels()
        val canEnable = AppConfig.aiCurrentModelConfig != null
        val storedEnabled = preferenceManager.sharedPreferences
            ?.getBoolean(PreferKey.aiAssistantEnabled, false) == true
        if (!canEnable && storedEnabled) {
            AppConfig.aiAssistantEnabled = false
        }
        findPreference<SwitchPreference>(PreferKey.aiAssistantEnabled)?.apply {
            isEnabled = canEnable
            isChecked = AppConfig.aiAssistantEnabled
            summary = getString(
                if (canEnable) R.string.ai_enable_summary else R.string.ai_enable_summary_disabled
            )
        }
        findPreference<Preference>("aiManageProviders")?.summary =
            if (AppConfig.aiProviderList.isEmpty()) {
                getString(R.string.ai_no_providers)
            } else {
                buildString {
                    append(currentProvider?.name ?: getString(R.string.ai_current_provider_summary_empty))
                    append(" · ")
                    append(getString(R.string.ai_manage_providers_summary, AppConfig.aiProviderList.size))
                }
            }
        findPreference<Preference>("aiManageModels")?.summary =
            if (providerModels.isEmpty()) {
                getString(
                    if (currentProvider == null) {
                        R.string.ai_current_model_summary_empty
                    } else {
                        R.string.ai_current_model_summary_no_provider_models
                    }
                )
            } else {
                buildString {
                    append(AppConfig.aiCurrentModelConfig?.modelId ?: providerModels.first().modelId)
                    append(" · ")
                    append(getString(R.string.ai_manage_models_summary, providerModels.size))
                }
            }
        findPreference<Preference>("aiAddModel")?.summary =
            getString(R.string.ai_add_model_summary_modern)
        findPreference<Preference>("aiFetchModels")?.summary =
            getString(R.string.ai_fetch_models_summary_modern)
        findPreference<Preference>(PreferKey.aiSystemPrompt)?.summary =
            getString(R.string.ai_system_prompt_summary)
        findPreference<Preference>("aiManageNativeTools")?.summary = run {
            val enabledTools = AppConfig.aiEnabledToolNames
            if (enabledTools.isEmpty()) {
                getString(R.string.ai_manage_native_tools_summary)
            } else {
                "${getString(R.string.ai_manage_native_tools_summary)} · ${enabledTools.size}"
            }
        }
        if (notifyMain || (!canEnable && storedEnabled)) {
            postEvent(EventBus.NOTIFY_MAIN, false)
        }
    }
}
