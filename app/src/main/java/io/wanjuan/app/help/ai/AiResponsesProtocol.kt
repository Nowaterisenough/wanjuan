package io.wanjuan.app.help.ai

import io.wanjuan.app.ui.main.ai.AiChatException
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

    private fun baseRequest(model: String) = JSONObject()
        .put("model", model).put("stream", true).put("store", false)

    fun seriesRequest(model: String, instructions: String, shelf: String): String {
        val text = JSONObject().put("type", "input_text").put("text", shelf)
        val message = JSONObject().put("role", "user").put("content", JSONArray().put(text))
        return baseRequest(model).put("instructions", instructions).put("input", JSONArray().put(message)).toString()
    }

    fun request(model: String, input: List<JSONObject>, tools: List<JSONObject>, webSearch: Boolean): String =
        baseRequest(model).apply {
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

    fun httpError(status: Int, body: String, log: String, secrets: Collection<String>): AiChatException {
        val text = body.trim()
        if (looksLikeEvents(text)) return streamError(status, text.reader().buffered(), log, secrets)
        val root = runCatching { JSONObject(text) }.getOrNull()
        val plain = text.lineSequence().firstOrNull()?.takeIf {
            root == null && !it.startsWith('<') && !it.startsWith('{') && !it.startsWith('[')
        }
        return providerError(status, root, plain, log, secrets)
    }

    fun looksLikeEvents(prefix: String): Boolean {
        val first = prefix.removePrefix("\uFEFF").trimStart()
        return listOf("event:", "data:", ":", "id:", "retry:").any(first::startsWith)
    }

    private val failureTypes = setOf("response.failed", "response.incomplete", "error")

    fun streamError(status: Int, reader: BufferedReader, log: String, secrets: Collection<String>): AiChatException {
        for ((name, data) in events(reader)) {
            if (data == "[DONE]") break
            val event = runCatching { JSONObject(data) }.getOrNull()
            if (name in failureTypes || event?.optString("type") in failureTypes || (event != null && !event.isNull("error")) ||
                event?.optJSONObject("response")?.optJSONObject("error") != null) {
                return providerError(status, event, null, log, secrets)
            }
        }
        return providerError(status, null, null, log, secrets)
    }

    private fun providerError(status: Int?, root: JSONObject?, plain: String?, log: String, secrets: Collection<String>): AiChatException {
        val response = root?.optJSONObject("response") ?: root
        val error = response?.optJSONObject("error") ?: root?.optJSONObject("error") ?: response
        val detail = error?.opt("detail") ?: root?.opt("detail")
        val code = error?.opt("code") as? String
        val param = error?.opt("param") as? String
        val reason = listOfNotNull(error?.opt("message") as? String, root?.opt("error") as? String,
            response?.opt("error") as? String, detail as? String, (detail as? JSONObject)?.opt("message") as? String,
            (detail as? JSONArray)?.optJSONObject(0)?.opt("msg") as? String,
            response?.optJSONObject("incomplete_details")?.opt("reason") as? String, plain
        ).firstOrNull { it.isNotBlank() }.orEmpty()
        val lengthError = code in setOf("context_length_exceeded", "input_too_long") ||
            reason == "max_output_tokens" ||
                listOf("maximum context length", "context length exceeded", "input is too long", "too many input tokens")
                .any { reason.contains(it, ignoreCase = true) }
        val tooLarge = status == 413 || (lengthError && (status == null || status == 400 || status == 422))
        var explanation = listOf(reason, param.orEmpty(), code.orEmpty()).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        for (secret in secrets.filter(String::isNotBlank).sortedByDescending { it.length }) {
            explanation = explanation.replace(secret, "***")
        }
        if (explanation.isBlank() && status == 400) explanation = "服务端未提供原因，请核对接口地址和所选模型是否支持 Responses。"
        if (explanation.isBlank() && status == null) explanation = "服务端未提供具体原因"
        val heading = status?.let { "HTTP $it" } ?: "Responses 请求失败"
        val message = listOf(heading, explanation.take(500)).filter(String::isNotBlank).joinToString(" · ")
        return AiChatException(message, log + (status?.let { "status=$it" } ?: "stream failed"), inputTooLarge = tooLarge)
    }

    fun toolResult(callId: String, output: String) = JSONObject()
        .put("type", "function_call_output").put("call_id", callId).put("output", output)

    data class Turn(val content: String, val output: List<JSONObject>)

    /** Read each SSE frame once. Some gateways put its type only on the event: line. */
    private fun events(reader: BufferedReader, checkCancelled: () -> Unit = {}): Sequence<Pair<String, String>> = sequence {
        var name = ""
        val data = StringBuilder()
        while (true) {
            checkCancelled()
            val line = reader.readLine()?.removePrefix("\uFEFF")
            when {
                line.isNullOrEmpty() -> {
                    if (data.isNotEmpty()) yield(name to data.toString())
                    if (line == null) break
                    data.clear()
                    name = ""
                }
                line.startsWith("event:") -> name = line.substringAfter(':').trim()
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.substringAfter(':').removePrefix(" "))
                }
            }
        }
    }

    fun read(
        reader: BufferedReader,
        isEventStream: Boolean,
        onPartial: (String) -> Unit,
        onThinking: (String) -> Unit,
        checkCancelled: () -> Unit = {},
        secrets: Collection<String> = emptyList()
    ): Turn {
        if (!isEventStream) return completed(JSONObject(reader.readText()), secrets).also { onPartial(it.content) }
        val text = StringBuilder()
        for ((name, payload) in events(reader, checkCancelled)) {
            if (payload == "[DONE]") break
            if (name in failureTypes) {
                throw providerError(null, runCatching { JSONObject(payload) }.getOrNull(), null, "", secrets)
            }
            val event = JSONObject(payload)
            val type = event.optString("type").ifBlank { name }
            when (type) {
                "response.output_text.delta", "response.refusal.delta" -> {
                    text.append(event.optString("delta"))
                    onPartial(text.toString())
                }
                "response.reasoning_summary_text.delta" -> onThinking(event.optString("delta"))
                "response.completed" -> return completed(event.getJSONObject("response"), secrets).also { onPartial(it.content) }
                in failureTypes -> throw providerError(null, event, null, "", secrets)
            }
        }
        error("Responses stream ended before response.completed; please retry")
    }

    private fun completed(response: JSONObject, secrets: Collection<String>): Turn {
        if (response.optString("status") != "completed") throw providerError(null, response, null, "", secrets)
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
