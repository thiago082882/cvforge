package com.cvforge.server

import com.cvforge.contract.ErrorDto
import com.cvforge.server.ai.CvGenerator
import com.cvforge.server.ai.GroqClient
import com.cvforge.server.ai.JobScraper
import com.cvforge.server.ai.LlmClient
import com.cvforge.server.ai.newGroqHttpClient
import com.cvforge.server.auth.AuthService
import com.cvforge.server.auth.TokenService
import com.cvforge.server.config.AppConfig
import com.cvforge.server.config.AppException
import com.cvforge.server.db.CurriculumRepository
import com.cvforge.server.db.DatabaseFactory
import com.cvforge.server.db.ProfileRepository
import com.cvforge.server.db.UserRepository
import com.cvforge.server.pdf.PdfRenderer
import com.cvforge.server.routes.accountRoutes
import com.cvforge.server.routes.authRoutes
import com.cvforge.server.routes.curriculumRoutes
import com.cvforge.server.routes.healthRoutes
import com.cvforge.server.routes.profileRoutes
import com.cvforge.server.service.CurriculumService
import com.cvforge.server.service.ProfileService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

fun main() {
    val config = AppConfig.fromEnv()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(config) }.start(wait = true)
}

fun Application.module(config: AppConfig, llm: LlmClient = GroqClient(newGroqHttpClient(), config)) {
    if (!config.production) log.warn("Rodando em modo DEV (APP_ENV != production)")
    if (config.groqApiKey == null) log.warn("GROQ_API_KEY ausente: a geração de currículos vai responder 503")

    DatabaseFactory.connect(config)

    // ── composição das dependências ──────────────────────────────────────────
    val users = UserRepository()
    val profiles = ProfileRepository()
    val curriculums = CurriculumRepository()
    val tokens = TokenService(config)
    val auth = AuthService(users)
    val profileService = ProfileService(profiles)
    val curriculumService = CurriculumService(users, profiles, curriculums, JobScraper(), CvGenerator(llm), PdfRenderer())

    // ── plugins ──────────────────────────────────────────────────────────────
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false })
    }

    if (config.trustProxy) install(XForwardedHeaders)

    install(CallLogging) { level = Level.INFO } // loga método/caminho — nunca corpo de requisição (dados pessoais)

    install(CORS) {
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        exposeHeader(HttpHeaders.ContentDisposition)
        if (config.corsHosts.isEmpty()) {
            if (!config.production) {
                allowHost("localhost:8081"); allowHost("127.0.0.1:8081") // dev server do app Web
            }
        } else {
            config.corsHosts.forEach { allowHost(it, schemes = listOf("https")) }
        }
    }

    install(StatusPages) {
        exception<AppException> { call, e -> call.respond(e.status, ErrorDto(e.message ?: "Erro", e.code)) }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorDto("Requisição inválida")) }
        exception<Throwable> { call, e ->
            call.application.log.error("Erro não tratado", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorDto("Erro interno do servidor"))
        }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respond(status, ErrorDto("Muitas requisições. Aguarde um instante e tente novamente."))
        }
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "cvforge"
            verifier(tokens.verifier())
            validate { cred ->
                val id = cred.payload.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                // Token de conta já excluída deixa de valer imediatamente.
                if (id != null && users.findById(id) != null) JWTPrincipal(cred.payload) else null
            }
            challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized, ErrorDto("Sessão expirada. Entre novamente.")) }
        }
    }

    install(RateLimit) {
        register(RateLimitName("auth")) { rateLimiter(limit = 10, refillPeriod = 1.minutes) }
        register(RateLimitName("api")) { rateLimiter(limit = 120, refillPeriod = 1.minutes) }
        // Protege a cota gratuita da Groq: por USUÁRIO, não por IP.
        register(RateLimitName("generate")) {
            rateLimiter(limit = 6, refillPeriod = 1.minutes)
            requestKey { call -> call.principal<JWTPrincipal>()?.subject ?: call.request.origin.remoteHost }
        }
    }

    // ── rotas ────────────────────────────────────────────────────────────────
    routing {
        healthRoutes()
        rateLimit(RateLimitName("auth")) { authRoutes(auth, tokens) }
        authenticate("auth-jwt") {
            rateLimit(RateLimitName("api")) {
                accountRoutes(users)
                profileRoutes(profileService)
                curriculumRoutes(curriculumService) { inner -> rateLimit(RateLimitName("generate")) { inner() } }
            }
        }
    }
}
