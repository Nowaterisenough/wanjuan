package io.wanjuan.app.help.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader

/** Stateless Responses conversations keep output items (including reasoning) for tool continuations. */
internal object AiResponsesProtocol {
    fun responsesUrl(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return when {
            base.endsWith("/responses") -> base
            base.endsWith("/chat/completions") -> base.removeSuffix("/chat/completions") + "/responses"
            base.endsWith("/v1") -> "$base/responses"
            else -> "$base/v1/responses"
        }
    }

    fun modelsUrl(baseUrl: String): String = if (baseUrl.trim().trimEnd('/').endsWith("/models")) {
        baseUrl.trim().trimEnd('/')
    } else responsesUrl(baseUrl).removeSuffix("/responses") + "/models"

    fun request(model: String, input: List<JSONObject>, tools: List<JSONObject>, webSearch: Boolean): String =
        JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("store", false)
            put("include", JSONArray().put("reasoning.encrypted_content"))
            put("input", JSONArray(input))
            val definitions = JSONArray()
            tools.forEach { definition ->
                val function = definition.optJSONObject("function") ?: definition
                definitions.put(JSONObject(function.toString()).apply {
                    put("type", "function")
                    // Existing local tools have optional parameters; do not implicitly enable strict schemas.
                    put("strict", false)
                })
            }
            if (webSearch) definitions.put(JSONObject().put("type", "web_search"))
            if (definitions.length() > 0) put("tools", definitions)
            if (webSearch && tools.isEmpty()) put("tool_choice", "required")
        }.toString()

    fun toolResult(callId: String, output: String) = JSONObject()
        .put("type", "function_call_output").put("call_id", callId).put("output", output)

    data class Turn(val content: String, val output: List<JSONObject>)

    fun read(
        reader: BufferedReader,
        isEventStream: Boolean,
        onPartial: (String) -> Unit,
        onThinking: (String) -> Unit,
        checkCancelled: () -> Unit = {}
    ): Turn {
        if (!isEventStream) return completed(JSONObject(reader.readText())).also { onPartial(it.content) }
        val text = StringBuilder()
        var result: Turn? = null
        val frame = StringBuilder()
        fun consume() {
            if (frame.isEmpty()) return
            val payload = frame.toString()
            frame.clear()
            if (payload == "[DONE]") return
            val event = JSONObject(payload)
            when (event.optString("type")) {
                "response.output_text.delta", "response.refusal.delta" -> {
                    text.append(event.optString("delta"))
                    onPartial(text.toString())
                }
                "response.reasoning_summary_text.delta" -> onThinking(event.optString("delta"))
                "response.completed" -> {
                    result = completed(event.getJSONObject("response"))
                    onPartial(result.content)
                }
                "response.failed", "response.incomplete" -> error(failure(event.getJSONObject("response")))
                "error" -> error(event.optString("message", "Responses request failed"))
            }
        }
        while (result == null) {
            checkCancelled()
            val line = reader.readLine() ?: break
            if (line.isEmpty()) consume()
            else if (line.startsWith("data:")) {
                if (frame.isNotEmpty()) frame.append('\n')
                frame.append(line.removePrefix("data:").removePrefix(" "))
            }
        }
        consume()
        return result ?: error("Responses stream ended before response.completed; please retry")
    }

    private fun failure(response: JSONObject): String =
        response.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
            ?: "Responses ${response.optString("status")}: ${response.optJSONObject("incomplete_details")?.optString("reason").orEmpty()}"

    private fun completed(response: JSONObject): Turn {
        check(response.optString("status") == "completed") { failure(response) }
        val output = response.optJSONArray("output") ?: JSONArray()
        val items = (0 until output.length()).map { output.getJSONObject(it) }
        val citations = linkedMapOf<String, String>()
        val text = buildString {
            items.filter { it.optString("type") == "message" }.forEach { message ->
                val parts = message.optJSONArray("content") ?: JSONArray()
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    when (part.optString("type")) {
                        "output_text" -> append(part.optString("text"))
                        "refusal" -> append(part.optString("refusal"))
                    }
                    val annotations = part.optJSONArray("annotations") ?: JSONArray()
                    for (j in 0 until annotations.length()) {
                        val annotation = annotations.getJSONObject(j)
                        val url = annotation.optString("url")
                        if (annotation.optString("type") == "url_citation" &&
                            (url.startsWith("https://") || url.startsWith("http://"))) {
                            citations[url] = annotation.optString("title").ifBlank { url }
                        }
                    }
                }
            }
        }
        val rendered = buildString {
            append(text)
            if (citations.isNotEmpty()) {
                append("\n\n来源：\n")
                citations.entries.forEachIndexed { index, (url, title) ->
                    val label = title.replace(Regex("[\\[\\]\\r\\n]"), " ")
                    val target = url.replace(" ", "%20").replace("(", "%28").replace(")", "%29")
                        .replace("<", "%3C").replace(">", "%3E")
                    append("${index + 1}. [$label]($target)\n")
                }
            }
        }
        return Turn(rendered, items)
    }
}
