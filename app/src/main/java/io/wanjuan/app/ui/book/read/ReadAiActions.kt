package io.wanjuan.app.ui.book.read

import android.content.Intent
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import io.wanjuan.app.R
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.ui.config.ConfigActivity
import io.wanjuan.app.ui.config.ConfigTag

/** Both readers use the same actions, consent and Responses settings. */
object ReadAiActions {
    fun show(activity: FragmentActivity, manga: Boolean = false, open: (String, Boolean, Boolean) -> Unit) {
        if (!checkConfig(activity)) return
        val actions = if (manga) arrayOf("解读当前页", "解读本章（最多前 12 页）", "人物生平 / 主要事迹", "自由提问")
            else arrayOf("分析本章主要内容", "人物生平 / 主要事迹", "自由提问")
        AlertDialog.Builder(activity).setTitle(R.string.ai_read_actions)
            .setItems(actions) { _, index ->
                when {
                    manga && index == 0 -> confirm(activity, true) {
                        open("请分析当前漫画页的主要内容、人物关系、关键对白和画面细节；看不清的文字请说明，不要猜测。", false, false)
                    }
                    index == (if (manga) 1 else 0) -> confirm(activity, manga) {
                        open("请分析所提供章节的主要内容，按情节梗概、关键事件、人物关系、主题与伏笔组织回答，并给出原文或画面依据。不要泄露后续章节。", false, manga)
                    }
                    index == (if (manga) 2 else 1) -> character(activity, manga, open)
                    else -> confirm(activity, manga) { open("", false, false) }
                }
            }.show()
    }

    fun checkConfig(activity: FragmentActivity): Boolean {
        if (AppConfig.aiAssistantEnabled && AppConfig.aiCurrentProvider != null && AppConfig.aiCurrentModelConfig != null) return true
        AlertDialog.Builder(activity).setTitle(R.string.ai_read_actions)
            .setMessage("请先配置 OpenAI Responses 接口、选择模型并启用 AI。漫画需要支持图像输入的模型。")
            .setPositiveButton(R.string.ai_setting) { _, _ ->
                activity.startActivity(Intent(activity, ConfigActivity::class.java).putExtra("configTag", ConfigTag.AI_CONFIG))
            }.setNegativeButton(R.string.dialog_cancel, null).show()
        return false
    }

    fun confirm(activity: FragmentActivity, manga: Boolean, proceed: () -> Unit) {
        val content = if (manga) "所选漫画页面图像" else "当前章节正文"
        AlertDialog.Builder(activity).setTitle("发送阅读内容")
            .setMessage("将把$content、书名及提问发送至你配置的 Responses 服务，可能产生用量费用。${if (manga) "需要支持图像输入的模型；长图缩放后可能影响文字识别。" else ""}")
            .setPositiveButton("继续") { _, _ -> proceed() }
            .setNegativeButton(R.string.dialog_cancel, null).show()
    }

    private fun character(activity: FragmentActivity, manga: Boolean, open: (String, Boolean, Boolean) -> Unit) {
        val input = EditText(activity).apply { hint = "输入人物姓名"; setSingleLine() }
        AlertDialog.Builder(activity).setTitle("人物资料").setView(input)
            .setMessage("书内分析只依据已提供内容；联网检索会发送检索词，可能涉及后续剧情，需要服务支持 web_search。")
            .setPositiveButton("联网检索") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) confirm(activity, manga) {
                    open("请联网检索人物「$name」的生平、主要事迹、人物关系和时间线。先核对其属于本书角色还是同名真实人物，区分原著设定与历史事实。提供可点击来源；查不到请明确说明。涉及后续剧情请放在单独标记的剧透部分。", true, false)
                }
            }.setNeutralButton("仅分析书内") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) confirm(activity, manga) {
                    open("仅依据提供的章节内容，梳理人物「$name」的生平线索、主要事迹和人物关系。缺少的信息请明确说明，不推测后续剧情。", false, false)
                }
            }.setNegativeButton(R.string.dialog_cancel, null).show()
    }
}
