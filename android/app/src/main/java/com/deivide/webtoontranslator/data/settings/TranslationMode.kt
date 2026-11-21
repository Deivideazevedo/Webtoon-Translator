package com.deivide.webtoontranslator.data.settings

/** Como o tradutor flutuante escolhe o que traduzir (ver LAYOUT.md §5.7). */
enum class TranslationMode(val id: String) {
    /** Toca no ícone e arrasta a setinha sobre o balão. */
    REGION("region"),

    /** Área marcada uma vez; cada toque no ícone traduz de novo a mesma área. */
    FIXED_REGION("fixed_region");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: REGION
    }
}
