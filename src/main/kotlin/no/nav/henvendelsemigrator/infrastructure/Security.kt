package no.nav.henvendelsemigrator.infrastructure

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.impl.JWTParser
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.Payload
import io.ktor.application.ApplicationCall
import io.ktor.auth.Authentication
import io.ktor.auth.Principal
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.jwt
import io.ktor.http.auth.HttpAuthHeader
import no.nav.henvendelsemigrator.log
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit

fun Authentication.Configuration.setupMock(mockPrincipal: SubjectPrincipal) {
    mock {
        principal = mockPrincipal
    }
}

fun Authentication.Configuration.setupJWT(jwksUrl: String) {
    jwt {
        authHeader(Security::useJwtFromCookie)
        verifier(Security.makeJwkProvider(jwksUrl))
        realm = "modiapersonoversikt-draft"
        validate { Security.validateJWT(it) }
    }
}

object Security {
    private const val invalidJWT = "Invalid JWT"
    private const val cookieName = "ID_token"

    fun getSubject(call: ApplicationCall): String {
        return try {
            useJwtFromCookie(call)
                ?.getBlob()
                ?.let { blob -> JWT.decode(blob).parsePayload().subject }
                ?: "Anonymous"
        } catch (e: Throwable) {
            invalidJWT
        }
    }

    internal fun useJwtFromCookie(call: ApplicationCall): HttpAuthHeader? {
        return try {
            val token = call.request.cookies[cookieName]
            io.ktor.http.auth.parseAuthorizationHeader("Bearer $token")
        } catch (ex: Throwable) {
            log.warn("Could not get JWT from cookie '$cookieName'", ex)
            null
        }
    }

    internal fun makeJwkProvider(jwksUrl: String): JwkProvider =
        JwkProviderBuilder(URL(jwksUrl))
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    internal fun validateJWT(credentials: JWTCredential): Principal? {
        return try {
            requireNotNull(credentials.payload.audience) { "Audience not present" }
            SubjectPrincipal(credentials.payload.subject)
        } catch (e: Exception) {
            log.error("Failed to validateJWT token", e)
            null
        }
    }

    private fun HttpAuthHeader.getBlob() = when {
        this is HttpAuthHeader.Single -> blob
        else -> null
    }

    private fun DecodedJWT.parsePayload(): Payload {
        val payloadString = String(Base64.getUrlDecoder().decode(payload))
        return JWTParser().parsePayload(payloadString)
    }
}

class SubjectPrincipal(val subject: String) : Principal
