package com.cvforge.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.cvforge.contract.Limits
import com.cvforge.contract.LoginRequest
import com.cvforge.contract.RegisterRequest
import com.cvforge.contract.Validators
import com.cvforge.server.config.AppConfig
import com.cvforge.server.config.AppException
import com.cvforge.server.db.UserRepository
import com.cvforge.server.db.UserRow
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mindrot.jbcrypt.BCrypt
import java.util.Date
import java.util.UUID

class TokenService(private val cfg: AppConfig) {
    private val algorithm = Algorithm.HMAC256(cfg.jwtSecret)

    fun issue(userId: UUID): String = JWT.create()
        .withIssuer(cfg.jwtIssuer)
        .withAudience(cfg.jwtAudience)
        .withSubject(userId.toString())
        .withExpiresAt(Date(System.currentTimeMillis() + cfg.jwtExpiryDays * 24L * 60 * 60 * 1000))
        .sign(algorithm)

    fun verifier(): JWTVerifier = JWT.require(algorithm)
        .withIssuer(cfg.jwtIssuer)
        .withAudience(cfg.jwtAudience)
        .build()
}

class AuthService(private val users: UserRepository) {
    /**
     * Hash VÁLIDO usado quando o e-mail não existe, para que login com e-mail inexistente
     * custe o mesmo tempo que com e-mail existente (evita enumeração de usuários).
     * (No projeto original o hash "dummy" era inválido e o BCrypt lançava exceção → HTTP 500.)
     */
    private val dummyHash: String = BCrypt.hashpw("cvforge-dummy-password", BCrypt.gensalt(12))

    suspend fun register(req: RegisterRequest): UserRow {
        Validators.name(req.name)?.let { throw AppException.BadRequest(it) }
        Validators.email(req.email)?.let { throw AppException.BadRequest(it) }
        Validators.password(req.password)?.let { throw AppException.BadRequest(it) }
        if (req.password.length > 200) throw AppException.BadRequest("Senha muito longa")

        val email = req.email.trim().lowercase()
        if (users.findByEmail(email) != null) throw AppException.Conflict("Este e-mail já está cadastrado")
        val hash = withContext(Dispatchers.Default) { BCrypt.hashpw(req.password, BCrypt.gensalt(12)) }
        return users.create(email, hash, req.name.trim().take(Limits.NAME))
    }

    suspend fun login(req: LoginRequest): UserRow {
        val user = users.findByEmail(req.email.trim().lowercase())
        val ok = withContext(Dispatchers.Default) {
            BCrypt.checkpw(req.password.take(200), user?.passwordHash ?: dummyHash)
        }
        if (user == null || !ok) throw AppException.Unauthorized()
        return user
    }
}

val ApplicationCall.userId: UUID
    get() = UUID.fromString(principal<JWTPrincipal>()!!.subject!!)
