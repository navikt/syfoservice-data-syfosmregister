package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.Principal
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log

fun Application.setupAuth(
    jwkProviderInternal: JwkProvider,
    issuerServiceuser: String,
    clientId: String,
    appIds: List<String>
) {
    install(Authentication) {
        jwt(name = "jwtserviceuser") {
            verifier(jwkProviderInternal, issuerServiceuser)
            validate { credentials ->
                val appId: String = credentials.payload.getClaim("azp").asString()
                if (appId in appIds && clientId in credentials.payload.audience) {
                    JWTPrincipal(credentials.payload)
                } else {
                    unauthorized(credentials)
                }
            }
        }
    }
}

fun unauthorized(credentials: JWTCredential): Principal? {
    log.warn(
        "Auth: Unexpected audience for jwt {}, {}",
        StructuredArguments.keyValue("issuer", credentials.payload.issuer),
        StructuredArguments.keyValue("audience", credentials.payload.audience)
    )
    return null
}
