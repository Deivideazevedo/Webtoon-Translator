package com.deivide.webtoontranslator.data.library

import android.content.Context
import com.deivide.webtoontranslator.data.translation.toBridge
import com.deivide.webtoontranslator.data.translation.toDomain
import com.deivide.webtoontranslator.domain.model.Translation
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import uniffi.webtoon_android.TranslationLibrary

/** As duas listas de traduções salvas. */
enum class SavedList(val id: String) {
    RECENT("recent"),
    FAVORITES("favorites"),
}

data class Library(
    val recent: List<Translation> = emptyList(),
    val favorites: List<Translation> = emptyList(),
    val recentBytes: Long = 0,
    val favoritesBytes: Long = 0,
    val recentMax: Int = 20,
) {
    val favoriteIds: Set<String> = favorites.mapTo(HashSet()) { it.id }

    fun list(which: SavedList) = if (which == SavedList.RECENT) recent else favorites
}

/**
 * Recentes e favoritas, guardadas pelo núcleo Rust (mesmos arquivos e formato do desktop).
 * [library] é atualizado a cada mudança, então as telas só observam.
 */
class LibraryRepository(context: Context) {
    private val native = TranslationLibrary(File(context.filesDir, "library").path)

    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val usage = native.usage()
        _library.value = Library(
            recent = native.recent().map { it.toDomain() },
            favorites = native.favorites().map { it.toDomain() },
            recentBytes = usage.recentBytes.toLong(),
            favoritesBytes = usage.favoritesBytes.toLong(),
            recentMax = usage.recentMax.toInt(),
        )
    }

    suspend fun addRecent(translation: Translation) = change { native.addRecent(translation.toBridge()) }

    suspend fun setFavorite(translation: Translation, favorite: Boolean) =
        change { native.setFavorite(translation.toBridge(), favorite) }

    suspend fun delete(list: SavedList, ids: Collection<String>) = change { native.delete(list.id, ids.toList()) }

    private suspend fun change(action: () -> Unit) {
        withContext(Dispatchers.IO) { action() }
        refresh()
    }
}
