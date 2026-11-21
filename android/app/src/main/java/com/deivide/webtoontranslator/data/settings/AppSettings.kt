package com.deivide.webtoontranslator.data.settings

/** Provedores de IA, com os mesmos ids do desktop. */
enum class Provider(val id: String, val label: String, val defaultBaseUrl: String) {
    GOOGLE("google", "Google", "https://generativelanguage.googleapis.com"),
    ANTHROPIC("anthropic", "Anthropic", "https://api.anthropic.com"),
    OPENAI("openai", "Compatível OpenAI", "http://192.168.1.10");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: GOOGLE
    }
}

data class AppSettings(
    val provider: Provider,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val speechSpeed: Float,
) {
    val isConfigured get() = apiKey.isNotBlank() && model.isNotBlank()

    companion object {
        /** Mesmo padrão do desktop: um pouco mais devagar que a fala normal. */
        const val DEFAULT_SPEECH_SPEED = 0.7f
        val SPEECH_SPEED_RANGE = 0.5f..1.5f
    }
}
