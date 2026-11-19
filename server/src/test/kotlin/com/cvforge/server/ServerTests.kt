package com.cvforge.server

import com.cvforge.contract.CurriculumDto
import com.cvforge.contract.CvContentDto
import com.cvforge.contract.CvLanguage
import com.cvforge.contract.CvTemplate
import com.cvforge.contract.EducationDto
import com.cvforge.contract.GeneratedExperienceDto
import com.cvforge.server.ai.AtsScorer
import com.cvforge.server.ai.UrlGuard
import com.cvforge.server.config.AppException
import com.cvforge.server.pdf.PdfRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AtsScorerTest {
    @Test fun scoresOnlyKeywordsPresentInText() {
        val r = AtsScorer.score(listOf("Kotlin", "Docker", "PostgreSQL", "Kubernetes"), "Desenvolvi APIs em Kotlin com PostgreSQL e Docker")
        assertEquals(75, r.score)
        assertEquals(listOf("Kubernetes"), r.missing)
    }

    @Test fun ignoresAccentsAndCase() {
        val r = AtsScorer.score(listOf("Integração Contínua"), "Implantei integracao continua no time")
        assertEquals(100, r.score)
    }

    @Test fun handlesSymbolsLikeCSharp() {
        assertEquals(100, AtsScorer.score(listOf("C#"), "Experiência com C# e .NET").score)
    }

    @Test fun emptyKeywordsGiveZero() {
        assertEquals(0, AtsScorer.score(emptyList(), "qualquer texto").score)
    }
}

class UrlGuardTest {
    @Test fun blocksLoopbackAndMetadata() {
        assertFailsWith<AppException.BadRequest> { UrlGuard.check("http://127.0.0.1/admin") }
        assertFailsWith<AppException.BadRequest> { UrlGuard.check("http://169.254.169.254/latest/meta-data") }
        assertFailsWith<AppException.BadRequest> { UrlGuard.check("http://10.0.0.5/") }
        assertFailsWith<AppException.BadRequest> { UrlGuard.check("file:///etc/passwd") }
    }
}

class PdfRendererTest {
    @Test fun generatesRealPdfForEveryTemplate() {
        val cv = CurriculumDto(
            id = "1", jobTitle = "Dev", company = "ACME", language = CvLanguage.PT_BR, atsScore = 80,
            keywordsFound = listOf("Kotlin"), keywordsMissing = emptyList(), createdAt = "2026-01-01",
            content = CvContentDto(
                name = "Ana Souza", email = "ana@exemplo.com", city = "São Paulo", summary = "Desenvolvedora com ênfase em APIs.",
                experiences = listOf(GeneratedExperienceDto("ACME", "Dev", "01/2022 – Atual", listOf("Criei APIs em Kotlin"))),
                education = listOf(EducationDto("USP", "Bacharelado", "Computação", 2021)),
                skills = listOf("Kotlin", "SQL"),
            ),
        )
        CvTemplate.entries.forEach {
            val bytes = PdfRenderer().render(cv, it)
            assertTrue(String(bytes.copyOf(5), Charsets.ISO_8859_1).startsWith("%PDF"))
        }
    }
}
