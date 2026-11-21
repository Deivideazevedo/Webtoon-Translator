package com.deivide.webtoontranslator.data.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LearnTab { SEGMENTS, GRAMMAR }

/** Como o cartão de tradução aparece (lembrado entre traduções, como no balão do desktop). */
data class ReadingOptions(
    /** Pronúncia em IPA em vez da grafia PT-BR. */
    val showIpa: Boolean = false,
    /** Seção "📖 Aprender" aberta. */
    val learnOpen: Boolean = false,
    val learnTab: LearnTab = LearnTab.SEGMENTS,
)

class ReadingPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("reading", Context.MODE_PRIVATE)

    private val _options = MutableStateFlow(
        ReadingOptions(
            showIpa = prefs.getBoolean(KEY_SHOW_IPA, false),
            learnOpen = prefs.getBoolean(KEY_LEARN_OPEN, false),
            learnTab = LearnTab.entries.getOrElse(prefs.getInt(KEY_LEARN_TAB, 0)) { LearnTab.SEGMENTS },
        )
    )
    val options: StateFlow<ReadingOptions> = _options.asStateFlow()

    fun update(change: (ReadingOptions) -> ReadingOptions) {
        _options.update(change)
        val o = _options.value
        prefs.edit {
            putBoolean(KEY_SHOW_IPA, o.showIpa)
            putBoolean(KEY_LEARN_OPEN, o.learnOpen)
            putInt(KEY_LEARN_TAB, o.learnTab.ordinal)
        }
    }

    private companion object {
        const val KEY_SHOW_IPA = "show_ipa"
        const val KEY_LEARN_OPEN = "learn_open"
        const val KEY_LEARN_TAB = "learn_tab"
    }
}
