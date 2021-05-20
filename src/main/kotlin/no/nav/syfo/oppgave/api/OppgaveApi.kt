package no.nav.syfo.oppgave.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.syfo.log
import no.nav.syfo.oppgave.OppgaveClient
import java.util.UUID

fun Route.registerHentOppgaverApi(oppgaveClient: OppgaveClient) {
    post("/api/oppgave/list") {

        val callId = UUID.randomUUID().toString()

        val ids = call.receive<List<String>>()
        if (ids.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Listen med oppgaveId-er kan ikke være tom")
        }

        try {
            log.info("Henter oppgaver fra Oppgave-api {}", ids)

            ids.forEach {
                when {
                    it.toIntOrNull() == null -> {
                        call.respond(HttpStatusCode.BadRequest, "$it er ikke et nummer")
                    }
                }
            }

            val toList = ids.map {
                oppgaveClient.hentOppgave(oppgaveId = it.toInt(), msgId = callId)
            }.toList()

            call.respond(HttpStatusCode.OK, toList)

        } catch (e: Exception) {
            log.error("Kastet exception ved enting av oppgaver fra oppgave-api", e)
            call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt ved henting av oppgave")
        }
    }
}