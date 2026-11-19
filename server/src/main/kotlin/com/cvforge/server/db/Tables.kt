package com.cvforge.server.db

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

// Mantém os nomes de tabela do projeto original; colunas novas são nullable/com default
// para que createMissingTablesAndColumns funcione sobre bancos já existentes.

object UsersTable : UUIDTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val name = varchar("name", 255)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
}

object ProfilesTable : UUIDTable("user_profiles") {
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val city = varchar("city", 255).default("")
    val phone = varchar("phone", 50).nullable()
    val linkedinUrl = varchar("linkedin_url", 512).nullable()
    val website = varchar("website", 512).nullable()
    val summary = text("summary").default("")
    val experiencesJson = text("experiences_json")
    val educationJson = text("education_json")
    val skillsJson = text("skills_json")
}

object CurriculumsTable : UUIDTable("generated_curriculums") {
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE).index()
    val jobTitle = varchar("job_title", 255)
    val company = varchar("company", 255)
    val language = varchar("language", 10).default("PT_BR")
    val atsScore = integer("ats_score")
    val atsKeywordsFoundJson = text("ats_keywords_found_json")
    val atsKeywordsMissingJson = text("ats_keywords_missing_json")
    val contentJson = text("content_json")
    val coverLetter = text("cover_letter").nullable()
    val pdfUrl = varchar("pdf_url", 1024).nullable() // legado, não usado
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }.index()
}
