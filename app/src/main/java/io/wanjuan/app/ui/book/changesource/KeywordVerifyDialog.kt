package io.wanjuan.app.ui.book.changesource

import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import io.wanjuan.app.R
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

/**
 * 关键词校验对话框（对齐 Z·阅读 同名功能）：
 * 输入「校验章节名 / 必含词 / 禁词」，对每个候选书源抓该章正文并判定，
 * 列出通过与不通过的原因。设置不保存，关闭即失效。
 */
class KeywordVerifyDialog : DialogFragment() {

    private val results = mutableListOf<KeywordVerifier.SourceResult>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val owner = requireParentFragment() as ChangeBookSourceDialog
        val initialChapter = owner.currentChapterTitle.orEmpty()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
        }

        fun label(text: String): TextView = TextView(context).apply {
            this.text = text
            textSize = 12f
            setPadding(0, dp(10), 0, dp(2))
        }

        fun input(hint: String, initial: String): EditText = EditText(context).apply {
            this.hint = hint
            setText(initial)
            setSingleLine(true)
            textSize = 15f
        }

        root.addView(label("校验章节名"))
        val chapterInput = input("如：第一百四十五章 寻人", initialChapter)
        root.addView(chapterInput)

        root.addView(label("必含词"))
        val mustInput = input("多个词用 | 分隔，可留空", "")
        root.addView(mustInput)

        root.addView(label("禁词"))
        val banInput = input("多个词用 | 分隔，可留空", "")
        root.addView(banInput)

        root.addView(TextView(context).apply {
            text = "逐个显示候选书源的校验结果。多词用 | 分隔。" +
                "设置不保存，关闭窗口后自动失效。"
            textSize = 12f
            setPadding(0, dp(10), 0, dp(8))
        })

        val resultView = TextView(context).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val scroll = ScrollView(context).apply {
            addView(resultView)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(200)
            )
        }
        root.addView(scroll)

        val dialog = AlertDialog.Builder(context)
            .setTitle("关键词校验")
            .setView(root)
            .setPositiveButton("开始校验", null)
            .setNeutralButton("复制结果", null)
            .setNegativeButton("关闭", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                startVerify(
                    sources = owner.verificationCandidates(),
                    chapterName = chapterInput.text?.toString()?.trim().orEmpty(),
                    mustRaw = mustInput.text?.toString().orEmpty(),
                    banRaw = banInput.text?.toString().orEmpty(),
                    resultView = resultView,
                    button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                )
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val text = buildString {
                    results.forEach { r ->
                        append(if (r.passed) "✅ " else "❌ ").append(r.sourceName)
                        append("  ").append(r.reason)
                        r.chapterTitle?.let { append("  [").append(it).append("]") }
                        append("\n")
                    }
                }
                if (text.isBlank()) return@setOnClickListener
                val clipboard = requireContext()
                    .getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("keywordVerify", text))
            }
        }
        return dialog
    }

    private fun startVerify(
        sources: List<Book>,
        chapterName: String,
        mustRaw: String,
        banRaw: String,
        resultView: TextView,
        button: Button
    ) {
        if (sources.isEmpty()) {
            resultView.text = "没有可校验的书源"
            return
        }
        val must = KeywordVerifier.parseKeywords(mustRaw)
        val ban = KeywordVerifier.parseKeywords(banRaw)
        results.clear()
        resultView.text = "开始校验 ${sources.size} 个书源…\n"
        button.isEnabled = false

        lifecycleScope.launch {
            val semaphore = Semaphore(4)
            var passed = 0
            sources.forEachIndexed { index, book ->
                val result = withContext(Dispatchers.IO) {
                    val source = appDb.bookSourceDao.getBookSource(book.origin)
                    if (source == null) KeywordVerifier.SourceResult(book.originName, book.origin, false, "书源不存在")
                    else KeywordVerifier.verify(source, book, chapterName, must, ban, semaphore)
                }
                results.add(result)
                if (result.passed) passed++
                withContext(Dispatchers.Main) {
                    resultView.append(
                        (if (result.passed) "✅ " else "❌ ") + result.sourceName +
                            "  " + result.reason +
                            (result.chapterTitle?.let { "  [" + it + "]" } ?: "") + "\n"
                    )
                    resultView.append("进度 ${index + 1}/${sources.size}  通过 $passed\n")
                }
            }
            withContext(Dispatchers.Main) {
                resultView.append("\n完成：通过 $passed / 共 ${sources.size}")
                button.isEnabled = true
            }
        }
    }

}
