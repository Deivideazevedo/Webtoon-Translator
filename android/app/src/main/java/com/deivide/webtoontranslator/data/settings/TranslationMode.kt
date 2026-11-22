package com.deivide.webtoontranslator.data.settings

/** Como o tradutor flutuante escolhe o que traduzir (ver LAYOUT.md §5.7). */
enum class TranslationMode(val id: String) {
    /** Toca no ícone e arrasta a setinha sobre o balão. */
    REGION("region"),

    /** Área marcada uma vez; cada toque no ícone traduz de novo a mesma área. */
    FIXED_REGION("fixed_region"),

    /** Um toque traduz todos os balões da tela; tocar num deles abre a tradução completa. */
    FULL_SCREEN("full_screen"),

    /** Área marcada uma vez, traduzida sozinha quando o conteúdo dela muda (cartão fora da área). */
    AUTO_REGION("auto_region"),

    /** Tela cheia de novo, sozinha, sempre que a rolagem para. */
    AUTO_FULL_SCREEN("auto_full_screen");

    /** Usa a área marcada (editor com alças nos cantos). */
    val usesFixedRegion get() = this == FIXED_REGION || this == AUTO_REGION

    val isAutomatic get() = this == AUTO_REGION || this == AUTO_FULL_SCREEN

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: REGION
    }
}
