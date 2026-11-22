package com.deivide.webtoontranslator.floating

/**
 * Comparação tolerante de textos do OCR, pros modos automáticos: o mesmo balão lido de novo
 * (rolagem leve, uma letra ou pontuação diferente, linha cortada na borda) não conta como texto
 * novo, então não chama a IA de novo.
 */
internal object AutoText {
    /** A partir dessa semelhança (0–1) é o mesmo texto. */
    private const val SAME_SIMILARITY = 0.85
    /** Texto novo contido no anterior (só perdeu uma linha cortada) e com pelo menos essa fração dele. */
    private const val MIN_CONTAINED_FRACTION = 0.6

    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** Minúsculas, só letras e números, espaços simples: some pontuação e quebra de linha. */
    fun normalize(text: String): String = text.lowercase().replace(NOT_WORD, " ").trim()

    /** [a] e [b] (já normalizados) são o mesmo texto lido de novo. */
    fun same(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.isEmpty() || b.isEmpty()) return false
        // O novo só perdeu um pedaço (linha que saiu pela borda): nada de novo pra traduzir.
        if (a.contains(b) && b.length >= a.length * MIN_CONTAINED_FRACTION) return true
        return similarity(a, b) >= SAME_SIMILARITY
    }

    /** Valor guardado com uma chave que é "o mesmo texto" que [key] (normalizada). */
    fun <V> find(cache: Map<String, V>, key: String): V? =
        cache[key] ?: cache.entries.firstOrNull { same(it.key, key) }?.value

    /** 1 − distância de edição / tamanho do maior (1 = iguais). */
    private fun similarity(a: String, b: String): Double {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            previous = current.also { current = previous }
        }
        return 1.0 - previous[b.length].toDouble() / maxOf(a.length, b.length)
    }
}
