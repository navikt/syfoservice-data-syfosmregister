package no.nav.syfo.narmesteleder.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.syfo.narmesteleder.NarmestelederService

fun Route.registrerNarmestelederRequestApi(narmestelederService: NarmestelederService) {
    post("/api/narmesteleder/request") {
        val nlRequest = call.receive<NlRequestDTO>()
        narmestelederService.sendNewNlRequest(nlRequest)
        call.respond(HttpStatusCode.OK)
    }
}
