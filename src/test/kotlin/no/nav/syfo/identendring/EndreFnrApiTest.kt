package no.nav.syfo.identendring

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import java.nio.file.Paths
import no.nav.syfo.application.setupAuth
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.identendring.api.registerFnrApi
import no.nav.syfo.identendring.client.NarmestelederClient
import no.nav.syfo.identendring.db.updateFnr
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.NarmesteLederResponseKafkaProducer
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.client.model.IdentInformasjon
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.aivenmigrering.SykmeldingV2KafkaProducer
import no.nav.syfo.sykmelding.api.model.EndreFnr
import no.nav.syfo.testutil.generateJWT
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
class EndreFnrApiTest : Spek({
    describe("Test endre fnr") {

        with(TestApplicationEngine()) {

            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()

            val pdlPersonService = mockk<PdlPersonService>(relaxed = true)
            val sendtSykmeldingKafkaProducer = mockk<SykmeldingV2KafkaProducer>(relaxed = true)
            val narmesteLederResponseKafkaProducer = mockk<NarmesteLederResponseKafkaProducer>(relaxed = true)
            val narmestelederClient = mockk<NarmestelederClient>()

            mockkStatic("no.nav.syfo.identendring.db.SyfoSmRegisterKt")
            val db = mockk<DatabaseInterfacePostgres>(relaxed = true)

            start()

            application.setupAuth(
                jwkProvider,
                "sillyUser",
                "clint",
                listOf("foo", "bar")
            )
            application.routing {
                registerFnrApi(UpdateFnrService(pdlPersonService, db, sendtSykmeldingKafkaProducer, narmesteLederResponseKafkaProducer, narmestelederClient, "topic"))
            }

            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }
            application.install(StatusPages) {
                exception<Throwable> { cause ->
                    call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                    log.error("Caught exception", cause)
                    throw cause
                }
            }

            coEvery { pdlPersonService.getPdlPerson(any()) } returns PdlPerson(
                    listOf(
                        IdentInformasjon("12345678913", false, "FOLKEREGISTERIDENT"),
                        IdentInformasjon("12345678912", true, "FOLKEREGISTERIDENT"),
                        IdentInformasjon("12345", false, "AKTORID")
                )
            )
            coEvery { narmestelederClient.getNarmesteledere(any()) } returns emptyList()

            every { db.updateFnr(any(), any()) } returns 1

            val endreFnr = EndreFnr(fnr = "12345678912", nyttFnr = "12345678913")

            with(handleRequest(HttpMethod.Post, "/api/sykmelding/fnr") {
                addHeader("Content-Type", "application/json")
                addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                setBody(objectMapper.writeValueAsString(endreFnr))
            }) {
                response.status() shouldEqual HttpStatusCode.OK
                response.content shouldEqual "Vellykket oppdatering."
            }
        }
    }
})
