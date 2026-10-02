package com.example.wordexplainer

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

object Ai {
    // ── Groq Engine ─────────────────────────────────────────────────────────
    val GROQ_KEY: String get() = BuildConfig.GROQ_API_KEY
    const val GROQ_MODEL = "openai/gpt-oss-20b"         // Primary — blazing fast
    const val GROQ_FALLBACK_MODEL = "openai/gpt-oss-120b"  // Fallback — bigger

    // ── Gemini Engine (Final fallback) ───────────────────────────────────────
    const val GEMINI_MODEL = "gemini-2.5-flash-lite"
    val GEMINI_KEYS: List<String> by lazy {
        val raw = BuildConfig.GEMINI_API_KEYS
        if (raw.isNotBlank()) raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        else emptyList()
    }

    private val geminiKeyIndex = AtomicInteger(0)

    // ── Groq Caller ──────────────────────────────────────────────────────────
    private fun callGroq(messages: JSONArray, maxTokens: Int = 400): String {
        for (m in listOf(GROQ_MODEL, GROQ_FALLBACK_MODEL)) {
            try {
                val body = JSONObject().apply {
                    put("model", m)
                    put("messages", messages)
                    put("max_tokens", maxTokens)
                    put("temperature", 0.3)
                }
                val conn = URL("https://api.groq.com/openai/v1/chat/completions")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Authorization", "Bearer $GROQ_KEY")
                conn.setRequestProperty("User-Agent", "FleenchApp/1.0 (Android; Mobile)")
                conn.connectTimeout = 8000
                conn.readTimeout = 15000
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""

                if (code in 200..299) {
                    val root = JSONObject(responseText)
                    val choices = root.getJSONArray("choices")
                    if (choices.length() > 0) {
                        return choices.getJSONObject(0).getJSONObject("message").getString("content").trim()
                    }
                }
            } catch (_: Exception) {}
        }
        throw Exception("Groq failed.")
    }

