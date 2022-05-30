package no.nav.syfo.narmesteleder.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.syfo.narmesteleder.NarmestelederService

fun Route.registrerNarmestelederRequestApi(narmestelederService: NarmestelederService) {
    post("/api/narmesteleder/request") {
        val nlRequest = call.receive<NlRequestDTO>()
        narmestelederService.sendNewNlRequest(nlRequest)
        call.respond(HttpStatusCode.OK)
    }
}
