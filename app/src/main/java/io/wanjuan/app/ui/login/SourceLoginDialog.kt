package io.wanjuan.app.ui.login

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.setPadding
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.script.rhino.runScriptWithContext
import io.wanjuan.app.R
import io.wanjuan.app.base.BaseDialogFragment
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.data.entities.BaseSource
import io.wanjuan.app.data.entities.rule.RowUi
import io.wanjuan.app.databinding.DialogLoginBinding
import io.wanjuan.app.databinding.ItemSourceEditBinding
import io.wanjuan.app.lib.dialogs.alert
import io.wanjuan.app.lib.theme.applyUiToolbarTypeface
import io.wanjuan.app.lib.theme.primaryColor
import io.wanjuan.app.lib.theme.primaryTextColor
import io.wanjuan.app.ui.about.AppLogDialog
import io.wanjuan.app.ui.widget.RowUiForm
import io.wanjuan.app.ui.widget.RowUiViewFactory
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.applyTint
import io.wanjuan.app.utils.dpToPx
import io.wanjuan.app.utils.fromJsonArray
import io.wanjuan.app.utils.isAbsUrl
import io.wanjuan.app.utils.openUrl
import io.wanjuan.app.utils.printOnDebug
import io.wanjuan.app.utils.sendToClip
import io.wanjuan.app.utils.setLayout
import io.wanjuan.app.utils.showDialogFragment
import io.wanjuan.app.utils.toastOnUi
import io.wanjuan.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import kotlin.text.lastIndexOf
import kotlin.text.startsWith
import kotlin.text.substring
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSpinner
import io.wanjuan.app.data.entities.rule.RowUi.Type
import io.wanjuan.app.ui.widget.text.TextInputLayout
import io.wanjuan.app.utils.buildMainHandler
import io.wanjuan.app.utils.indexOf
import io.wanjuan.app.utils.setSelectionSafely

