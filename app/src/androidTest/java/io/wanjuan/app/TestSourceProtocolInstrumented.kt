package io.wanjuan.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.wanjuan.app.data.entities.rule.ContentRule
import io.wanjuan.app.help.source.exploreKinds
import io.wanjuan.app.help.source.clearExploreKindsCache
import io.wanjuan.app.help.book.BookHelp
import io.wanjuan.app.help.source.BookSourceImportParser
import io.wanjuan.app.model.BatchContent
import io.wanjuan.app.model.webBook.WebBook
import io.wanjuan.app.ui.book.changesource.KeywordVerifier
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TestSourceProtocolInstrumented {
    @Test fun pairedRubyAndHardLineBreakArePreserved() {
        val html = org.jsoup.Jsoup.parse("<p style='white-space: pre'>A\nB<ruby>東<rt>とう</rt>京<rt>きょう</rt></ruby></p>")
        val dom = io.wanjuan.app.model.localBook.EpubDomBuilder({ _, _ -> "" }, { _, url -> url })
            .build(html, html.body(), "chapter.xhtml")
        val layout = io.wanjuan.app.model.localBook.EpubLayoutEngine(viewportWidth = 300, viewportHeight = 600,
            basePaint = android.text.TextPaint().apply { textSize = 20f }).layout(dom)
        val runs = layout.pages.flatMap { it.commands }.filterIsInstance<io.wanjuan.app.model.localBook.EpubTextRun>()
        assertEquals(listOf("東" to "とう", "京" to "きょう"), runs.filter { it.annotation != null }.map { it.text to it.annotation })
        assertTrue(runs.first { it.text == "A" }.baseline < runs.first { it.text == "B" }.baseline)
    }

    @Test fun markdownFollowsEditsAndRetainsOtherFiles() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val dir = java.io.File(context.cacheDir, "excerpts-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val uri = android.net.Uri.fromFile(dir)
            val note = io.wanjuan.app.data.entities.Bookmark(bookName = "Book", bookAuthor = "Author", bookText = "quote", content = "one")
            val sync = io.wanjuan.app.ui.book.bookmark.BookmarkMarkdownSync
            sync.update(uri, listOf(note))
            val file = dir.walk().single { it.extension == "md" }
            assertTrue(file.readText().contains("one"))
            sync.update(uri, listOf(note.copy(content = "two")))
            assertTrue(file.readText().contains("two"))
            val other = java.io.File(file.parentFile, "mine.md").apply { writeText("mine") }
            sync.update(uri, emptyList())
            assertFalse(file.exists())
            assertEquals("mine", other.readText())
        } finally { dir.deleteRecursively() }
    }

    @Test fun largeScriptRoundTripsThroughARecreatedEditor() {
        val transfer = io.wanjuan.app.ui.code.SourceEditSession
        val launch = android.content.Intent()
        val original = "很长的脚本\n".repeat(90000)
        transfer.start(launch, original)
        assertNull(launch.getStringExtra("text"))
        val edited = original + "完"
        transfer.checkpoint(launch, edited)
        val recreated = android.content.Intent(launch)
        assertEquals(original, transfer.original(recreated))
        assertEquals(edited, transfer.draft(recreated))
        val result = android.content.Intent()
        transfer.result(recreated, result, edited)
        assertEquals(edited, transfer.consume(result))
    }

    @Test fun discoveryScriptStateSurvivesDiscardingTheLiveMap() = runBlocking {
        val source = io.wanjuan.app.data.entities.BookSource(bookSourceUrl = "https://fixture.invalid/${UUID.randomUUID()}",
            exploreUrl = "@js:infoMap.put('category','saved'); 'Category::/books'")
        try {
            assertEquals("Category", source.exploreKinds().single().title)
            val map = io.wanjuan.app.ui.main.explore.ExploreAdapter.exploreInfoMapList
            map.remove(source.bookSourceUrl)
            io.wanjuan.app.help.CacheManager.deleteMemory("infoMap_" + source.bookSourceUrl)
            assertEquals("saved", io.wanjuan.app.utils.InfoMap(source.bookSourceUrl)["category"])
        } finally {
            source.clearExploreKindsCache()
            io.wanjuan.app.help.CacheManager.delete("infoMap_" + source.bookSourceUrl)
        }
    }

    @Test fun singleFileFromImportToChapterAndPartialBatch() = runBlocking {
        val url = "https://fixture.invalid/${UUID.randomUUID()}"
        val source = BookSourceImportParser.parse("""
            const config = {bookSourceName:'Fixture',bookSourceUrl:'$url',maxBatchSize:3};
            function search(key,page){return [{name:key,author:'Author',bookUrl:'$url/book'}]}
            function getBookInfo(book){return {intro:'details',tocUrl:'$url/toc'}}
            function getChapters(book){return [{title:'One',url:'$url/one'},{title:'Two',url:'$url/two'}]}
            function getContent(chapter,book){return 'single '+chapter.title}
            function getContentBatch(chapters, book){java.cacheContent(chapters[0].url,'batch One')}
            function loginUi(state){return {rows:[{type:'label',name:'Login'}]}}
            function loginAction(action,state,form){return {close:true}}
        """.trimIndent().reader()).sources.single()
        assertEquals("{\"version\":2}", source.loginUi)
        assertEquals(3,source.ruleContent?.maxBatchSize)
        val book = WebBook.searchBookAwait(source,"Fixture-${UUID.randomUUID()}").single().toBook()
        WebBook.getBookInfoAwait(source,book)
        assertEquals("details",book.intro)
        val chapters = WebBook.getChapterListAwait(source,book).getOrThrow()
        assertEquals(listOf(0,1),chapters.map { it.index })
        assertEquals("single Two", WebBook.getContentAwait(source,book,chapters[1],needSave=false))
        assertTrue(KeywordVerifier.verify(source,book,"Two",listOf("single"),listOf("wrong"),Semaphore(1)).passed)
        val saved = mutableListOf<String>()
        try {
            BatchContent.download(source,book,chapters) { chapter, _ -> saved.add(chapter.title) }
            assertEquals(listOf("One"),saved)
            assertEquals("batch One", BookHelp.getContent(book,chapters.first()))
            source.mainJs = null
            source.ruleContent = ContentRule(contentBatch="@js:java.cacheContent(result[1].url,'batch Two')",maxBatchSize=2)
            BatchContent.download(source,book,chapters) { chapter, _ -> saved.add(chapter.title) }
            assertEquals(listOf("One","Two"),saved)
        } finally {
            BookHelp.clearCache(book)
        }
    }
}
