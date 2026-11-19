package com.cvforge.contract

import kotlinx.serialization.Serializable

/** Contrato HTTP compartilhado entre servidor e todos os apps (Android, iOS, Desktop, Web). */

@Serializable
enum class CvLanguage { PT_BR, EN }

@Serializable
enum class CvTemplate { CLASSIC, MODERN, COMPACT }

@Serializable
data class ErrorDto(val message: String, val code: String? = null) {
    companion object {
        const val PROFILE_INCOMPLETE = "PROFILE_INCOMPLETE"
    }
}

// ── Auth ─────────────────────────────────────────────────────────────────────
@Serializable data class RegisterRequest(val name: String, val email: String, val password: String)
@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class UserDto(val id: String, val name: String, val email: String)
@Serializable data class AuthResponse(val token: String, val user: UserDto)

// ── Perfil ───────────────────────────────────────────────────────────────────
@Serializable
data class ExperienceDto(
    val company: String,
    val role: String,
    val startDate: String,
    /** null = emprego atual */
    val endDate: String? = null,
    val description: String = "",
    val skills: List<String> = emptyList(),
)

@Serializable
data class EducationDto(
    val institution: String,
    val degree: String,
    val fieldOfStudy: String = "",
    /** null = em andamento */
    val graduationYear: Int? = null,
)

@Serializable
data class ProfileDto(
    val city: String = "",
    val phone: String? = null,
    val linkedinUrl: String? = null,
    val website: String? = null,
    val summary: String = "",
    val experiences: List<ExperienceDto> = emptyList(),
    val education: List<EducationDto> = emptyList(),
    val skills: List<String> = emptyList(),
)

// ── Currículos ───────────────────────────────────────────────────────────────
@Serializable
data class GenerateRequest(
    val jobUrl: String? = null,
    val jobText: String? = null,
    val language: CvLanguage = CvLanguage.PT_BR,
)

@Serializable
data class GeneratedExperienceDto(
    val company: String,
    val role: String,
    val period: String,
    val bullets: List<String>,
)

@Serializable
data class CvContentDto(
    val name: String,
    val email: String,
    val phone: String? = null,
    val city: String = "",
    val linkedin: String? = null,
    val website: String? = null,
    val summary: String,
    val experiences: List<GeneratedExperienceDto>,
    val education: List<EducationDto>,
    val skills: List<String>,
)

@Serializable
data class CurriculumDto(
    val id: String,
    val jobTitle: String,
    val company: String,
    val language: CvLanguage,
    val atsScore: Int,
    val keywordsFound: List<String>,
    val keywordsMissing: List<String>,
    val content: CvContentDto,
    val coverLetter: String? = null,
    val createdAt: String,
)

@Serializable
data class CurriculumSummaryDto(
    val id: String,
    val jobTitle: String,
    val company: String,
    val atsScore: Int,
    val createdAt: String,
)

@Serializable
data class HealthDto(val status: String)
