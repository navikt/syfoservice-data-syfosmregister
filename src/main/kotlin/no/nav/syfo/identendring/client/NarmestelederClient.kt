package no.nav.syfo.identendring.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import java.time.LocalDate
import no.nav.syfo.clients.AccessTokenClientV2
import no.nav.syfo.log

class NarmestelederClient(
    private val httpClient: HttpClient,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val baseUrl: String,
    private val resource: String
) {

    suspend fun getNarmesteledere(fnr: String): List<NarmesteLeder> {
        try {
            val token = accessTokenClientV2.getAccessTokenV2(resource)
            log.info(token)
            return httpClient.get<List<NarmesteLeder>>("$baseUrl/sykmeldt/narmesteledere") {
                headers {
                    append(HttpHeaders.Authorization, token)
                    append("Sykmeldt-Fnr", fnr)
                }
                accept(ContentType.Application.Json)
            }
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av nærmeste leder")
            throw e
        }
    }
}

data class NarmesteLeder(
    val narmesteLederFnr: String,
    val orgnummer: String,
    val narmesteLederTelefonnummer: String,
    val narmesteLederEpost: String,
    val aktivFom: LocalDate,
    val aktivTom: LocalDate?,
    val arbeidsgiverForskutterer: Boolean?
)
