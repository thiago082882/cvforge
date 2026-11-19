package com.cvforge.app.data

import com.cvforge.contract.AuthResponse
import com.cvforge.contract.CurriculumDto
import com.cvforge.contract.CurriculumSummaryDto
import com.cvforge.contract.CvTemplate
import com.cvforge.contract.ErrorDto
import com.cvforge.contract.GenerateRequest
import com.cvforge.contract.LoginRequest
import com.cvforge.contract.ProfileDto
import com.cvforge.contract.RegisterRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** Erro "esperado" com mensagem pronta para mostrar ao usuário. */
class ApiException(message: String, val status: Int = 0, val code: String? = null) : Exception(message)

class ApiClient(config: AppConfig, private val session: SessionStore) {
    private val http = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000 // geração por IA pode levar alguns segundos
            connectTimeoutMillis = 15_000
        }
        defaultRequest { url(config.apiBaseUrl.trimEnd('/') + "/") }
        expectSuccess = false
    }.also { client ->
        client.plugin(HttpSend).intercept { request ->
            session.token?.let { request.bearerAuth(it) }
            execute(request)
        }
    }

    private suspend fun execute(block: suspend HttpClient.() -> HttpResponse): HttpResponse {
        val response = try {
            block(http)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw ApiException("Não foi possível conectar ao servidor. Verifique sua internet e tente novamente.")
        }
        if (!response.status.isSuccess()) {
            val status = response.status.value
            val err = runCatching { response.body<ErrorDto>() }.getOrNull()
            // 401 com sessão ativa = token expirado/conta removida → volta para o login.
            if (status == 401 && session.token != null) session.clear()
            throw ApiException(err?.message ?: fallbackMessage(status), status, err?.code)
        }
        return response
    }

    private fun fallbackMessage(status: Int) = when (status) {
        401 -> "Sessão expirada. Entre novamente."
        404 -> "Não encontrado."
        429 -> "Muitas tentativas. Aguarde um instante."
        in 500..599 -> "O servidor está com problemas. Tente novamente em instantes."
        else -> "Algo deu errado (erro $status)."
    }

    // Paths relativos (sem "/" inicial) para respeitar o defaultRequest.
    suspend fun login(r: LoginRequest): AuthResponse = execute { post("auth/login") { contentType(ContentType.Application.Json); setBody(r) } }.body()
    suspend fun register(r: RegisterRequest): AuthResponse = execute { post("auth/register") { contentType(ContentType.Application.Json); setBody(r) } }.body()
    suspend fun deleteAccount() { execute { delete("me") } }

    suspend fun getProfile(): ProfileDto? = try {
        execute { get("profile") }.body<ProfileDto>()
    } catch (e: ApiException) {
        if (e.status == 404) null else throw e
    }
    suspend fun saveProfile(p: ProfileDto): ProfileDto = execute { put("profile") { contentType(ContentType.Application.Json); setBody(p) } }.body()

    suspend fun generate(r: GenerateRequest): CurriculumDto = execute { post("curriculums/generate") { contentType(ContentType.Application.Json); setBody(r) } }.body()
    suspend fun listCurriculums(): List<CurriculumSummaryDto> = execute { get("curriculums") }.body()
    suspend fun getCurriculum(id: String): CurriculumDto = execute { get("curriculums/$id") }.body()
    suspend fun deleteCurriculum(id: String) { execute { delete("curriculums/$id") } }
    suspend fun coverLetter(id: String): CurriculumDto = execute { post("curriculums/$id/cover-letter") }.body()
    suspend fun export(id: String, format: String, template: CvTemplate): ByteArray =
        execute { get("curriculums/$id/export") { parameter("format", format); parameter("template", template.name) } }.body()
}

/** Converte exceções em Result sem engolir cancelamento de coroutine. */
suspend fun <T> safeCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}

fun Throwable.userMessage(): String = (this as? ApiException)?.message ?: "Algo deu errado. Tente novamente."

class AuthRepository(private val api: ApiClient, private val session: SessionStore) {
    suspend fun login(email: String, password: String) = safeCall {
        api.login(LoginRequest(email.trim(), password)).also { save(it, isNew = false) }
    }

    suspend fun register(name: String, email: String, password: String) = safeCall {
        api.register(RegisterRequest(name.trim(), email.trim(), password)).also { save(it, isNew = true) }
    }

    suspend fun deleteAccount() = safeCall { api.deleteAccount(); session.clear() }

    fun logout() = session.clear()

    private fun save(r: AuthResponse, isNew: Boolean) =
        session.save(Session(r.token, r.user.id, r.user.name, r.user.email), isNew)
}

class ProfileRepository(private val api: ApiClient) {
    suspend fun get() = safeCall { api.getProfile() }
    suspend fun save(p: ProfileDto) = safeCall { api.saveProfile(p) }
}

class CurriculumRepository(private val api: ApiClient) {
    suspend fun list() = safeCall { api.listCurriculums() }
    suspend fun get(id: String) = safeCall { api.getCurriculum(id) }
    suspend fun generate(r: GenerateRequest) = safeCall { api.generate(r) }
    suspend fun delete(id: String) = safeCall { api.deleteCurriculum(id) }
    suspend fun coverLetter(id: String) = safeCall { api.coverLetter(id) }
    suspend fun export(id: String, format: String, template: CvTemplate) = safeCall { api.export(id, format, template) }
}
