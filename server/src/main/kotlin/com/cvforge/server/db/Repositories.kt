package com.cvforge.server.db

import com.cvforge.contract.CurriculumDto
import com.cvforge.contract.CurriculumSummaryDto
import com.cvforge.contract.CvContentDto
import com.cvforge.contract.CvLanguage
import com.cvforge.contract.EducationDto
import com.cvforge.contract.ExperienceDto
import com.cvforge.contract.ProfileDto
import com.cvforge.server.config.AppException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true }

data class UserRow(val id: UUID, val email: String, val name: String, val passwordHash: String)

class UserRepository {
    suspend fun create(email: String, passwordHash: String, name: String): UserRow {
        val id = UUID.randomUUID()
        try {
            dbQuery {
                UsersTable.insert {
                    it[UsersTable.id] = id
                    it[UsersTable.email] = email
                    it[UsersTable.passwordHash] = passwordHash
                    it[UsersTable.name] = name
                }
            }
        } catch (e: ExposedSQLException) {
            if (e.sqlState == "23505") throw AppException.Conflict("Este e-mail já está cadastrado")
            throw e
        }
        return UserRow(id, email, name, passwordHash)
    }

    suspend fun findByEmail(email: String): UserRow? = dbQuery {
        UsersTable.selectAll().where { UsersTable.email eq email }.singleOrNull()?.toUser()
    }

    suspend fun findById(id: UUID): UserRow? = dbQuery {
        UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull()?.toUser()
    }

    /** Apaga o usuário e TODOS os dados dele (LGPD). Ordem explícita: não depende de FK em cascata. */
    suspend fun deleteEverything(id: UUID) = dbQuery {
        CurriculumsTable.deleteWhere { userId eq id }
        ProfilesTable.deleteWhere { userId eq id }
        UsersTable.deleteWhere { UsersTable.id eq id }
        Unit
    }

    private fun ResultRow.toUser() = UserRow(
        id = this[UsersTable.id].value,
        email = this[UsersTable.email],
        name = this[UsersTable.name],
        passwordHash = this[UsersTable.passwordHash],
    )
}

class ProfileRepository {
    suspend fun find(userId: UUID): ProfileDto? = dbQuery {
        ProfilesTable.selectAll().where { ProfilesTable.userId eq userId }.singleOrNull()?.let { row ->
            ProfileDto(
                city = row[ProfilesTable.city],
                phone = row[ProfilesTable.phone],
                linkedinUrl = row[ProfilesTable.linkedinUrl],
                website = row[ProfilesTable.website],
                summary = row[ProfilesTable.summary],
                // runCatching: linhas no formato antigo (projeto original) viram lista vazia em vez de quebrar.
                experiences = runCatching { json.decodeFromString<List<ExperienceDto>>(row[ProfilesTable.experiencesJson]) }.getOrDefault(emptyList()),
                education = runCatching { json.decodeFromString<List<EducationDto>>(row[ProfilesTable.educationJson]) }.getOrDefault(emptyList()),
                skills = runCatching { json.decodeFromString<List<String>>(row[ProfilesTable.skillsJson]) }.getOrDefault(emptyList()),
            )
        }
    }

    suspend fun upsert(userId: UUID, p: ProfileDto) = dbQuery {
        val exists = ProfilesTable.selectAll().where { ProfilesTable.userId eq userId }.any()
        if (!exists) {
            ProfilesTable.insert {
                it[id] = UUID.randomUUID()
                it[ProfilesTable.userId] = userId
                it[city] = p.city
                it[phone] = p.phone
                it[linkedinUrl] = p.linkedinUrl
                it[website] = p.website
                it[summary] = p.summary
                it[experiencesJson] = json.encodeToString(p.experiences)
                it[educationJson] = json.encodeToString(p.education)
                it[skillsJson] = json.encodeToString(p.skills)
            }
        } else {
            ProfilesTable.update({ ProfilesTable.userId eq userId }) {
                it[city] = p.city
                it[phone] = p.phone
                it[linkedinUrl] = p.linkedinUrl
                it[website] = p.website
                it[summary] = p.summary
                it[experiencesJson] = json.encodeToString(p.experiences)
                it[educationJson] = json.encodeToString(p.education)
                it[skillsJson] = json.encodeToString(p.skills)
            }
        }
        Unit
    }
}

class CurriculumRepository {
    suspend fun save(userId: UUID, cv: CurriculumDto): CurriculumDto = dbQuery {
        CurriculumsTable.insert {
            it[id] = UUID.fromString(cv.id)
            it[CurriculumsTable.userId] = userId
            it[jobTitle] = cv.jobTitle.take(255)
            it[company] = cv.company.take(255)
            it[language] = cv.language.name
            it[atsScore] = cv.atsScore
            it[atsKeywordsFoundJson] = json.encodeToString(cv.keywordsFound)
            it[atsKeywordsMissingJson] = json.encodeToString(cv.keywordsMissing)
            it[contentJson] = json.encodeToString(cv.content)
            it[coverLetter] = cv.coverLetter
        }
        cv
    }

    suspend fun listSummaries(userId: UUID): List<CurriculumSummaryDto> = dbQuery {
        CurriculumsTable.selectAll()
            .where { CurriculumsTable.userId eq userId }
            .orderBy(CurriculumsTable.createdAt, SortOrder.DESC)
            .map {
                CurriculumSummaryDto(
                    id = it[CurriculumsTable.id].value.toString(),
                    jobTitle = it[CurriculumsTable.jobTitle],
                    company = it[CurriculumsTable.company],
                    atsScore = it[CurriculumsTable.atsScore],
                    createdAt = it[CurriculumsTable.createdAt].toString(),
                )
            }
    }

    /** Sempre filtra por dono: um usuário nunca enxerga currículo de outro. */
    suspend fun find(id: UUID, userId: UUID): CurriculumDto? = dbQuery {
        CurriculumsTable.selectAll()
            .where { (CurriculumsTable.id eq id) and (CurriculumsTable.userId eq userId) }
            .singleOrNull()?.toDto()
    }

    suspend fun setCoverLetter(id: UUID, userId: UUID, text: String) = dbQuery {
        CurriculumsTable.update({ (CurriculumsTable.id eq id) and (CurriculumsTable.userId eq userId) }) {
            it[coverLetter] = text
        }
        Unit
    }

    suspend fun delete(id: UUID, userId: UUID): Boolean = dbQuery {
        CurriculumsTable.deleteWhere { (CurriculumsTable.id eq id) and (CurriculumsTable.userId eq userId) } > 0
    }

    private fun ResultRow.toDto() = CurriculumDto(
        id = this[CurriculumsTable.id].value.toString(),
        jobTitle = this[CurriculumsTable.jobTitle],
        company = this[CurriculumsTable.company],
        language = runCatching { CvLanguage.valueOf(this[CurriculumsTable.language]) }.getOrDefault(CvLanguage.PT_BR),
        atsScore = this[CurriculumsTable.atsScore],
        keywordsFound = runCatching { json.decodeFromString<List<String>>(this[CurriculumsTable.atsKeywordsFoundJson]) }.getOrDefault(emptyList()),
        keywordsMissing = runCatching { json.decodeFromString<List<String>>(this[CurriculumsTable.atsKeywordsMissingJson]) }.getOrDefault(emptyList()),
        content = json.decodeFromString<CvContentDto>(this[CurriculumsTable.contentJson]),
        coverLetter = this[CurriculumsTable.coverLetter],
        createdAt = this[CurriculumsTable.createdAt].toString(),
    )
}
