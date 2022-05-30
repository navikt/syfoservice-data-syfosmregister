package no.nav.syfo.identendring

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.syfo.application.setupAuth
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.identendring.api.registerFnrApi
import no.nav.syfo.identendring.client.NarmestelederClient
import no.nav.syfo.identendring.db.updateFnr
import no.nav.syfo.narmesteleder.NarmesteLederResponseKafkaProducer
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.client.model.IdentInformasjon
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.aivenmigrering.SykmeldingV2KafkaProducer
import no.nav.syfo.sykmelding.api.model.EndreFnr
import no.nav.syfo.testutil.generateJWT
import org.amshove.kluent.shouldBeEqualTo
import java.nio.file.Paths

class EndreFnrApiTest : FunSpec({
    test("Test endre fnr") {

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

            coEvery { pdlPersonService.getPdlPerson(any()) } returns PdlPerson(
                listOf(
                    IdentInformasjon("12345678913", false, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("12345678912", true, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("12345", false, "AKTORID")
                ),
                "navn navn"
            )
            coEvery { narmestelederClient.getNarmesteledere(any()) } returns emptyList()

            every { db.updateFnr(any(), any()) } returns 1

            val endreFnr = EndreFnr(fnr = "12345678912", nyttFnr = "12345678913")

            with(
                handleRequest(HttpMethod.Post, "/api/sykmelding/fnr") {
                    addHeader("Content-Type", "application/json")
                    addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                    setBody(objectMapper.writeValueAsString(endreFnr))
                }
            ) {
                response.status() shouldBeEqualTo HttpStatusCode.OK
                response.content shouldBeEqualTo "Vellykket oppdatering."
            }
        }
    }
})
