package no.nav.syfo.oppgave.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import no.nav.syfo.log
import no.nav.syfo.oppgave.OppgaveClient
import java.util.UUID

fun Route.registerHentOppgaveApi(oppgaveClient: OppgaveClient) {
    get("/api/oppgave/{oppgaveId}") {

        val callId = UUID.randomUUID().toString()

        val oppgaveId = call.parameters["oppgaveId"]?.toIntOrNull()
        if (oppgaveId == null) {
            call.respond(HttpStatusCode.BadRequest, "oppgaveId må være satt")
        }
        try {
            val oppgave = oppgaveClient.hentOppgave(oppgaveId = oppgaveId!!, msgId = callId)
            call.respond(HttpStatusCode.OK, oppgave)
        } catch (e: Exception) {
            log.error("Kastet exception ved enting av oppgave med id {} fra oppgave-api: {}", oppgaveId, e.message)
            call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt ved henting av oppgave")
        }
    }
}

fun Route.registerHentOppgaverApi(oppgaveClient: OppgaveClient) {
    post("/api/oppgave/list") {

        val callId = UUID.randomUUID().toString()

        val ids = call.receive<List<String>>()
        if (ids.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Listen med oppgaveId-er kan ikke være tom")
        }

        try {
            val toList = ids.map {
                oppgaveClient.hentOppgave(oppgaveId = it.toInt(), msgId = callId)
            }.toList()

            call.respond(HttpStatusCode.OK, toList)

        } catch (e: Exception) {
            log.error("Kastet exception ved enting av oppgaverfra oppgave-api: {}", e.message)
            call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt ved henting av oppgave")
        }
    }
}