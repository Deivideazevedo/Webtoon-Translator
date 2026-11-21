package com.deivide.webtoontranslator.di

import android.content.Context
import com.deivide.webtoontranslator.data.engine.EngineProvider
import com.deivide.webtoontranslator.data.library.LibraryRepository
import com.deivide.webtoontranslator.data.settings.ReadingPreferences
import com.deivide.webtoontranslator.data.settings.SettingsRepository
import com.deivide.webtoontranslator.data.speech.SpeechPlayer
import com.deivide.webtoontranslator.data.translation.TranslationRepository
import com.deivide.webtoontranslator.floating.FloatingStatus
import com.deivide.webtoontranslator.floating.RegionTranslator
import com.deivide.webtoontranslator.ui.translation.TranslationActions

/**
 * Injeção de dependências manual: uma instância de cada serviço pro app inteiro. Os ViewModels
 * recebem só o que usam (ver as factories em cada tela).
 */
class AppContainer(context: Context) {
    private val engineProvider = EngineProvider(context)

    val settingsRepository = SettingsRepository(context)
    val readingPreferences = ReadingPreferences(context)
    val translationRepository = TranslationRepository(engineProvider)
    val libraryRepository = LibraryRepository(context)
    val speechPlayer = SpeechPlayer(context)
    val translationActions = TranslationActions(speechPlayer, settingsRepository, libraryRepository, readingPreferences)
    val regionTranslator = RegionTranslator(translationRepository, settingsRepository, libraryRepository)
    val floatingStatus = FloatingStatus()

    /** Começa a carregar o motor Rust em segundo plano (OCR pronto antes da primeira seleção). */
    suspend fun warmUp() {
        engineProvider.get()
    }
}
