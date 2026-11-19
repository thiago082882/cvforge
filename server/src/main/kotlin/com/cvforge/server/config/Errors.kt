package com.cvforge.server.config

import io.ktor.http.HttpStatusCode

/** Erros de negócio → status HTTP + mensagem amigável (pt-BR). */
sealed class AppException(val status: HttpStatusCode, message: String, val code: String? = null) :
    RuntimeException(message) {
    class BadRequest(message: String, code: String? = null) : AppException(HttpStatusCode.BadRequest, message, code)
    class Unauthorized(message: String = "E-mail ou senha incorretos") : AppException(HttpStatusCode.Unauthorized, message)
    class NotFound(message: String) : AppException(HttpStatusCode.NotFound, message)
    class Conflict(message: String) : AppException(HttpStatusCode.Conflict, message)
    class Unavailable(message: String) : AppException(HttpStatusCode.ServiceUnavailable, message)
}
