package io.wanjuan.app.help.ai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader
import java.util.concurrent.CancellationException

class TestAiResponsesProtocol {
    @Test fun endpointsMigrateWithoutChangingCustomPrefixes() {
        val expected = "https://example.org/proxy/v1/responses"
        for (suffix in listOf("/v1", "/v1/", "/v1/responses", "/v1/chat/completions")) {
            assertEquals(expected, AiResponsesProtocol.responsesUrl(" https://example.org/proxy$suffix "))
        }
        assertEquals("https://example.org/v1/responses", AiResponsesProtocol.responsesUrl("https://example.org"))
        assertEquals("https://example.org/proxy/v1/models", AiResponsesProtocol.modelsUrl(expected))
    }

    @Test fun requestUsesResponsesInputsAndFlatOptionalTools() {
        val input = JSONObject("""{"role":"user","content":[{"type":"input_text","text":"chapter"},{"type":"input_image","image_url":"data:image/jpeg;base64,dGVzdA=="}]}""")
        val tool = JSONObject("""{"type":"function","function":{"name":"lookup","description":"find","parameters":{"type":"object","properties":{}}}}""")
        val request = JSONObject(AiResponsesProtocol.request("chosen-model", listOf(input), listOf(tool), true))
        assertEquals("chosen-model", request.getString("model"))
        assertFalse(request.has("messages"))
        assertFalse(request.getBoolean("store"))
        assertEquals("input_image", request.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(1).getString("type"))
        val tools = request.getJSONArray("tools")
        assertEquals("lookup", tools.getJSONObject(0).getString("name"))
        assertFalse(tools.getJSONObject(0).has("function"))
        assertFalse(tools.getJSONObject(0).getBoolean("strict"))
        assertEquals("web_search", tools.getJSONObject(1).getString("type"))
    }

    @Test fun readerHasNoToolsUnlessWebSearchRequested() {
        val body = JSONObject(AiResponsesProtocol.request("model", emptyList(), emptyList(), false))
        assertFalse(body.has("tools"))
        val search = JSONObject(AiResponsesProtocol.request("model", emptyList(), emptyList(), true))
        assertEquals("required", search.getString("tool_choice"))
    }

    @Test fun streamsTextAndClickableCitations() {
        val partials = mutableListOf<String>()
        val message = JSONObject("""{"type":"message","role":"assistant","content":[{"type":"output_text","text":"answer","annotations":[{"type":"url_citation","url":"https://example.org/wiki/(a)","title":"Source [one]"},{"type":"url_citation","url":"javascript:alert(1)","title":"bad"}]}]}""")
        val stream = ": heartbeat\r\nevent: response.output_text.delta\r\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\"ans\"}\r\n\r\n" + completion(listOf(message))
        val turn = read(stream, partials::add)
        assertEquals("ans", partials.first())
        assertTrue(turn.content.contains("[Source  one ](https://example.org/wiki/%28a%29)"))
        assertFalse(turn.content.contains("javascript:"))
        assertEquals(turn.content, partials.last())
    }

    @Test fun toolContinuationPreservesReasoningAndCallIds() {
        val reasoning = JSONObject("""{"type":"reasoning","id":"rs1","encrypted_content":"encrypted","summary":[]}""")
        val function = JSONObject("""{"type":"function_call","id":"fc1","call_id":"call1","name":"lookup","arguments":"{}"}""")
        val turn = read(completion(listOf(reasoning, function)))
        val result = AiResponsesProtocol.toolResult("call1", "result")
        val next = JSONObject(AiResponsesProtocol.request("m", turn.output + result, emptyList(), false))
        assertEquals("encrypted", next.getJSONArray("input").getJSONObject(0).getString("encrypted_content"))
        assertEquals("function_call_output", next.getJSONArray("input").getJSONObject(2).getString("type"))
        assertEquals("call1", result.getString("call_id"))
    }

    @Test fun nonStreamingResponsesAreAccepted() {
        val response = JSONObject().put("status", "completed").put("output", JSONArray().put(message("hello")))
        val turn = AiResponsesProtocol.read(StringReader(response.toString()).buffered(), false, {}, {})
        assertEquals("hello", turn.content)
    }

    @Test fun refusalsAreVisible() {
        val refusal = JSONObject("""{"type":"message","content":[{"type":"refusal","refusal":"cannot answer"}]}""")
        assertEquals("cannot answer", read(completion(listOf(refusal))).content)
    }

    @Test fun truncatedStreamIsNeverSuccessful() {
        assertThrows(IllegalStateException::class.java) {
            read("data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\ndata: [DONE]\n\n")
        }
    }

    @Test fun failuresAndIncompleteOutputsAreErrors() {
        for (type in listOf("response.failed", "response.incomplete")) {
            assertThrows(IllegalStateException::class.java) {
                read("data: {\"type\":\"$type\",\"response\":{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"}}}\n\n")
            }
        }
    }

    @Test fun cancellationIsNotConvertedToSuccess() {
        assertThrows(CancellationException::class.java) {
            AiResponsesProtocol.read(StringReader(completion(listOf(message("late")))).buffered(), true,
                {}, {}, { throw CancellationException() })
        }
    }

    @Test fun completionAtEofAndMultilineFramesWork() {
        val payload = JSONObject().put("type", "response.completed").put("response", JSONObject()
            .put("status", "completed").put("output", JSONArray().put(message("end"))))
        val stream = payload.toString(2).lines().joinToString("\n") { "data: $it" }
        assertEquals("end", read(stream).content)
    }

    private fun message(text: String) = JSONObject().put("type", "message").put("content",
        JSONArray().put(JSONObject().put("type", "output_text").put("text", text)))

    private fun completion(items: List<JSONObject>) = "data: " + JSONObject().put("type", "response.completed")
        .put("response", JSONObject().put("status", "completed").put("output", JSONArray(items))) + "\n\n"

    private fun read(stream: String, partial: (String) -> Unit = {}) =
        AiResponsesProtocol.read(StringReader(stream).buffered(), true, partial, {})
}
