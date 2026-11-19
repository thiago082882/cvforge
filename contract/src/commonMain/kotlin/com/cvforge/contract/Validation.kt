package com.cvforge.contract

/** Limites de tamanho — aplicados no servidor e usados pelos apps para feedback imediato. */
object Limits {
    const val NAME = 100
    const val SHORT_FIELD = 200
    const val SUMMARY = 1500
    const val DESCRIPTION = 2000
    const val JOB_TEXT = 12_000
    const val MAX_EXPERIENCES = 15
    const val MAX_EDUCATION = 10
    const val MAX_SKILLS = 60
    const val MIN_PASSWORD = 8
}

/** Cada função devolve a mensagem de erro (pt-BR) ou null se o valor é válido. */
object Validators {
    private val EMAIL = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    fun name(value: String): String? = when {
        value.isBlank() -> "Informe seu nome"
        value.trim().length > Limits.NAME -> "Nome muito longo"
        else -> null
    }

    fun email(value: String): String? =
        if (EMAIL.matches(value.trim()) && value.length <= 254) null else "E-mail inválido"

    fun password(value: String): String? =
        if (value.length >= Limits.MIN_PASSWORD) null
        else "A senha deve ter ao menos ${Limits.MIN_PASSWORD} caracteres"

    fun jobUrl(value: String): String? {
        val v = value.trim()
        return if (v.startsWith("https://") || v.startsWith("http://")) null
        else "Use um link completo, começando com https://"
    }

    /** Valida o perfil inteiro (limites de tamanho e de quantidade). */
    fun profile(p: ProfileDto): String? {
        if (p.summary.length > Limits.SUMMARY) return "O resumo passou de ${Limits.SUMMARY} caracteres"
        if (p.experiences.size > Limits.MAX_EXPERIENCES) return "Máximo de ${Limits.MAX_EXPERIENCES} experiências"
        if (p.education.size > Limits.MAX_EDUCATION) return "Máximo de ${Limits.MAX_EDUCATION} formações"
        if (p.skills.size > Limits.MAX_SKILLS) return "Máximo de ${Limits.MAX_SKILLS} habilidades"
        for (e in p.experiences) {
            if (e.company.isBlank() || e.role.isBlank()) return "Preencha empresa e cargo em todas as experiências"
            if (e.startDate.isBlank()) return "Informe a data de início de \"${e.role}\""
            if (e.description.length > Limits.DESCRIPTION) return "A descrição de \"${e.role}\" é muito longa"
        }
        for (e in p.education) {
            if (e.institution.isBlank() || e.degree.isBlank()) return "Preencha instituição e curso em todas as formações"
            val y = e.graduationYear
            if (y != null && y !in 1950..2100) return "Ano de conclusão inválido: $y"
        }
        val tooLong = listOfNotNull(p.city, p.phone, p.linkedinUrl, p.website).any { it.length > Limits.SHORT_FIELD }
        if (tooLong) return "Algum campo de contato está longo demais"
        return null
    }

    /** O perfil tem conteúdo suficiente para gerar um currículo? */
    fun profileHasContent(p: ProfileDto): Boolean =
        p.experiences.isNotEmpty() || p.education.isNotEmpty() || p.skills.isNotEmpty()
}
