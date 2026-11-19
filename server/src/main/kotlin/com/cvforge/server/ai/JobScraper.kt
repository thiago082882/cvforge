package com.cvforge.server.ai

import com.cvforge.server.config.AppException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Bloqueia SSRF: o servidor nunca deve acessar rede interna, localhost ou metadados de nuvem. */
object UrlGuard {
    private val ALLOWED_PORTS = setOf(-1, 80, 443, 8080, 8443)

    fun check(raw: String): URI {
        val uri = try { URI(raw.trim()) } catch (e: Exception) { throw AppException.BadRequest("O link da vaga é inválido") }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") throw AppException.BadRequest("O link da vaga deve começar com http:// ou https://")
        val host = uri.host ?: throw AppException.BadRequest("O link da vaga é inválido")
        if (uri.port !in ALLOWED_PORTS) throw AppException.BadRequest("Esse endereço não é permitido")
        val addresses = try { InetAddress.getAllByName(host) } catch (e: Exception) {
            throw AppException.BadRequest("Não consegui encontrar esse endereço. Confira o link.")
        }
        if (addresses.any(::isInternal)) throw AppException.BadRequest("Esse endereço não é permitido")
        return uri
    }

    fun isInternal(a: InetAddress): Boolean {
        if (a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress) return true
        val b = a.address
        if (b.size == 4) {
            val o0 = b[0].toInt() and 0xFF
            val o1 = b[1].toInt() and 0xFF
            if (o0 == 0) return true
            if (o0 == 100 && o1 in 64..127) return true // CGNAT 100.64.0.0/10
        }
        if (b.size == 16 && ((b[0].toInt() and 0xFE) == 0xFC)) return true // IPv6 ULA fc00::/7
        return false
    }
}

class JobScraper {
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER) // redirecionamentos são validados um a um
        .connectTimeout(Duration.ofSeconds(8))
        .build()

    suspend fun fetch(url: String): JobInfo = withContext(Dispatchers.IO) { fetchBlocking(url) }

    fun fromText(text: String): JobInfo = JobInfo(title = null, company = null, text = text.trim().take(MAX_TEXT))

    private fun fetchBlocking(raw: String): JobInfo {
        var uri = UrlGuard.check(raw)
        repeat(MAX_REDIRECTS + 1) {
            val request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "Mozilla/5.0 (compatible; CVForgeBot/1.0)")
                .header("Accept", "text/html,application/xhtml+xml")
                .GET().build()
            val response = try {
                client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            } catch (e: Exception) {
                throw AppException.BadRequest(UNREADABLE)
            }

            val code = response.statusCode()
            if (code in 300..399) {
                response.body().close()
                val location = response.headers().firstValue("Location").orElse(null) ?: throw AppException.BadRequest(UNREADABLE)
                uri = UrlGuard.check(uri.resolve(location).toString())
                return@repeat
            }
            if (code !in 200..299) { response.body().close(); throw AppException.BadRequest(UNREADABLE) }
            val type = response.headers().firstValue("Content-Type").orElse("")
            if (!type.contains("html", ignoreCase = true)) { response.body().close(); throw AppException.BadRequest(UNREADABLE) }

            val bytes = response.body().use { it.readNBytes(MAX_BYTES) }
            return parse(bytes, uri.toString())
        }
        throw AppException.BadRequest(UNREADABLE)
    }

    private fun parse(bytes: ByteArray, baseUri: String): JobInfo {
        val doc = Jsoup.parse(ByteArrayInputStream(bytes), null, baseUri)

        // 1) Dados estruturados (schema.org/JobPosting) — presentes em muitos sites de vagas.
        val posting = doc.select("script[type=application/ld+json]").firstNotNullOfOrNull { el ->
            runCatching { findJobPosting(Json.parseToJsonElement(el.data())) }.getOrNull()
        }
        if (posting != null) {
            val description = posting["description"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                ?.let { Jsoup.parse(it).text() }.orEmpty()
            val title = (posting["title"] as? JsonPrimitive)?.contentOrNull
            val company = when (val org = posting["hiringOrganization"]) {
                is JsonObject -> (org["name"] as? JsonPrimitive)?.contentOrNull
                is JsonPrimitive -> org.contentOrNull
                else -> null
            }
            if (description.length >= MIN_TEXT) return JobInfo(title, company, description.take(MAX_TEXT))
        }

        // 2) Fallback: texto visível da página.
        doc.select("script,style,nav,footer,header,noscript,svg,form,aside").remove()
        val text = doc.body().text().trim()
        if (text.length < MIN_TEXT) throw AppException.BadRequest(UNREADABLE)
        val title = doc.select("meta[property=og:title]").attr("content").ifBlank { doc.title() }
            .replace(Regex("\\s*[-|·–].*$"), "").trim().ifBlank { null }
        return JobInfo(title?.take(120), null, text.take(MAX_TEXT))
    }

    private fun findJobPosting(el: JsonElement): JsonObject? = when (el) {
        is JsonArray -> el.firstNotNullOfOrNull { findJobPosting(it) }
        is JsonObject -> {
            val type = el["@type"]
            val isPosting = (type as? JsonPrimitive)?.contentOrNull == "JobPosting" ||
                (type as? JsonArray)?.any { (it as? JsonPrimitive)?.contentOrNull == "JobPosting" } == true
            if (isPosting) el else el["@graph"]?.let { findJobPosting(it) }
        }
        else -> null
    }

    private companion object {
        const val MAX_REDIRECTS = 4
        const val MAX_BYTES = 1_000_000
        const val MAX_TEXT = 8_000
        const val MIN_TEXT = 200
        const val UNREADABLE =
            "Não consegui ler o conteúdo dessa página (muitos sites de vagas exigem login ou carregam via JavaScript). " +
                "Copie o texto da vaga e cole no campo de descrição."
    }
}
