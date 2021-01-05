package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import io.ktor.auth.authenticate
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.syfo.Environment
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.application.api.setupSwaggerDocApi
import no.nav.syfo.papirsykmelding.api.UpdatePeriodeService
import no.nav.syfo.papirsykmelding.api.registrerPeriodeApi

fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
    updatePeriodeService: UpdatePeriodeService,
    jwkProviderInternal: JwkProvider,
    issuerServiceuser: String,
    clientId: String,
    appIds: List<String>
): ApplicationEngine =
        embeddedServer(Netty, env.applicationPort) {
            setupAuth(
                jwkProviderInternal = jwkProviderInternal,
                issuerServiceuser = issuerServiceuser,
                clientId = clientId,
                appIds = appIds)

            routing {
                registerNaisApi(applicationState)
                setupSwaggerDocApi()

                authenticate("jwtserviceuser") {
                    registrerPeriodeApi(updatePeriodeService)
                }
            }
        }