    // ── Gemini Caller (interactions API — multi-format response parser) ───────
    private fun callGemini(customKey: String?, inputText: String): String {
        val keysToTry = if (!customKey.isNullOrBlank()) {
            listOf(customKey.trim()) + GEMINI_KEYS
        } else {
            val start = Math.floorMod(geminiKeyIndex.get(), GEMINI_KEYS.size)
            (0 until GEMINI_KEYS.size).map { GEMINI_KEYS[(start + it) % GEMINI_KEYS.size] }
        }
        var lastError: Exception? = null
        for (k in keysToTry) {
            try {
                val body = JSONObject().apply {
                    put("model", GEMINI_MODEL)
                    put("input", inputText)
                }
                val conn = URL("https://generativelanguage.googleapis.com/v1beta/interactions?key=$k")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("x-goog-api-key", k)
                conn.connectTimeout = 10000
                conn.readTimeout = 20000
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""

                if (code in 200..299) {
                    val root = JSONObject(responseText)

                    val outputText = root.optString("output_text", "")
                    if (outputText.isNotBlank()) return outputText.trim()

                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val t = parts.getJSONObject(0).optString("text", "")
                            if (t.isNotBlank()) return t.trim()
                        }
                    }

                    // interactions API 2026 steps format
                    val steps = root.optJSONArray("steps")
                    if (steps != null) {
                        for (i in 0 until steps.length()) {
                            val step = steps.optJSONObject(i) ?: continue
                            if (step.optString("type") == "model_output") {
                                val contentArr = step.optJSONArray("content")
                                if (contentArr != null && contentArr.length() > 0) {
                                    val t = contentArr.getJSONObject(0).optString("text", "")
                                    if (t.isNotBlank()) return t.trim()
                                }
                            }
                        }
                    }
                    return "No explanation generated."
                } else {
                    geminiKeyIndex.incrementAndGet()
                    lastError = Exception("Gemini error ($code): ${responseText.take(100)}")
                }
            } catch (e: Exception) {
                geminiKeyIndex.incrementAndGet()
                lastError = e
            }
        }
        throw lastError ?: Exception("All Gemini keys exhausted.")
    }

    // ── Multi-turn Ask AI conversation ───────────────────────────────────────
    fun chat(customKey: String?, systemPrompt: String, history: List<Pair<String, String>>): String {
        // 1. Groq (fast)
        try {
            val messages = JSONArray()
            if (systemPrompt.isNotBlank()) {
                messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
            }
            for ((role, text) in history) {
                val r = if (role == "assistant" || role == "model") "assistant" else "user"
                messages.put(JSONObject().put("role", r).put("content", text))
            }
            return callGroq(messages, maxTokens = 600)
        } catch (_: Exception) {}

        // 2. Gemini fallback
        val input = buildString {
            if (systemPrompt.isNotBlank()) appendLine("[System]: $systemPrompt\n")
            for ((role, text) in history) {
                val label = if (role == "assistant" || role == "model") "Assistant" else "User"
                appendLine("$label: $text")
            }
        }
        return callGemini(customKey, input.trim())
    }

    // ── Single word / phrase definition — AI ONLY, no offline fallback ───────
    // Handles ALL word types: standard, slang, internet slang, neologisms,
    // informal language, abbreviations, names — never refuses to define.
    fun define(
        customKey: String?,
        word: String,
        contextSnippet: String = "",
        surroundingContext: String = ""
    ): String {
        val today = LocalDate.now().toString()
        val prompt = buildString {
            append("Define \"${word.trim()}\" for the user.\n")
            append("IMPORTANT: Always give a definition. The word may be:\n")
            append("- A standard English dictionary word\n")
            append("- Slang or informal language (e.g. 'larpy', 'slay', 'bussin', 'rizz')\n")
            append("- An internet/social media term (e.g. 'ratio', 'NPC', 'based', 'mid')\n")
            append("- A neologism, portmanteau, or made-up word\n")
            append("- A name, brand, acronym, or abbreviation\n")
            append("Never say 'I cannot define this' or 'this is not a standard word'. ")
            append("If it's slang or informal, explain what it means in that usage.\n")
            if (contextSnippet.isNotBlank()) {
                append("Context it appeared in: \"${contextSnippet.take(800).trim()}\"\n")
            }
            if (surroundingContext.isNotBlank() && surroundingContext != contextSnippet) {
                append("Surrounding context: \"${surroundingContext.take(1200).trim()}\"\n")
            }
            append("\nFormat:\n")
            append("**[pronunciation if applicable] • [part of speech / type]**\n")
            append("[Clear 1-2 sentence definition or explanation]\n\n")
            if (contextSnippet.isNotBlank()) append("**In context:** [what it means here specifically]\n\n")
            append("**Example:** [natural example sentence]\n")
            append("**Synonyms/Related:** [2-3 related words or phrases]\n\n")
            append("Wrap proper nouns, names, places in [[double brackets]] e.g. [[London]].")
        }

        // 1. Groq — fast
        try {
            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content",
                    "Today is $today. You are a comprehensive language assistant. You define ALL words — standard, slang, internet terms, informal, made-up — never refuse."))
                put(JSONObject().put("role", "user").put("content", prompt))
            }
            return callGroq(messages, maxTokens = 380)
        } catch (_: Exception) {}

        // 2. Gemini fallback
        try {
            return callGemini(customKey, "Today is $today.\n$prompt")
        } catch (_: Exception) {}

        // No offline dictionary
        return "Could not reach AI right now. Check your connection and try again."
    }

    // ── Multi-word / Phrase Explanation ─────────────────────────────────────
    fun explain(
        customKey: String?,
        systemPrompt: String,
        selectedText: String,
        contextBlock: String,
        surroundingContext: String = ""
    ): String {
        val prompt = buildString {
            append("The user highlighted: \"$selectedText\"\n")
            if (contextBlock.isNotBlank() && contextBlock != selectedText) {
                append("Immediate text: \"${contextBlock.take(1500).trim()}\"\n")
            }
            if (surroundingContext.isNotBlank() && surroundingContext != contextBlock) {
                append("\nSurrounding screen/conversation context:\n\"\"\"\n${surroundingContext.take(2500).trim()}\n\"\"\"\n")
            }
            append("\nExplain what this means in this context in 1-2 punchy, concise sentences.")
            append(" If this is a conversation, social media reply, tweet, or message, briefly identify who is replying or reacting to what.")
            append(" Keep it crisp, direct, and short; do not write long paragraphs.")
            append(" Wrap any proper nouns, names, or places in [[double brackets]] e.g. [[Twitter]], [[Feyi]].")
        }

        // 1. Groq
        try {
            val messages = JSONArray().apply {
                if (systemPrompt.isNotBlank()) {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                }
                put(JSONObject().put("role", "user").put("content", prompt))
            }
            return callGroq(messages, maxTokens = 400)
        } catch (_: Exception) {}

        // 2. Gemini fallback
        try {
            val geminiPrompt = buildString {
                if (systemPrompt.isNotBlank()) appendLine("[System]: $systemPrompt\n")
                append(prompt)
            }
            return callGemini(customKey, geminiPrompt.trim())
        } catch (_: Exception) {}

        return "Could not reach AI right now. Check your connection and try again."
    }
}
