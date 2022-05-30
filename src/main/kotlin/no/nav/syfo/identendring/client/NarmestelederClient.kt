package no.nav.syfo.identendring.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.syfo.clients.AccessTokenClientV2
import no.nav.syfo.log
import java.time.LocalDate

class NarmestelederClient(
    private val httpClient: HttpClient,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val baseUrl: String,
    private val resource: String
) {

    suspend fun getNarmesteledere(fnr: String): List<NarmesteLeder> {
        try {
            val token = accessTokenClientV2.getAccessTokenV2(resource)
            return httpClient.get("$baseUrl/sykmeldt/narmesteledere") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append("Sykmeldt-Fnr", fnr)
                }
                accept(ContentType.Application.Json)
            }.body<List<NarmesteLeder>>()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av nærmeste leder")
            throw e
        }
    }

    suspend fun getNarmestelederKoblingerForLeder(lederFnr: String): List<NarmesteLeder> {
        try {
            val token = accessTokenClientV2.getAccessTokenV2(resource)
            return httpClient.get("$baseUrl/leder/narmesteleder/aktive") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append("Narmeste-Leder-Fnr", lederFnr)
                }
                accept(ContentType.Application.Json)
            }.body<List<NarmesteLeder>>()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av nærmesteleder-koblinger for leder")
            throw e
        }
    }
}

data class NarmesteLeder(
    val fnr: String,
    val narmesteLederFnr: String,
    val orgnummer: String,
    val narmesteLederTelefonnummer: String,
    val narmesteLederEpost: String,
    val aktivFom: LocalDate,
    val aktivTom: LocalDate?,
    val arbeidsgiverForskutterer: Boolean?
)
