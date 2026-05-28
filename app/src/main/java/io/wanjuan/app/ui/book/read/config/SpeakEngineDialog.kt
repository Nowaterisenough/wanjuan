package io.wanjuan.app.ui.book.read.config

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.wanjuan.app.R
import io.wanjuan.app.base.BaseDialogFragment
import io.wanjuan.app.base.adapter.ItemViewHolder
import io.wanjuan.app.base.adapter.RecyclerAdapter
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.HttpTTS
import io.wanjuan.app.databinding.DialogEditTextBinding
import io.wanjuan.app.databinding.DialogRecyclerViewBinding
import io.wanjuan.app.databinding.ItemHttpTtsBinding
import io.wanjuan.app.help.DirectLinkUpload
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.lib.dialogs.SelectItem
import io.wanjuan.app.lib.dialogs.alert
import io.wanjuan.app.lib.theme.primaryColor
import io.wanjuan.app.model.ReadAloud
import io.wanjuan.app.model.ReadBook
import io.wanjuan.app.ui.association.ImportHttpTtsDialog
import io.wanjuan.app.ui.file.HandleFileContract
import io.wanjuan.app.ui.login.SourceLoginActivity
import io.wanjuan.app.utils.ACache
import io.wanjuan.app.utils.FileUtils
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.applyTint
import io.wanjuan.app.utils.fromJsonObject
import io.wanjuan.app.utils.gone
import io.wanjuan.app.utils.isAbsUrl
import io.wanjuan.app.utils.isJsonObject
import io.wanjuan.app.utils.sendToClip
import io.wanjuan.app.utils.setEdgeEffectColor
import io.wanjuan.app.utils.setLayout
import io.wanjuan.app.utils.showDialogFragment
import io.wanjuan.app.utils.splitNotBlank
import io.wanjuan.app.utils.startActivity
import io.wanjuan.app.utils.toastOnUi
import io.wanjuan.app.utils.viewbindingdelegate.viewBinding
import io.wanjuan.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File

/**
 * tts引擎管理
 */
