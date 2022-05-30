package no.nav.syfo.oppgave.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.syfo.log
import no.nav.syfo.oppgave.client.OppgaveClient
import java.util.UUID

fun Route.registerHentOppgaverApi(oppgaveClient: OppgaveClient) {
    post("/api/oppgave/list") {

        val callId = UUID.randomUUID().toString()

        val ids = call.receive<List<Int>>()
        if (ids.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Listen med oppgaveId-er kan ikke være tom")
        }

        try {
            log.info("Henter oppgaver fra Oppgave-api {}", ids)

            val toList = ids.map {
                oppgaveClient.hentOppgave(oppgaveId = it, msgId = callId)
            }.toList()

            call.respond(HttpStatusCode.OK, toList)
        } catch (e: Exception) {
            log.error("Kastet exception ved enting av oppgaver fra oppgave-api", e)
            call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt ved henting av oppgave")
        }
    }
}
