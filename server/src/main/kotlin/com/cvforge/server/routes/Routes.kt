package com.cvforge.server.routes

import com.cvforge.contract.AuthResponse
import com.cvforge.contract.CvTemplate
import com.cvforge.contract.GenerateRequest
import com.cvforge.contract.HealthDto
import com.cvforge.contract.LoginRequest
import com.cvforge.contract.ProfileDto
import com.cvforge.contract.RegisterRequest
import com.cvforge.contract.UserDto
import com.cvforge.server.auth.AuthService
import com.cvforge.server.auth.TokenService
import com.cvforge.server.auth.userId
import com.cvforge.server.config.AppException
import com.cvforge.server.db.UserRepository
import com.cvforge.server.db.UserRow
import com.cvforge.server.service.CurriculumService
import com.cvforge.server.service.ProfileService
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

private fun UserRow.toDto() = UserDto(id.toString(), name, email)

private fun String?.toUuid(): UUID =
    runCatching { UUID.fromString(this) }.getOrElse { throw AppException.BadRequest("Identificador inválido") }

fun Route.healthRoutes() {
    get("/health") { call.respond(HealthDto("ok")) }
}

fun Route.authRoutes(auth: AuthService, tokens: TokenService) {
    route("/auth") {
        post("/register") {
            val user = auth.register(call.receive<RegisterRequest>())
            call.respond(HttpStatusCode.Created, AuthResponse(tokens.issue(user.id), user.toDto()))
        }
        post("/login") {
            val user = auth.login(call.receive<LoginRequest>())
            call.respond(AuthResponse(tokens.issue(user.id), user.toDto()))
        }
    }
}

fun Route.accountRoutes(users: UserRepository) {
    get("/me") {
        val user = users.findById(call.userId) ?: throw AppException.NotFound("Usuário não encontrado")
        call.respond(user.toDto())
    }
    /** Exclusão de conta + todos os dados (LGPD). */
    delete("/me") {
        users.deleteEverything(call.userId)
        call.respond(HttpStatusCode.NoContent)
    }
}

fun Route.profileRoutes(service: ProfileService) {
    route("/profile") {
        get { call.respond(service.get(call.userId)) }
        put { call.respond(service.save(call.userId, call.receive<ProfileDto>())) }
    }
}

fun Route.curriculumRoutes(service: CurriculumService, generateLimit: Route.(Route.() -> Unit) -> Unit) {
    route("/curriculums") {
        generateLimit {
            post("/generate") {
                val cv = service.generate(call.userId, call.receive<GenerateRequest>())
                call.respond(HttpStatusCode.Created, cv)
            }
            post("/{id}/cover-letter") {
                call.respond(service.coverLetter(call.userId, call.parameters["id"].toUuid()))
            }
        }
        get { call.respond(service.list(call.userId)) }
        get("/{id}") { call.respond(service.get(call.userId, call.parameters["id"].toUuid())) }
        delete("/{id}") {
            service.delete(call.userId, call.parameters["id"].toUuid())
            call.respond(HttpStatusCode.NoContent)
        }
        get("/{id}/export") {
            val template = call.request.queryParameters["template"]
                ?.let { t -> CvTemplate.entries.firstOrNull { it.name.equals(t, ignoreCase = true) } }
                ?: CvTemplate.CLASSIC
            val file = service.export(
                userId = call.userId,
                id = call.parameters["id"].toUuid(),
                format = call.request.queryParameters["format"] ?: "pdf",
                template = template,
            )
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, file.fileName).toString(),
            )
            call.respondBytes(file.bytes, ContentType.parse(file.contentType))
        }
    }
}
