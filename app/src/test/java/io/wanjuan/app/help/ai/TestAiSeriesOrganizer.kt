package io.wanjuan.app.help.ai

import io.wanjuan.app.data.entities.SeriesBookInfo
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TestAiSeriesOrganizer {
    private fun book(title: String, author: String = "甲", series: String? = null) =
        SeriesBookInfo("private/$title", title, author, null, series)

    @Test fun joinsCrossAuthorExtraAndHonorsChangesDuringRequest() = runBlocking {
        val main = book("海岸线", series = "海岸线")
        val extra = book("远航", "乙")
        val optedOut = book("秋信", series = "")
        val shelf = listOf(main, extra, optedOut)
        val result = AiSeriesOrganizer.propose(shelf) { request ->
            assertFalse(request.contains("private/"))
            assertFalse(request.contains("秋信"))
            assertEquals(1, JSONObject(request).getJSONArray("existing").length())
            """{"groups":[{"name":"海岸线","ids":[1]}]}"""
        }
        assertEquals(mapOf("海岸线" to listOf(extra)), AiSeriesOrganizer.applicable(result, shelf))
        assertTrue(AiSeriesOrganizer.applicable(result, listOf(main, extra.copy(seriesName = "另一个系列"))).isEmpty())
        assertTrue(AiSeriesOrganizer.applicable(result, listOf(extra)).isEmpty())
    }

    @Test fun joinsSeriesAcrossBatchesWithoutCreatingSingletons() = runBlocking {
        val shelf = (1..61).map { book("书$it") }
        var batches = 0
        val result = AiSeriesOrganizer.propose(shelf) { request ->
            if (++batches == 1) """{"groups":[{"name":"全集","ids":[1]},{"name":"孤本","ids":[2]}]}"""
            else {
                assertTrue(JSONObject(request).getJSONArray("existing").toString().contains("全集"))
                """{"groups":[{"name":"全集","ids":[1]}]}"""
            }
        }
        assertEquals(mapOf("全集" to listOf(shelf.first(), shelf.last())), AiSeriesOrganizer.applicable(result, shelf))
    }

    @Test fun oversizedBatchShrinksAndRetainsEveryBookId() = runBlocking {
        val shelf = (1..7).map { book("卷$it") }
        val acceptedTitles = mutableListOf<String>()
        val proposed = AiSeriesOrganizer.propose(shelf) { request ->
            val input = JSONObject(request).getJSONArray("books")
            if (input.length() > 2) throw AiResponsesProtocol.httpError(400,
                """{"error":{"code":"context_length_exceeded"}}""", "", emptyList())
            val ids = (0 until input.length()).map { i ->
                val row = input.getJSONObject(i)
                acceptedTitles += row.getString("title")
                row.getInt("id")
            }
            """{"groups":[{"name":"全集","ids":$ids}]}"""
        }
        assertEquals(shelf.map { it.name }, acceptedTitles)
        assertEquals(shelf, AiSeriesOrganizer.applicable(proposed, shelf)["全集"])
    }

    @Test fun malformedRequestIsReportedWithoutRetrying() = runBlocking {
        var calls = 0
        val failure = runCatching { AiSeriesOrganizer.propose(listOf(book("一"), book("二"))) {
            calls++
            throw AiResponsesProtocol.httpError(400, """{"detail":"Unknown model"}""", "", emptyList())
        } }.exceptionOrNull()
        assertEquals("HTTP 400 · Unknown model", failure?.message)
        assertEquals(1, calls)
    }

    @Test fun rejectsAmbiguousOrUnknownBookIds() = runBlocking {
        val shelf = listOf(book("一"), book("二"))
        for (ids in listOf("1,1", "1,3", "true", "1.5")) {
            assertTrue(runCatching {
                AiSeriesOrganizer.propose(shelf) { """{"groups":[{"name":"系列","ids":[$ids]}]}""" }
            }.isFailure)
        }
    }
}
