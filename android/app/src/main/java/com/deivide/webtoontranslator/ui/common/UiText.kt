package com.deivide.webtoontranslator.ui.common

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import uniffi.webtoon_android.BridgeException

/**
 * Texto que os ViewModels mandam pra tela sem depender de Context: um recurso de string (traduzível)
 * ou um texto pronto (ex.: mensagem de erro vinda do servidor).
 */
sealed interface UiText {
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText
    data class Raw(val text: String) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Resource -> stringResource(id, *args.toTypedArray())
    is UiText.Plural -> pluralStringResource(id, count, *args.toTypedArray())
    is UiText.Raw -> text
}

/** Versão fora do Compose (ex.: avisos das janelas flutuantes). */
fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Resource -> context.getString(id, *args.toTypedArray())
    is UiText.Plural -> context.resources.getQuantityString(id, count, *args.toTypedArray())
    is UiText.Raw -> text
}

/** Mensagem legível de uma falha (as do Rust trazem o motivo em `reason`). */
fun Throwable.friendlyMessage(): String = when (this) {
    is BridgeException.Failed -> reason
    else -> message ?: toString()
}

fun Throwable.toUiText(): UiText = UiText.Raw(friendlyMessage())
