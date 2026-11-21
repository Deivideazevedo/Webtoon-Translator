package com.deivide.webtoontranslator.domain.model

/** Tradução completa de um balão, já com a pronúncia calculada localmente. */
data class Translation(
    val originalText: String,
    val translation: String,
    val pronunciationPt: String,
    val pronunciationIpa: String,
    val segments: List<Segment>,
    val grammarNotes: String,
    val gloss: List<Gloss>,
    val model: String,
    val durationSecs: Double,
)

/** Trecho destacado do original ("breakdown" no desktop). */
data class Segment(
    val text: String,
    val pronunciationPt: String,
    val pronunciationIpa: String,
    val meaning: String,
    val note: String,
)

/** Tradução de uma palavra do original (a glosa do hover no desktop). */
data class Gloss(val word: String, val meaning: String)

/** O que chega durante o streaming de uma tradução, na ordem em que fica pronto. */
sealed interface TranslationEvent {
    /** O original corrigido pelo modelo, com a pronúncia em PT-BR. */
    data class Original(val text: String, val pronunciation: String) : TranslationEvent

    data class Translated(val text: String) : TranslationEvent

    data class Done(val translation: Translation) : TranslationEvent
}
