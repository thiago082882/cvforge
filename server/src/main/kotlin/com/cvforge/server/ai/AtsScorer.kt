package com.cvforge.server.ai

import java.text.Normalizer
import kotlin.math.roundToInt

data class AtsResult(val score: Int, val found: List<String>, val missing: List<String>)

/**
 * Score determinístico e auditável: % das palavras-chave da vaga que REALMENTE aparecem no texto do currículo.
 * (No projeto original o número vinha "chutado" pelo LLM, sem qualquer verificação.)
 * É uma heurística de aderência — não é o algoritmo de nenhum ATS específico.
 */
object AtsScorer {
    fun score(keywords: List<String>, cvText: String): AtsResult {
        val haystack = " ${normalize(cvText)} "
        val unique = keywords
            .map { it.trim() }
            .filter { it.length in 2..40 }
            .distinctBy { normalize(it) }
            .take(25)

        if (unique.isEmpty()) return AtsResult(0, emptyList(), emptyList())

        val (found, missing) = unique.partition { kw ->
            val n = normalize(kw)
            n.isNotEmpty() && haystack.contains(" $n ")
        }
        return AtsResult((found.size * 100.0 / unique.size).roundToInt().coerceIn(0, 100), found, missing)
    }

    /** minúsculas, sem acentos, pontuação → espaço (mantém + # . / para "c++", "c#", "node.js"). */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9+#./]+"), " ")
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { it.trimEnd('.', '/') }
}
