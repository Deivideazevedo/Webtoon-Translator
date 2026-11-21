package com.deivide.webtoontranslator.data.translation

import com.deivide.webtoontranslator.domain.model.Gloss
import com.deivide.webtoontranslator.domain.model.Segment
import com.deivide.webtoontranslator.domain.model.Translation
import uniffi.webtoon_android.Gloss as BridgeGloss
import uniffi.webtoon_android.Segment as BridgeSegment
import uniffi.webtoon_android.Translation as BridgeTranslation

// Conversão entre os tipos gerados pelo UniFFI e os do app: só a camada `data` conhece os primeiros.

internal fun BridgeTranslation.toDomain() = Translation(
    id = id,
    originalText = originalText,
    translation = translation,
    pronunciationPt = pronunciationPt,
    pronunciationIpa = pronunciationIpa,
    segments = segments.map { Segment(it.text, it.pronunciationPt, it.pronunciationIpa, it.meaning, it.note) },
    grammarNotes = grammarNotes,
    gloss = gloss.map { Gloss(it.word, it.meaning) },
    model = model,
    durationSecs = durationSecs,
)

internal fun Translation.toBridge() = BridgeTranslation(
    id = id,
    originalText = originalText,
    translation = translation,
    pronunciationPt = pronunciationPt,
    pronunciationIpa = pronunciationIpa,
    segments = segments.map { BridgeSegment(it.text, it.pronunciationPt, it.pronunciationIpa, it.meaning, it.note) },
    grammarNotes = grammarNotes,
    gloss = gloss.map { BridgeGloss(it.word, it.meaning) },
    model = model,
    durationSecs = durationSecs,
)