class SourceLoginDialog : BaseDialogFragment(R.layout.dialog_login, true),
    SourceLoginJsExtensions.Callback {

    private val binding by viewBinding(DialogLoginBinding::bind)
    private val viewModel by activityViewModels<SourceLoginViewModel>()
    private var lastClickTime: Long = 0
    private var oKToClose = false
    private var rowUis: List<RowUi>? = null
    private var rowUiName = arrayListOf<String>()
    private val rowUiActionRunnables = hashMapOf<String, Runnable>()
    private var hasChange = false
    private var loginUrl: String? = null
    private val sourceLoginJsExtensions by lazy {
        SourceLoginJsExtensions(
            activity as AppCompatActivity,
            viewModel.source,
            viewModel.bookType,
            this
        )
    }

    private var initHandler = false
    private val handler by lazy {
        initHandler = true
        buildMainHandler()
    }

    override fun upUiData(data: Map<String, Any?>?) {
        try {
            activity?.runOnUiThread { // 鍦ㄤ富绾跨▼涓洿鏂?UI
                handleUpUiData(data)
            }
        } catch (e: Exception) {
            AppLog.put("upLoginData Error: " + e.localizedMessage, e)
        }
    }

    override fun reUiView(deltaUp: Boolean) {
        activity?.runOnUiThread {
            handleReUiView(deltaUp)
        }
    }

    private fun handleReUiView(deltaUp: Boolean) {
        val source = viewModel.source ?: return
        val loginUiStr = source.loginUi ?: return
        val codeStr = loginUiStr.let {
            when {
                it.startsWith("@js:") -> it.substring(4)
                it.startsWith("<js>") -> it.substring(4, it.lastIndexOf("<"))
                else -> null
            }
        }
        if (codeStr != null) {
            hasChange = true
            lifecycleScope.launch(Main) {
                withContext(IO) {
                    val loginUiJson = evalUiJs(codeStr)
                    rowUis = loginUi(loginUiJson)
                }
                rowUiBuilderShared(source, rowUis, deltaUp)
            }
        } else {
            rowUis = loginUi(loginUiStr)
            rowUiBuilderShared(source, rowUis, deltaUp)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun handleUpUiData(data: Map<String, Any?>?) {
        hasChange = true
        if (data == null) {
            val newLoginInfo: MutableMap<String, String> = mutableMapOf()
            rowUis?.forEachIndexed { index, rowUi ->
                val default = rowUi.default
                when (val rowView = binding.root.findViewById<View>(index + 1000)) {
                    is TextInputLayout -> {
                        val value = default ?: ""
                        newLoginInfo[rowUi.name] = value
                        rowView.editText?.setText(value)
                    }

                    is TextView -> {
                        when (rowUi.type) {
                            Type.button -> {
                                rowView.text = rowUi.viewName ?: rowUi.name
                            }
                            Type.toggle -> {
                                val char = default ?: run{
                                    val chars = rowUi.chars?.filterNotNull() ?: listOf("chars is null")
                                    chars.getOrNull(0) ?: ""
                                }
                                newLoginInfo[rowUi.name] = char
                                val name =  rowUi.viewName ?: rowUi.name
                                val left = rowUi.style?.layout_justifySelf != "right"
                                rowView.text = if (left) char + name else name + char
                            }
                        }
                    }

                    is LinearLayout -> {
                        val chars = rowUi.chars?.filterNotNull() ?: listOf("chars","is null")
                        val index = chars.indexOf(default)
                        newLoginInfo[rowUi.name] = default ?: run{
                            chars.getOrNull(0) ?: ""
                        }
                        rowView.findViewById<AppCompatSpinner>(R.id.sp_type)?.setSelectionSafely(index)
                    }
                }
            }
            viewModel.loginInfo = newLoginInfo
            return
        }
        val loginInfo = viewModel.loginInfo
        data.forEach { (key, value) ->
            val value = value?.toString()
            val index = rowUiName.indexOf(key)
            if (index != -1) {
                val rowUi = rowUis?.getOrNull(index) ?: return@forEach
                val value = value ?: rowUi.default
                when (val rowView = binding.root.findViewById<View>(index + 1000)) {
                    is TextInputLayout -> {
                        val value = value ?: ""
                        loginInfo[rowUi.name] = value
                        rowView.editText?.setText(value)
                    }

                    is TextView -> {
                        when (rowUi.type) {
                            Type.button -> {
                                rowView.text = value ?: rowUi.viewName ?: key
                            }

                            Type.toggle -> {
                                val char = value ?: run{
                                    val chars = rowUi.chars?.filterNotNull() ?: listOf("chars is null")
                                    chars.getOrNull(0) ?: ""
                                }
                                loginInfo[rowUi.name] = char
                                val name =  rowUi.viewName ?: rowUi.name
                                val left = rowUi.style?.layout_justifySelf != "right"
                                rowView.text = if (left) char + name else name + char
                            }
                        }
                    }

                    is LinearLayout -> {
                        val items = rowUi.chars?.filterNotNull() ?: listOf("chars","is null")
                        val index = items.indexOf(value)
                        rowView.findViewById<AppCompatSpinner>(R.id.sp_type)?.setSelectionSafely(index)
                    }
                }
            } else {
                loginInfo[key] = value ?: ""
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    suspend fun evalUiJs(jsStr: String): String? {
        val source = viewModel.source ?: return null
        val loginJS = loginUrl ?: ""
        val result = rowUis?.let {
            getLoginData(it)
        } ?: viewModel.loginInfo.toMutableMap()
        return try {
            runScriptWithContext {
                source.evalJS("$loginJS\n$jsStr") {
                    put("result", result)
                    put("book", viewModel.book)
                    put("chapter", viewModel.chapter)
                }.toString()
            }
        } catch (e: Exception) {
            AppLog.put(source.getTag() + " loginUi err:" + (e.localizedMessage ?: e.toString()), e)
            null
        }
    }

    fun loginUi(json: String?): List<RowUi>? {
        return GSON.fromJsonArray<RowUi>(json).onFailure {
            AppLog.put("loginUi json parse err:" + it.localizedMessage, it)
        }.getOrNull()
    }

    @SuppressLint("SetTextI18n")
    private fun rowUiBuilderShared(source: BaseSource, rowUis: List<RowUi>?, deltaUp: Boolean) {
        val rows = rowUis ?: emptyList()
        val loginInfo = viewModel.loginInfo
        rowUiName.clear()
        rowUiName.addAll(rows.map { it.name })
        RowUiForm.render(
            container = binding.flexbox,
            rows = rows,
            values = buildLoginUiValues(rows, loginInfo),
            callback = object : RowUiForm.Callback {
                override fun onValueChanged(rowUi: RowUi, value: String) {
                    hasChange = true
                    loginInfo[rowUi.name] = value
                    when {
                        rowUi.type == Type.select && rowUi.action != null -> {
                            handleButtonClick(source, rowUi.action, rowUi.name, false)
                        }
                        (rowUi.type == Type.text || rowUi.type == Type.password)
                            && rowUi.action != null -> {
                            rowUiActionRunnables.remove(rowUi.name)?.let {
                                handler.removeCallbacks(it)
                            }
                            val runnable = Runnable {
                                handleButtonClick(source, rowUi.action, rowUi.name, false)
                            }
                            rowUiActionRunnables[rowUi.name] = runnable
                            handler.postDelayed(runnable, 600)
                        }
                    }
                }

                override fun onAction(rowUi: RowUi, isLongClick: Boolean) {
                    handleButtonClick(source, rowUi.action, rowUi.name, isLongClick)
                }

                override fun resolveViewName(
                    rowUi: RowUi,
                    fallback: String,
                    apply: (String) -> Unit
                ) {
                    resolveLoginViewName(rowUi, fallback, apply)
                }
            }
        )
    }

    private fun buildLoginUiValues(
        rows: List<RowUi>,
        loginInfo: MutableMap<String, String>
    ): Map<String, String> {
        val values = hashMapOf<String, String>()
        rows.forEach { rowUi ->
            val value = when (rowUi.type) {
                Type.select, Type.toggle -> {
                    val chars = rowUi.chars?.filterNotNull() ?: listOf("chars is null")
                    loginInfo[rowUi.name]?.takeIf { it.isNotEmpty() }
                        ?: rowUi.default
                        ?: chars.firstOrNull()
                        ?: ""
                }
                else -> loginInfo[rowUi.name] ?: rowUi.default.orEmpty()
            }
            if ((rowUi.type == Type.select || rowUi.type == Type.toggle)
                && loginInfo[rowUi.name] != value
            ) {
                hasChange = true
                loginInfo[rowUi.name] = value
            }
            values[rowUi.name] = value
        }
        return values
    }

    private fun resolveLoginViewName(
        rowUi: RowUi,
        fallback: String,
        apply: (String) -> Unit
    ) {
        val viewName = rowUi.viewName
        when {
            viewName == null -> apply(fallback)
            viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'' -> {
                val name = viewName.substring(1, viewName.length - 1)
                rowUi.viewName = name
                apply(name)
            }
            else -> {
                apply(fallback)
                execute {
                    evalUiJs(viewName)
                }.onSuccess { name ->
                    val resolvedName = name?.takeIf { it.isNotEmpty() } ?: "null"
                    rowUi.viewName = resolvedName
                    apply(resolvedName)
                }.onError { _ ->
                    apply("err")
                }
            }
        }
    }

    private fun applyModernRowUiStyle(rowUi: RowUi, view: View) {
        RowUiViewFactory.applyModernRowUiStyle(rowUi, view)
    }

    private fun applyModernTextButtonStyle(rowUi: RowUi, textView: TextView) {
        RowUiViewFactory.applyModernTextButtonStyle(rowUi, textView)
    }

    private fun buttonUi(source: BaseSource, rowUis: List<RowUi>?) {
        rowUiBuilderShared(source, rowUis, false)
        binding.toolBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_ok -> {
                    oKToClose = true
                    login(source)
                }

                R.id.menu_show_login_header -> alert {
                    setTitle(R.string.login_header)
                    source.getLoginHeader()?.let { loginHeader ->
                        setMessage(loginHeader)
                        positiveButton(R.string.copy_text) {
                            appCtx.sendToClip(loginHeader)
                        }
                    }
                }

                R.id.menu_del_login_header -> source.removeLoginHeader()
                R.id.menu_log -> showDialogFragment<AppLogDialog>()
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val source = viewModel.source ?: return
        loginUrl = source.getLoginJs()
        val loginUiStr = source.loginUi ?: return
        val codeStr = loginUiStr.let {
            when {
                it.startsWith("@js:") -> it.substring(4)
                it.startsWith("<js>") -> it.substring(4, it.lastIndexOf("<"))
                else -> null
            }
        }
        if (codeStr != null) {
            lifecycleScope.launch(Main) {
                withContext(IO) {
                    val loginUiJson = evalUiJs(codeStr)
                    rowUis = loginUi(loginUiJson)
                }
                buttonUi(source, rowUis)
            }
        } else {
            rowUis = loginUi(loginUiStr)
            buttonUi(source, rowUis)
        }
        binding.toolBar.setBackgroundColor(requireContext().primaryColor)
        binding.toolBar.setTitleTextColor(primaryTextColor)
        binding.toolBar.title = getString(R.string.login_source, source.getTag())
        binding.toolBar.applyUiToolbarTypeface(requireContext())
        binding.toolBar.inflateMenu(R.menu.source_login)
        binding.toolBar.menu.applyTint(requireContext())
    }

    private fun handleButtonClick(source: BaseSource, action: String?, name: String, isLongClick: Boolean) {
        lifecycleScope.launch(IO) {
            if (action.isAbsUrl()) {
                context?.openUrl(action!!)
            } else if (action != null) {
                // JavaScript
                val buttonFunctionJS = action
                val loginJS = loginUrl ?: return@launch
                kotlin.runCatching {
                    runScriptWithContext {
                        source.evalJS("$loginJS\n$buttonFunctionJS") {
                            put("java", sourceLoginJsExtensions)
                            put("result", getLoginData(rowUis))
                            put("book", viewModel.book)
                            put("chapter", viewModel.chapter)
                            put("isLongClick", isLongClick)
                        }
                    }
                }.onFailure { e ->
                    ensureActive()
                    AppLog.put("LoginUI Button $name JavaScript error", e)
                }
            }
        }
    }

    private fun getLoginData(rowUis: List<RowUi>?): MutableMap<String, String> {
        val loginData = hashMapOf<String, String>()
        rowUis?.forEachIndexed { index, rowUi ->
            when (rowUi.type) {
                Type.text, Type.password -> {
                    val rowView = binding.root.findViewById<View>(index + 1000)
                    ItemSourceEditBinding.bind(rowView).editText.text.let {
                        loginData[rowUi.name] = it?.toString() ?: rowUi.default ?: "" //没文本的时候存空字符串,而不是删除loginInfo
                    }
                }
            }
        }
        return viewModel.loginInfo.toMutableMap().apply { putAll(loginData) }
    }

    private fun login(source: BaseSource) {
        lifecycleScope.launch(IO) {
            val loginData = getLoginData(rowUis)
            if (loginData.isEmpty()) {
                source.removeLoginInfo()
                withContext(Main) {
                    dismiss()
                }
            } else if (source.putLoginInfo(GSON.toJson(loginData))) {
                try {
                    val buttonFunctionJS = "if (typeof login=='function'){ login.apply(this); } else { throw('Function login not implements!!!') }"
                    val loginJS = loginUrl ?: return@launch
                    runScriptWithContext {
                        source.evalJS("$loginJS\n$buttonFunctionJS") {
                            put("java", sourceLoginJsExtensions)
                            put("result", loginData)
                            put("book", viewModel.book)
                            put("chapter", viewModel.chapter)
                            put("isLongClick", false)
                        }
                    }
                    context?.toastOnUi(R.string.success)
                    withContext(Main) {
                        dismiss()
                    }
                } catch (e: Exception) {
                    AppLog.put("登录出错\n${e.localizedMessage}", e)
                    context?.toastOnUi("登录出错\n${e.localizedMessage}")
                    e.printOnDebug()
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (!oKToClose && hasChange) {
            val loginInfo = viewModel.loginInfo
            if (loginInfo.isEmpty()) {
                viewModel.source?.removeLoginInfo()
            } else {
                viewModel.source?.putLoginInfo(GSON.toJson(loginInfo))
            }
        }
        if (initHandler) {
            handler.removeCallbacksAndMessages(null)
            rowUiActionRunnables.clear()
        }
        super.onDismiss(dialog)
        activity?.finish()
    }

}
