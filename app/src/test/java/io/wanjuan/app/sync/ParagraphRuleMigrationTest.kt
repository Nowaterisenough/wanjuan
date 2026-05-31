package io.wanjuan.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ParagraphRuleMigrationTest {

    @Test
    fun paragraphRulesUseWanjuanDatabaseVersionAndSchema() {
        val database = repoFile("app/src/main/java/io/wanjuan/app/data/AppDatabase.kt").readText()
        val migrations = repoFile("app/src/main/java/io/wanjuan/app/data/DatabaseMigrations.kt").readText()

        assertTrue(database.contains("version = 94"))
        assertTrue(database.contains("ParagraphRule::class"))
        assertTrue(database.contains("BookParagraphRule::class"))
        assertTrue(database.contains("ParagraphRuleVar::class"))
        assertTrue(database.contains("abstract val paragraphRuleDao: ParagraphRuleDao"))
        assertTrue(migrations.contains("migration_92_93"))
        assertTrue(migrations.contains("CREATE TABLE IF NOT EXISTS `paragraph_rules`"))
        assertTrue(migrations.contains("CREATE TABLE IF NOT EXISTS `book_paragraph_rules`"))
        assertTrue(migrations.contains("CREATE TABLE IF NOT EXISTS `paragraph_rule_vars`"))
        assertTrue(repoFile("app/schemas/io.wanjuan.app.data.AppDatabase/94.json").isFile)
        assertFalse(File(repoRoot(), "app/schemas/io.legado.app.data.AppDatabase/94.json").exists())
    }

    @Test
    fun paragraphRuleProcessorSupportsStableCacheKeysAndPclickWrapping() {
        val processor = repoFile("app/src/main/java/io/wanjuan/app/help/book/ParagraphRuleProcessor.kt").readText()
        val jsExtensions = repoFile("app/src/main/java/io/wanjuan/app/help/book/ParagraphRuleJsExtensions.kt").readText()

        assertTrue(processor.contains("object ParagraphRuleProcessor"))
        assertTrue(processor.contains("fun isParagraphClick(click: String?): Boolean"))
        assertTrue(processor.contains("fun wrapClick(ruleId: Long, js: String): String"))
        assertTrue(processor.contains("fun clickKey(ruleId: Long, js: String): String"))
        assertTrue(processor.contains("fun stableKey(rule: ParagraphRule): String"))
        assertTrue(processor.contains("private fun wrapPclicks(ruleId: Long, text: String): String"))
        assertTrue(processor.contains("\"pclick\""))
        assertTrue(processor.contains("BrowserCallback"))
        assertTrue(processor.contains("sourceKey: String? = null"))
        assertTrue(processor.contains("processCacheKey("))
        assertTrue(jsExtensions.contains("class ParagraphRuleJsExtensions"))
        assertTrue(jsExtensions.contains("fun showBrowser(url: String"))
        assertTrue(jsExtensions.contains("enabledCookieJar"))
    }

    @Test
    fun readerRoutesParagraphRulesThroughMenuContentAndImageClicks() {
        val readBook = repoFile("app/src/main/java/io/wanjuan/app/model/ReadBook.kt").readText()
        val readActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookActivity.kt").readText()
        val menu = repoFile("app/src/main/res/menu/book_read.xml").readText()
        val layout = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/page/provider/TextChapterLayout.kt").readText()

        assertTrue(readBook.contains("ParagraphRuleProcessor.process("))
        assertTrue(readBook.contains("paragraphRuleLayoutKey"))
        assertTrue(readBook.contains("fun invalidateParagraphRuleLayout()"))
        assertTrue(readActivity.contains("ParagraphRuleManageActivity"))
        assertTrue(readActivity.contains("private fun evalParagraphRuleClick(click: String?, src: String): Boolean"))
        assertTrue(readActivity.contains("urlOptionMap?.get(\"pclick\")"))
        assertTrue(readActivity.contains("ParagraphRuleProcessor.isParagraphClick(click)"))
        assertTrue(readActivity.contains("BottomWebViewDialog("))
        assertTrue(menu.contains("@+id/menu_paragraph_rule_manage"))
        assertTrue(layout.contains("data-legado-pclick"))
    }

    @Test
    fun commentBrowserUsesDedicatedReusableWebViewSession() {
        val session = repoFile("app/src/main/java/io/wanjuan/app/ui/widget/dialog/CommentWebViewSession.kt").readText()
        val styleCache = repoFile("app/src/main/java/io/wanjuan/app/ui/widget/dialog/CommentBrowserStyleCache.kt").readText()
        val dialog = repoFile("app/src/main/java/io/wanjuan/app/ui/widget/dialog/BottomWebViewDialog.kt").readText()
        val dialogLayout = repoFile("app/src/main/res/layout/dialog_web_view.xml").readText()

        assertTrue(session.contains("class CommentWebViewSession"))
        assertTrue(session.contains("val shared: CommentWebViewSession"))
        assertTrue(session.contains("fun prepare(context: Context)"))
        assertTrue(session.contains("fun detachForReuse(pooled: PooledWebView)"))
        assertTrue(styleCache.contains("object CommentBrowserStyleCache"))
        assertTrue(styleCache.contains("getForPreOpen"))
        assertTrue(styleCache.contains("remember("))
        assertTrue(dialog.contains("webViewSession: CommentWebViewSession? = null"))
        assertTrue(dialog.contains("session?.acquire(context) ?: WebViewPool.acquire(context)"))
        assertTrue(dialog.contains("webViewSession?.detachForReuse(pooledWebView) ?: WebViewPool.release(pooledWebView)"))
        assertTrue(dialog.contains("applySheetCorners(radius)"))
        assertTrue(dialog.contains("isHuaweiSystemDevice"))
        assertTrue(dialogLayout.contains("@+id/native_sheet_surface"))
        assertTrue(dialogLayout.contains("@+id/web_view_placeholder"))
    }

    private fun repoFile(path: String): File = File(repoRoot(), path)

    private fun repoRoot(): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app").isDirectory && File(it, ".git").exists() }
            ?: error("repo root not found")
    }
}