class SpeakEngineDialog() : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val viewModel: SpeakEngineViewModel by viewModels()
    private val ttsUrlKey = "ttsUrlKey"
    private val adapter by lazy { Adapter(requireContext()) }
    private var ttsEngine: String? = ReadAloud.ttsEngine
    private val sysTtsViews = arrayListOf<RadioButton>()
    private val callBack: CallBack? get() = parentFragment as? CallBack
    private var currentSelect = -1
    private val importDocResult = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            showDialogFragment(ImportHttpTtsDialog(uri.toString()))
        }
    }
    private val exportDirResult = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            alert(R.string.export_success) {
                if (uri.toString().isAbsUrl()) {
                    setMessage(DirectLinkUpload.getSummary())
                }
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.hint = getString(R.string.path)
                    editView.setText(uri.toString())
                }
                customView { alertBinding.root }
                okButton {
                    requireContext().sendToClip(uri.toString())
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        initMenu()
        initData()
    }

    private fun initView() = binding.run {
        toolBar.setBackgroundColor(primaryColor)
        toolBar.setTitle(R.string.speak_engine)
        recyclerView.setEdgeEffectColor(primaryColor)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        adapter.addHeaderView {
            ItemHttpTtsBinding.inflate(layoutInflater, recyclerView, false).apply {
                sysTtsViews.add(cbName)
                ivEdit.gone()
                ivMenuDelete.gone()
                labelSys.visible()
                cbName.text = "系统默认"
                cbName.tag = ""
                cbName.isChecked = ttsEngine == null || ttsEngine!!.isJsonObject()
                        && GSON.fromJsonObject<SelectItem<String>>(ttsEngine)
                    .getOrNull()?.value.isNullOrEmpty()
                cbName.setOnClickListener {
                    upTts(GSON.toJson(SelectItem("系统默认", "")))
                }
            }
        }
        viewModel.sysEngines.forEach { engine ->
            adapter.addHeaderView {
                ItemHttpTtsBinding.inflate(layoutInflater, recyclerView, false).apply {
                    sysTtsViews.add(cbName)
                    ivEdit.gone()
                    ivMenuDelete.gone()
                    labelSys.visible()
                    cbName.text = engine.label
                    cbName.tag = engine.name
                    cbName.isChecked = GSON.fromJsonObject<SelectItem<String>>(ttsEngine)
                        .getOrNull()?.value == cbName.tag
                    cbName.setOnClickListener {
                        upTts(GSON.toJson(SelectItem(engine.label, engine.name)))
                    }
                }
            }
        }
        tvFooterLeft.setText(R.string.book)
        tvFooterLeft.visible()
        tvFooterLeft.setOnClickListener {
            ReadBook.book?.setTtsEngine(ttsEngine)
            callBack?.upSpeakEngineSummary()
            ReadAloud.upReadAloudClass()
            dismissAllowingStateLoss()
        }
        tvOk.setText(R.string.general)
        tvOk.visible()
        tvOk.setOnClickListener {
            ReadBook.book?.setTtsEngine(null)
            AppConfig.ttsEngine = ttsEngine
            callBack?.upSpeakEngineSummary()
            ReadAloud.upReadAloudClass()
            dismissAllowingStateLoss()
        }
        tvCancel.visible()
        tvCancel.setOnClickListener {
            dismissAllowingStateLoss()
        }
    }

    private fun initMenu() = binding.run {
        toolBar.inflateMenu(R.menu.speak_engine)
        toolBar.menu.applyTint(requireContext())
        toolBar.setOnMenuItemClickListener(this@SpeakEngineDialog)
    }

    private fun initData() {
        lifecycleScope.launch {
            appDb.httpTTSDao.flowAll().catch {
                AppLog.put("朗读引擎界面获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect {
                adapter.setItems(it)
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_clear -> clearCache()
            R.id.menu_add -> showDialogFragment<HttpTtsEditDialog>()
            R.id.menu_default -> viewModel.importDefault()
            R.id.menu_import_local -> importDocResult.launch {
                mode = HandleFileContract.FILE
                allowExtensions = arrayOf("txt", "json")
            }

            R.id.menu_import_onLine -> importAlert()
            R.id.menu_export_all -> exportDirResult.launch {
                mode = HandleFileContract.EXPORT
                fileData = HandleFileContract.FileData(
                    "httpTts.json",
                    GSON.toJson(adapter.getItems()).toByteArray(),
                    "application/json"
                )
            }
            R.id.menu_export -> {
                if (currentSelect == -1) {
                    toastOnUi(R.string.is_system_tts_no_export)
                    return true
                }
                val tts = adapter.getItem(currentSelect) ?: return true
                exportDirResult.launch {
                    mode = HandleFileContract.EXPORT
                    fileData = HandleFileContract.FileData(
                        "httpTts_${tts.name}.json",
                        GSON.toJson(tts).toByteArray(),
                        "application/json"
                    )
                }
            }
        }
        return true
    }

    fun clearCache() {
        execute {
            ReadAloud.upReadAloudClass()
            val ttsFolderPath = "${requireContext().cacheDir.absolutePath}${File.separator}httpTTS${File.separator}"
            FileUtils.listDirsAndFiles(ttsFolderPath)?.forEach {
                FileUtils.delete(it.absolutePath)
            }
            toastOnUi(R.string.clear_cache_success)
        }
    }

    private fun importAlert() {
        val aCache = ACache.get(cacheDir = false)
        val cacheUrls: MutableList<String> = aCache
            .getAsString(ttsUrlKey)
            ?.splitNotBlank(",")
            ?.toMutableList() ?: mutableListOf()
        alert(R.string.import_on_line) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url"
                editView.setFilterValues(cacheUrls)
                editView.delCallBack = {
                    cacheUrls.remove(it)
                    aCache.put(ttsUrlKey, cacheUrls.joinToString(","))
                }
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let { url ->
                    if (url.isAbsUrl() && !cacheUrls.contains(url)) {
                        cacheUrls.add(0, url)
                        aCache.put(ttsUrlKey, cacheUrls.joinToString(","))
                    }
                    showDialogFragment(ImportHttpTtsDialog(url))
                }
            }
        }
    }

    private fun upTts(tts: String) {
        ttsEngine = tts
        sysTtsViews.forEach {
            val isChecked = GSON.fromJsonObject<SelectItem<String>>(ttsEngine)
                .getOrNull()?.value == it.tag
            if (isChecked) {
                currentSelect = -1
            }
            it.isChecked = isChecked
        }
        adapter.notifyItemRangeChanged(adapter.getHeaderCount(), adapter.itemCount)
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<HttpTTS, ItemHttpTtsBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemHttpTtsBinding {
            return ItemHttpTtsBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemHttpTtsBinding,
            item: HttpTTS,
            payloads: MutableList<Any>
        ) {
            binding.apply {
                cbName.text = item.name
                val isChecked = item.id.toString() == ttsEngine
                if (isChecked) {
                    currentSelect = holder.layoutPosition - getHeaderCount()
                }
                cbName.isChecked = isChecked
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemHttpTtsBinding) {
            binding.run {
                cbName.setOnClickListener {
                    getItemByLayoutPosition(holder.layoutPosition)?.let { httpTTS ->
                        val id = httpTTS.id.toString()
                        upTts(id)
                        if (!httpTTS.loginUrl.isNullOrBlank()
                            && httpTTS.getLoginInfo().isNullOrBlank()
                        ) {
                            startActivity<SourceLoginActivity> {
                                putExtra("type", "httpTts")
                                putExtra("key", id)
                            }
                        }
                    }
                }
                cbName.setOnLongClickListener {
                    getItemByLayoutPosition(holder.layoutPosition)?.let { httpTTS ->
                        if (!httpTTS.loginUrl.isNullOrBlank()) {
                            val id = httpTTS.id.toString()
                            startActivity<SourceLoginActivity> {
                                putExtra("type", "httpTts")
                                putExtra("key", id)
                            }
                            return@setOnLongClickListener true
                        }
                    }
                    false
                }
                ivEdit.setOnClickListener {
                    val id = getItemByLayoutPosition(holder.layoutPosition)!!.id
                    showDialogFragment(HttpTtsEditDialog(id))
                }
                ivMenuDelete.setOnClickListener {
                    getItemByLayoutPosition(holder.layoutPosition)?.let { httpTTS ->
                        alert(R.string.draw) {
                            setMessage(getString(R.string.sure_del) + "\n" + httpTTS.name)
                            noButton()
                            yesButton {
                                appDb.httpTTSDao.delete(httpTTS)
                            }
                        }
                    }
                }
            }
        }

    }

    interface CallBack {
        fun upSpeakEngineSummary()
    }

}