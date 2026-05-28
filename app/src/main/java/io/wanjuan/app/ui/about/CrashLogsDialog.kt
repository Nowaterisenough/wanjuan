package io.wanjuan.app.ui.about

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.wanjuan.app.R
import io.wanjuan.app.base.BaseDialogFragment
import io.wanjuan.app.base.BaseViewModel
import io.wanjuan.app.base.adapter.ItemViewHolder
import io.wanjuan.app.base.adapter.RecyclerAdapter
import io.wanjuan.app.databinding.DialogRecyclerViewBinding
import io.wanjuan.app.databinding.Item1lineTextBinding
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.lib.theme.primaryColor
import io.wanjuan.app.ui.widget.dialog.TextDialog
import io.wanjuan.app.utils.FileDoc
import io.wanjuan.app.utils.FileUtils
import io.wanjuan.app.utils.delete
import io.wanjuan.app.utils.find
import io.wanjuan.app.utils.getFile
import io.wanjuan.app.utils.list
import io.wanjuan.app.utils.setLayout
import io.wanjuan.app.utils.showDialogFragment
import io.wanjuan.app.utils.toastOnUi
import io.wanjuan.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.isActive
import java.io.FileFilter

class CrashLogsDialog : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val viewModel by viewModels<CrashViewModel>()
    private val adapter by lazy { LogAdapter() }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(R.string.crash_log)
        binding.toolBar.inflateMenu(R.menu.crash_log)
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        viewModel.logLiveData.observe(viewLifecycleOwner) {
            adapter.setItems(it)
        }
        viewModel.initData()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_clear -> viewModel.clearCrashLog()
        }
        return true
    }

    private fun showLogFile(fileDoc: FileDoc) {
        viewModel.readFile(fileDoc) {
            if (lifecycleScope.isActive) {
                showDialogFragment(TextDialog(fileDoc.name, it))
            }
        }

    }

    inner class LogAdapter : RecyclerAdapter<FileDoc, Item1lineTextBinding>(requireContext()) {

        override fun getViewBinding(parent: ViewGroup): Item1lineTextBinding {
            return Item1lineTextBinding.inflate(inflater, parent, false)
        }

        override fun registerListener(holder: ItemViewHolder, binding: Item1lineTextBinding) {
            binding.root.setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let { item ->
                    showLogFile(item)
                }
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: Item1lineTextBinding,
            item: FileDoc,
            payloads: MutableList<Any>
        ) {
            binding.textView.text = item.name
        }

    }

    class CrashViewModel(application: Application) : BaseViewModel(application) {

        val logLiveData = MutableLiveData<List<FileDoc>>()

        fun initData() {
            execute {
                val list = arrayListOf<FileDoc>()
                context.externalCacheDir
                    ?.getFile("crash")
                    ?.listFiles(FileFilter { it.isFile })
                    ?.forEach {
                        list.add(FileDoc.fromFile(it))
                    }
                val backupPath = AppConfig.backupPath
                if (!backupPath.isNullOrEmpty()) {
                    val uri = Uri.parse(backupPath)
                    FileDoc.fromUri(uri, true)
                        .find("crash")
                        ?.list {
                            !it.isDir
                        }?.let {
                            list.addAll(it)
                        }
                }
                return@execute list.sortedByDescending { it.name }.distinctBy { it.name }
            }.onSuccess {
                logLiveData.postValue(it)
            }
        }

        fun readFile(fileDoc: FileDoc, success: (String) -> Unit) {
            execute {
                String(fileDoc.readBytes())
            }.onSuccess {
                success.invoke(it)
            }.onError {
                context.toastOnUi(it.localizedMessage)
            }
        }

        fun clearCrashLog() {
            execute {
                context.externalCacheDir
                    ?.getFile("crash")
                    ?.let {
                        FileUtils.delete(it, false)
                    }
                val backupPath = AppConfig.backupPath
                if (!backupPath.isNullOrEmpty()) {
                    val uri = Uri.parse(backupPath)
                    FileDoc.fromUri(uri, true)
                        .find("crash")
                        ?.delete()
                }
            }.onError {
                context.toastOnUi(it.localizedMessage)
            }.onFinally {
                initData()
            }
        }

    }

}