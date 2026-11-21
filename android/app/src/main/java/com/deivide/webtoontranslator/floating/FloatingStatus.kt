package com.deivide.webtoontranslator.floating

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Se o tradutor flutuante está ligado (o serviço avisa; a tela inicial mostra Iniciar/Parar). */
class FloatingStatus {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    internal fun set(running: Boolean) {
        _running.value = running
    }
}
