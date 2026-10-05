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
    private fun callGroq(messages: JSONArray, maxTokens: Int = 500): String {
        for (m in listOf(GROQ_MODEL, GROQ_FALLBACK_MODEL)) {
            try {
                val body = JSONObject().apply {
                    put("model", m)
                    put("messages", messages)
                    put("max_tokens", maxTokens)
                    put("temperature", 0.3)
                    put("reasoning_effort", "low")
                }
                val conn = URL("https://api.groq.com/openai/v1/chat/completions")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Authorization", "Bearer $GROQ_KEY")
                conn.setRequestProperty("User-Agent", "FleenchApp/1.0 (Android; Mobile)")
                conn.connectTimeout = 6000
                conn.readTimeout = 10000
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

    // ── Gemini Caller (Standard generateContent API) ───────────────────────────
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
                val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent?key=$k")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.connectTimeout = 6000
                conn.readTimeout = 10000
                conn.doOutput = true

                val body = JSONObject().apply {
                    val contents = JSONArray().apply {
                        put(JSONObject().apply {
                            val parts = JSONArray().apply {
                                put(JSONObject().put("text", inputText))
                            }
                            put("parts", parts)
                        })
                    }
                    put("contents", contents)
                }

                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""

                if (code in 200..299) {
                    val root = JSONObject(responseText)
                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val cand = candidates.getJSONObject(0)
                        val content = cand.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val t = parts.getJSONObject(0).optString("text", "")
                            if (t.isNotBlank()) return t.trim()
                        }
                    }
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
            append("IMPORTANT: Always give a definition. The word may be a standard word, slang, internet term, abbreviation, brand, or name — never refuse.\n")
            if (contextSnippet.isNotBlank()) {
                append("Context: \"${contextSnippet.take(600).trim()}\"\n")
            }
            if (surroundingContext.isNotBlank() && surroundingContext != contextSnippet) {
                append("Surrounding text: \"${surroundingContext.take(800).trim()}\"\n")
            }
            append("\nFormat concisely:\n")
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
                    "Today is $today. You are a fast, comprehensive language assistant. You define ALL words concisely — never refuse."))
                put(JSONObject().put("role", "user").put("content", prompt))
            }
            return callGroq(messages, maxTokens = 500)
        } catch (_: Exception) {}

        // 2. Gemini fallback
        try {
            return callGemini(customKey, "Today is $today.\n$prompt")
        } catch (_: Exception) {}

        // No offline dictionary
        return "Fleench is currently not fleenching right now. Check your connection and try again."
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
            return callGroq(messages, maxTokens = 500)
        } catch (_: Exception) {}

        // 2. Gemini fallback
        try {
            val geminiPrompt = buildString {
                if (systemPrompt.isNotBlank()) appendLine("[System]: $systemPrompt\n")
                append(prompt)
            }
            return callGemini(customKey, geminiPrompt.trim())
        } catch (_: Exception) {}

        return "Fleench is currently not fleenching right now. Check your connection and try again."
    }
}
