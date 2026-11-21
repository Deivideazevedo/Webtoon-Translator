package com.deivide.webtoontranslator.ui.home

import android.graphics.Rect
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.WebtoonApp
import com.deivide.webtoontranslator.data.image.PageLoader
import com.deivide.webtoontranslator.data.settings.SettingsRepository
import com.deivide.webtoontranslator.data.speech.SpeechPlayer
import com.deivide.webtoontranslator.data.translation.TranslationRepository
import com.deivide.webtoontranslator.domain.model.TranslationEvent
import com.deivide.webtoontranslator.ui.common.UiText
import com.deivide.webtoontranslator.ui.common.friendlyMessage
import com.deivide.webtoontranslator.ui.common.toUiText
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val translations: TranslationRepository,
    private val settings: SettingsRepository,
    private val pageLoader: PageLoader,
    private val speech: SpeechPlayer,
    warmUp: suspend () -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var translationJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { warmUp() }.onFailure { notify(it.toUiText()) }
            _uiState.update { it.copy(engineReady = true) }
        }
    }

    fun openImage(uri: Uri) {
        viewModelScope.launch {
            runCatching { pageLoader.load(uri) }
                .onSuccess { page -> _uiState.update { it.copy(page = page, result = null) } }
                .onFailure { notify(UiText.Resource(R.string.error_open_image, listOf(it.friendlyMessage()))) }
        }
    }

    /** OCR da área (em pixels da página) e tradução em streaming. */
    fun translateArea(area: Rect) {
        val page = _uiState.value.page ?: return
        val current = settings.load()
        if (!current.isConfigured) {
            setResult(ResultState.Failed(UiText.Resource(R.string.error_not_configured)))
            return
        }
        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            setResult(ResultState.Reading)
            val text = try {
                translations.recognize(withContext(Dispatchers.Default) { page.crop(area) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setResult(ResultState.Failed(e.toUiText()))
                return@launch
            }
            if (text.isBlank()) {
                setResult(ResultState.Failed(UiText.Resource(R.string.error_no_text)))
                return@launch
            }
            setResult(ResultState.Streaming(original = text))
            translations.translate(text, current)
                .catch { setResult(ResultState.Failed(it.toUiText())) }
                .collect { event -> _uiState.update { it.copy(result = it.result.apply(event)) } }
        }
    }

    fun closeResult() {
        translationJob?.cancel()
        setResult(null)
    }

    fun speak(text: String, slow: Boolean) {
        val rate = if (slow) SLOW_RATE else settings.load().speechSpeed
        when (speech.speak(text, rate)) {
            SpeechPlayer.Result.Spoken -> Unit
            SpeechPlayer.Result.NotReady -> notify(UiText.Resource(R.string.speech_not_ready))
            SpeechPlayer.Result.MissingEnglishVoice -> notify(UiText.Resource(R.string.speech_missing_voice))
        }
    }

    fun dismissNotice() = _uiState.update { it.copy(notice = null) }

    private fun setResult(result: ResultState?) = _uiState.update { it.copy(result = result) }

    private fun notify(message: UiText) = _uiState.update { it.copy(notice = message) }

    companion object {
        /** Velocidade do 🐢: absoluta, como no desktop. */
        private const val SLOW_RATE = 0.5f

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as WebtoonApp).container
                HomeViewModel(
                    translations = container.translationRepository,
                    settings = container.settingsRepository,
                    pageLoader = container.pageLoader,
                    speech = container.speechPlayer,
                    warmUp = container::warmUp,
                )
            }
        }
    }
}

/** Aplica um evento do streaming ao cartão (ignorado se o cartão foi fechado nesse meio-tempo). */
private fun ResultState?.apply(event: TranslationEvent): ResultState? = when (event) {
    is TranslationEvent.Done -> if (this == null) null else ResultState.Done(event.translation)
    is TranslationEvent.Original ->
        (this as? ResultState.Streaming)?.copy(original = event.text, pronunciation = event.pronunciation) ?: this
    is TranslationEvent.Translated -> (this as? ResultState.Streaming)?.copy(translation = event.text) ?: this
}
