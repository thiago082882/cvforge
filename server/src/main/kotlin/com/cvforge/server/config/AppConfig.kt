package com.cvforge.server.config

import java.security.SecureRandom
import java.util.Base64

data class AppConfig(
    val production: Boolean,
    val port: Int,
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val dbPoolSize: Int,
    val jwtSecret: String,
    val jwtIssuer: String,
    val jwtAudience: String,
    val jwtExpiryDays: Long,
    val groqApiKey: String?,
    val groqBaseUrl: String,
    /** Ordem = prioridade. Se um modelo falhar/limitar, tenta o próximo. */
    val groqModels: List<String>,
    /** Hosts permitidos no CORS (ex.: app.seudominio.com). Vazio = só localhost (dev). */
    val corsHosts: List<String>,
    /** true quando atrás de proxy/load balancer (usa X-Forwarded-For para rate limit). */
    val trustProxy: Boolean,
) {
    companion object {
        const val DEFAULT_MODELS = "openai/gpt-oss-120b,openai/gpt-oss-20b,qwen/qwen3.8-27b"

        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            val production = env["APP_ENV"].equals("production", ignoreCase = true)

            val jwtSecret = env["JWT_SECRET"]?.takeIf { it.isNotBlank() }
            if (production) {
                require(jwtSecret != null && jwtSecret.length >= 32) {
                    "JWT_SECRET é obrigatório em produção (mínimo 32 caracteres). Gere com: openssl rand -hex 48"
                }
                require(!env["DATABASE_URL"].isNullOrBlank()) { "DATABASE_URL é obrigatório em produção" }
                require(!env["GROQ_API_KEY"].isNullOrBlank()) { "GROQ_API_KEY é obrigatório em produção" }
            }
            // Em dev, sem JWT_SECRET: gera um aleatório (tokens deixam de valer ao reiniciar).
            val secret = jwtSecret ?: ByteArray(48).also { SecureRandom().nextBytes(it) }
                .let { Base64.getEncoder().encodeToString(it) }

            return AppConfig(
                production = production,
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                dbUrl = env["DATABASE_URL"] ?: "jdbc:postgresql://localhost:5432/cvforge",
                dbUser = env["DATABASE_USER"] ?: "postgres",
                dbPassword = env["DATABASE_PASSWORD"] ?: "postgres",
                dbPoolSize = env["DATABASE_POOL_SIZE"]?.toIntOrNull() ?: 10,
                jwtSecret = secret,
                jwtIssuer = "cvforge",
                jwtAudience = "cvforge-users",
                jwtExpiryDays = env["JWT_EXPIRY_DAYS"]?.toLongOrNull() ?: 30,
                groqApiKey = env["GROQ_API_KEY"]?.takeIf { it.isNotBlank() },
                groqBaseUrl = env["GROQ_BASE_URL"] ?: "https://api.groq.com/openai/v1",
                groqModels = (env["GROQ_MODELS"] ?: DEFAULT_MODELS).split(",").map { it.trim() }.filter { it.isNotEmpty() },
                corsHosts = env["CORS_HOSTS"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                trustProxy = env["TRUST_PROXY"].equals("true", ignoreCase = true),
            )
        }
    }
}
