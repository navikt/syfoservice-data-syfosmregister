package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import no.nav.syfo.Environment
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.application.api.setupSwaggerDocApi
import no.nav.syfo.identendring.UpdateFnrService
import no.nav.syfo.identendring.api.registerFnrApi
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.narmesteleder.api.registrerNarmestelederRequestApi
import no.nav.syfo.oppgave.api.registerHentOppgaverApi
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.papirsykmelding.DiagnoseService
import no.nav.syfo.papirsykmelding.api.UpdateBehandletDatoService
import no.nav.syfo.papirsykmelding.api.UpdatePeriodeService
import no.nav.syfo.papirsykmelding.api.registrerBehandletDatoApi
import no.nav.syfo.papirsykmelding.api.registrerPeriodeApi
import no.nav.syfo.service.GjenapneSykmeldingService
import no.nav.syfo.sykmelding.DeleteSykmeldingService
import no.nav.syfo.sykmelding.api.registerDeleteSykmeldingApi
import no.nav.syfo.sykmelding.api.registerGjenapneSykmeldingApi
import no.nav.syfo.sykmelding.api.registerUpdateBiDiagnosisApi
import no.nav.syfo.sykmelding.api.registerUpdateDiagnosisApi

fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
    updatePeriodeService: UpdatePeriodeService,
    updateBehandletDatoService: UpdateBehandletDatoService,
    updateFnrService: UpdateFnrService,
    diagnoseService: DiagnoseService,
    oppgaveClient: OppgaveClient,
    jwkProviderInternal: JwkProvider,
    issuerServiceuser: String,
    clientId: String,
    appIds: List<String>,
    deleteSykmeldingService: DeleteSykmeldingService,
    gjenapneSykmeldingService: GjenapneSykmeldingService,
    narmestelederService: NarmestelederService
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
            appIds = appIds
        )

        routing {
            registerNaisApi(applicationState)
            setupSwaggerDocApi()

            authenticate("jwtserviceuser") {
                registrerPeriodeApi(updatePeriodeService)
                registrerBehandletDatoApi(updateBehandletDatoService)
                registerFnrApi(updateFnrService)
                registerGjenapneSykmeldingApi(gjenapneSykmeldingService)
                registerUpdateDiagnosisApi(diagnoseService)
                registerDeleteSykmeldingApi(deleteSykmeldingService)
                registerUpdateBiDiagnosisApi(diagnoseService)
                registerHentOppgaverApi(oppgaveClient)
                registrerNarmestelederRequestApi(narmestelederService)
            }
        }
    }
