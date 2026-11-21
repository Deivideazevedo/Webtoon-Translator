package com.deivide.webtoontranslator.data.settings

/** Aparência do app: segue o sistema por padrão. */
enum class ThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}
