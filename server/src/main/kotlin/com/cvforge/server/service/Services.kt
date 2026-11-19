package com.cvforge.server.service

import com.cvforge.contract.CurriculumDto
import com.cvforge.contract.CurriculumSummaryDto
import com.cvforge.contract.CvTemplate
import com.cvforge.contract.ErrorDto
import com.cvforge.contract.GenerateRequest
import com.cvforge.contract.Limits
import com.cvforge.contract.ProfileDto
import com.cvforge.contract.Validators
import com.cvforge.server.ai.CvGenerator
import com.cvforge.server.ai.JobScraper
import com.cvforge.server.config.AppException
import com.cvforge.server.db.CurriculumRepository
import com.cvforge.server.db.ProfileRepository
import com.cvforge.server.db.UserRepository
import com.cvforge.server.pdf.PdfRenderer
import com.cvforge.server.pdf.TextRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.UUID

class ProfileService(private val profiles: ProfileRepository) {
    suspend fun get(userId: UUID): ProfileDto =
        profiles.find(userId) ?: throw AppException.NotFound("Perfil ainda não preenchido")

    suspend fun save(userId: UUID, input: ProfileDto): ProfileDto {
        val clean = input.copy(
            city = input.city.trim(),
            phone = input.phone?.trim()?.ifBlank { null },
            linkedinUrl = input.linkedinUrl?.trim()?.ifBlank { null },
            website = input.website?.trim()?.ifBlank { null },
            summary = input.summary.trim(),
            experiences = input.experiences.map { e ->
                e.copy(
                    company = e.company.trim(), role = e.role.trim(), startDate = e.startDate.trim(),
                    endDate = e.endDate?.trim()?.ifBlank { null }, description = e.description.trim(),
                    skills = e.skills.map { it.trim() }.filter { it.isNotEmpty() },
                )
            },
            education = input.education.map { it.copy(institution = it.institution.trim(), degree = it.degree.trim(), fieldOfStudy = it.fieldOfStudy.trim()) },
            skills = input.skills.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() },
        )
        Validators.profile(clean)?.let { throw AppException.BadRequest(it) }
        profiles.upsert(userId, clean)
        return clean
    }
}

class ExportFile(val bytes: ByteArray, val contentType: String, val fileName: String)

class CurriculumService(
    private val users: UserRepository,
    private val profiles: ProfileRepository,
    private val curriculums: CurriculumRepository,
    private val scraper: JobScraper,
    private val generator: CvGenerator,
    private val pdf: PdfRenderer,
) {
    suspend fun generate(userId: UUID, req: GenerateRequest): CurriculumDto {
        val text = req.jobText?.trim().orEmpty()
        val url = req.jobUrl?.trim().orEmpty()
        if (text.isEmpty() && url.isEmpty()) throw AppException.BadRequest("Informe o link da vaga ou cole a descrição dela")
        if (text.length > Limits.JOB_TEXT) throw AppException.BadRequest("A descrição da vaga é grande demais (máx. ${Limits.JOB_TEXT} caracteres)")
        if (text.isEmpty()) Validators.jobUrl(url)?.let { throw AppException.BadRequest(it) }

        val user = users.findById(userId) ?: throw AppException.NotFound("Usuário não encontrado")
        val profile = profiles.find(userId)
        if (profile == null || !Validators.profileHasContent(profile)) {
            throw AppException.BadRequest(
                "Complete seu perfil (experiências, formação ou habilidades) antes de gerar um currículo.",
                ErrorDto.PROFILE_INCOMPLETE,
            )
        }

        // Texto colado pelo usuário é mais confiável do que scraping — tem prioridade.
        val job = if (text.isNotEmpty()) scraper.fromText(text) else scraper.fetch(url)
        val cv = generator.generate(user, profile, job, req.language)
        return curriculums.save(userId, cv.copy(coverLetter = null))
            .also { jobTexts.put(cv.id, job.text) }
    }

    suspend fun list(userId: UUID): List<CurriculumSummaryDto> = curriculums.listSummaries(userId)

    suspend fun get(userId: UUID, id: UUID): CurriculumDto =
        curriculums.find(id, userId) ?: throw AppException.NotFound("Currículo não encontrado")

    suspend fun delete(userId: UUID, id: UUID) {
        if (!curriculums.delete(id, userId)) throw AppException.NotFound("Currículo não encontrado")
    }

    suspend fun coverLetter(userId: UUID, id: UUID): CurriculumDto {
        val cv = get(userId, id)
        val letter = generator.coverLetter(cv, jobTexts[cv.id])
        curriculums.setCoverLetter(id, userId, letter)
        return cv.copy(coverLetter = letter)
    }

    suspend fun export(userId: UUID, id: UUID, format: String, template: CvTemplate): ExportFile {
        val cv = get(userId, id)
        val base = "cv-${slug(cv.content.name)}-${slug(cv.company)}"
        return when (format.lowercase()) {
            "pdf" -> ExportFile(withContext(Dispatchers.Default) { pdf.render(cv, template) }, "application/pdf", "$base.pdf")
            "txt" -> ExportFile(TextRenderer.render(cv).toByteArray(Charsets.UTF_8), "text/plain; charset=utf-8", "$base.txt")
            "letter" -> {
                val letter = cv.coverLetter ?: throw AppException.NotFound("Gere a carta de apresentação primeiro")
                ExportFile(letter.toByteArray(Charsets.UTF_8), "text/plain; charset=utf-8", "carta-${slug(cv.content.name)}-${slug(cv.company)}.txt")
            }
            else -> throw AppException.BadRequest("Formato inválido. Use pdf, txt ou letter.")
        }
    }

    private fun slug(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(30)
            .ifEmpty { "x" }

    /**
     * Cache pequeno (em memória) do texto da vaga para a carta de apresentação.
     * Se o servidor reiniciar, a carta é gerada só com os dados do currículo — continua funcionando.
     */
    private val jobTexts = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 200
    }.let { java.util.Collections.synchronizedMap(it) }
}
