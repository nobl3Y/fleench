package com.example.wordexplainer

/**
 * Singleton bridge so ChatInputActivity can deliver typed text back to the
 * live ExplainService overlay card without needing a bound service or broadcast.
 * ExplainService sets [onMessageSent] when the popup is shown and clears it on close.
 */
object ChatBridge {
    @Volatile var onMessageSent: ((String) -> Unit)? = null
}
