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
        /**
         * A velocidade que o app mostra é relativa à fala "normal", que no motor de voz do Android
         * soa bem em 0,7 (o 1,0 do Android é corrido demais pra acompanhar a leitura). Então 1,0x
         * aqui = 0,7 no Android; [nativeRate] faz a conta na hora de falar.
         */
        const val NATIVE_BASE = 0.7f
        const val DEFAULT_SPEECH_SPEED = 1f
        /** 🐢: metade da fala normal. */
        const val SLOW_SPEECH_SPEED = 0.5f
        val SPEECH_SPEED_RANGE = 0.5f..1.5f

        /** Velocidade mostrada no app → a que o motor de voz do Android recebe. */
        fun nativeRate(speed: Float) = speed * NATIVE_BASE
    }
}
