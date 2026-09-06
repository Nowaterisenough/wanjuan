package io.wanjuan.app.ui.book.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestReaderSearchMatches {
    @Test
    fun literalSearchPreservesUtf16OffsetsAndTreatsMetacharactersLiterally() {
        val content = "\uD83D\uDE00春夜\n春夜.*"
        assertEquals(listOf(2..3, 5..6), ReaderSearchMatches.find(content, "春夜").toList())
        assertEquals(listOf(7..8), ReaderSearchMatches.find(content, ".*").toList())
        assertTrue(ReaderSearchMatches.find(content, "").none())
    }

    @Test
    fun regexSkipsZeroWidthResultsAndSupportsMultilineMatches() {
        assertTrue(ReaderSearchMatches.find("abc", "^", Regex("^")).none())
        assertEquals(listOf(0..3), ReaderSearchMatches.find("春夜\n风", "春夜\\s风", Regex("春夜\\s风")).toList())
    }

    @Test
    fun repeatedMatchesAreNonOverlappingAndBounded() {
        assertEquals(listOf(0..1, 2..3), ReaderSearchMatches.find("aaaaa", "aa").toList())
        assertEquals(5000, ReaderSearchMatches.find("a".repeat(6000), "a").count())
        assertEquals(5000, ReaderSearchMatches.find("a".repeat(6000), "a", Regex("a")).count())
    }
}
