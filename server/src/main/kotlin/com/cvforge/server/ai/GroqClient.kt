package com.cvforge.server.ai

import com.cvforge.server.config.AppConfig
import com.cvforge.server.config.AppException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

/** Abstração do LLM — permite trocar de provedor e testar sem rede. */
interface LlmClient {
    /** Resposta restrita a um JSON Schema. Retorna o JSON como texto. */
    suspend fun completeJson(system: String, user: String, schemaName: String, schema: JsonObject, maxTokens: Int = 3000): String

    suspend fun completeText(system: String, user: String, maxTokens: Int = 1200): String
}

/**
 * Cliente da Groq (API compatível com OpenAI).
 *
 * Plano gratuito da Groq (limites por modelo, conferidos em nov/2026): 30 req/min, ~1.000 req/dia,
 * 8K tokens/min, 200K tokens/dia. Por isso:
 *  - máx. 2 chamadas simultâneas ([gate]);
 *  - 429 → respeita Retry-After e, se não der, cai para o PRÓXIMO modelo da lista (cada modelo tem cota própria);
 *  - max_completion_tokens baixo e raciocínio mínimo (reasoning_effort) para não estourar o TPM.
 */
class GroqClient(private val http: HttpClient, private val cfg: AppConfig) : LlmClient {
    private val log = LoggerFactory.getLogger(GroqClient::class.java)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
    private val gate = Semaphore(2)

    @Serializable private data class Msg(val role: String, val content: String)
    @Serializable private data class ChatRequest(
        val model: String,
        val messages: List<Msg>,
        val temperature: Double,
        @SerialName("max_completion_tokens") val maxCompletionTokens: Int,
        @SerialName("response_format") val responseFormat: JsonObject? = null,
        @SerialName("reasoning_effort") val reasoningEffort: String? = null,
    )
    @Serializable private data class ChatResponse(val choices: List<Choice> = emptyList()) {
        @Serializable data class Choice(val message: RespMsg? = null)
        @Serializable data class RespMsg(val content: String? = null)
    }

    private sealed interface Outcome {
        data class Ok(val text: String) : Outcome
        data class RateLimited(val waitMs: Long) : Outcome
        data class Retry(val reason: String) : Outcome
        data class Skip(val reason: String) : Outcome
        data class Fatal(val message: String) : Outcome
    }

    override suspend fun completeJson(system: String, user: String, schemaName: String, schema: JsonObject, maxTokens: Int): String =
        run(system, user, temperature = 0.3, maxTokens = maxTokens) { model ->
            buildJsonObject {
                put("type", "json_schema")
                putJsonObject("json_schema") {
                    put("name", schemaName)
                    put("strict", model in STRICT_MODELS)
                    put("schema", schema)
                }
            }
        }

    override suspend fun completeText(system: String, user: String, maxTokens: Int): String =
        run(system, user, temperature = 0.5, maxTokens = maxTokens) { null }

    private suspend fun run(
        system: String,
        user: String,
        temperature: Double,
        maxTokens: Int,
        format: (String) -> JsonObject?,
    ): String {
        val key = cfg.groqApiKey
            ?: throw AppException.Unavailable("A geração por IA não está configurada no servidor (GROQ_API_KEY ausente).")

        for (model in cfg.groqModels) {
            var attempt = 0
            while (attempt < 2) {
                attempt++
                val outcome = gate.withPermit { callOnce(key, model, system, user, temperature, maxTokens, format(model)) }
                when (outcome) {
                    is Outcome.Ok -> return outcome.text
                    is Outcome.Fatal -> throw AppException.Unavailable(outcome.message)
                    is Outcome.Skip -> { log.warn("Groq {}: pulando modelo ({})", model, outcome.reason); break }
                    is Outcome.RateLimited -> {
                        log.warn("Groq {}: 429, aguardar {}ms", model, outcome.waitMs)
                        if (attempt < 2 && outcome.waitMs <= MAX_WAIT_MS) delay(outcome.waitMs) else break
                    }
                    is Outcome.Retry -> {
                        log.warn("Groq {}: tentativa {} falhou ({})", model, attempt, outcome.reason)
                        if (attempt < 2) delay(500) else break
                    }
                }
            }
        }
        throw AppException.Unavailable("O serviço de IA está sobrecarregado no momento. Tente novamente em alguns instantes.")
    }

    private suspend fun callOnce(
        key: String, model: String, system: String, user: String,
        temperature: Double, maxTokens: Int, format: JsonObject?,
    ): Outcome {
        val body = ChatRequest(
            model = model,
            messages = listOf(Msg("system", system), Msg("user", user)),
            temperature = temperature,
            maxCompletionTokens = maxTokens,
            responseFormat = format,
            reasoningEffort = reasoningFor(model),
        )
        val response = try {
            http.post("${cfg.groqBaseUrl}/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $key")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(body))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Outcome.Retry("rede: ${e::class.simpleName}")
        }

        val raw = response.bodyAsText()
        val status = response.status.value
        return when {
            response.status.isSuccess() -> {
                val text = runCatching { json.decodeFromString<ChatResponse>(raw).choices.firstOrNull()?.message?.content }.getOrNull()
                if (text.isNullOrBlank()) Outcome.Retry("resposta vazia") else Outcome.Ok(text)
            }
            status == 429 -> {
                val secs = response.headers["retry-after"]?.toDoubleOrNull() ?: 3.0
                Outcome.RateLimited((secs * 1000).toLong().coerceAtLeast(500))
            }
            status == 401 || status == 403 -> Outcome.Fatal("A chave da Groq configurada no servidor é inválida ou foi revogada.")
            status == 400 && raw.contains("json_validate_failed") -> Outcome.Retry("json inválido")
            status in 400..499 -> Outcome.Skip("HTTP $status")
            else -> Outcome.Retry("HTTP $status")
        }
    }

    private fun reasoningFor(model: String): String? = when {
        model.startsWith("openai/gpt-oss") -> "low"
        model.startsWith("qwen/qwen3") -> "none"
        else -> null
    }

    private companion object {
        /** Modelos com decodificação restrita (strict: true) — conforme a doc de Structured Outputs da Groq. */
        val STRICT_MODELS = setOf("openai/gpt-oss-120b", "qwen/qwen3.8-27b")
        const val MAX_WAIT_MS = 8_000L
    }
}

fun newGroqHttpClient(): HttpClient = HttpClient(io.ktor.client.engine.cio.CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 10_000
    }
}
