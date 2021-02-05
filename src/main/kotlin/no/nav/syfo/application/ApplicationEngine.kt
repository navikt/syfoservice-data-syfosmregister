package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.syfo.Environment
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.application.api.setupSwaggerDocApi
import no.nav.syfo.papirsykmelding.api.UpdateBehandletDatoService
import no.nav.syfo.papirsykmelding.api.UpdatePeriodeService
import no.nav.syfo.papirsykmelding.api.registrerBehandletDatoApi
import no.nav.syfo.papirsykmelding.api.registrerPeriodeApi

fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
    updatePeriodeService: UpdatePeriodeService,
    updateBehandletDatoService: UpdateBehandletDatoService,
    jwkProviderInternal: JwkProvider,
    issuerServiceuser: String,
    clientId: String,
    appIds: List<String>
): ApplicationEngine =
        embeddedServer(Netty, env.applicationPort) {
            install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
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
                    registrerBehandletDatoApi(updateBehandletDatoService)
                }
            }
        }
