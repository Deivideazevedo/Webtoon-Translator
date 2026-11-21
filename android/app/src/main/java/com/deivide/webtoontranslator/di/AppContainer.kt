package com.deivide.webtoontranslator.di

import android.content.Context
import com.deivide.webtoontranslator.data.engine.EngineProvider
import com.deivide.webtoontranslator.data.image.PageLoader
import com.deivide.webtoontranslator.data.settings.SettingsRepository
import com.deivide.webtoontranslator.data.speech.SpeechPlayer
import com.deivide.webtoontranslator.data.translation.TranslationRepository

/**
 * Injeção de dependências manual: uma instância de cada serviço pro app inteiro. Os ViewModels
 * recebem só o que usam (ver as factories em cada tela).
 */
class AppContainer(context: Context) {
    private val engineProvider = EngineProvider(context)

    val settingsRepository = SettingsRepository(context)
    val translationRepository = TranslationRepository(engineProvider)
    val pageLoader = PageLoader(context)
    val speechPlayer = SpeechPlayer(context)

    /** Começa a carregar o motor Rust em segundo plano (OCR pronto antes da primeira seleção). */
    suspend fun warmUp() {
        engineProvider.get()
    }
}
