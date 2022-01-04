package no.nav.syfo.oppgave.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.syfo.client.StsOidcClient
import java.time.LocalDate

class OppgaveClient(
    private val url: String,
    private val oidcClient: StsOidcClient,
    private val httpClient: HttpClient
) {

    suspend fun hentOppgave(oppgaveId: Int, msgId: String): Oppgave {

        val httpResponse = httpClient.get<HttpStatement>("$url/$oppgaveId") {
            contentType(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("X-Correlation-ID", msgId)
        }.execute()

        return when (httpResponse.status) {
            HttpStatusCode.OK -> {
                httpResponse.call.response.receive()
            }
            else -> {
                val msg = "OppgaveClient hentOppgave kastet feil ${httpResponse.status} ved hentOppgave av oppgave, response: ${httpResponse.call.response.receive<String>()}"
                throw RuntimeException(msg)
            }
        }
    }
}

data class Oppgave(
    val id: Int? = null,
    val versjon: Int? = null,
    val tildeltEnhetsnr: String? = null,
    val opprettetAvEnhetsnr: String? = null,
    val aktoerId: String? = null,
    val journalpostId: String? = null,
    val behandlesAvApplikasjon: String? = null,
    val saksreferanse: String? = null,
    val tilordnetRessurs: String? = null,
    val beskrivelse: String? = null,
    val tema: String? = null,
    val oppgavetype: String,
    val behandlingstype: String? = null,
    val aktivDato: LocalDate,
    val fristFerdigstillelse: LocalDate? = null,
    val prioritet: String,
    val status: String? = null,
    val mappeId: Int? = null
)
