package no.nav.syfo.sykmelding.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.patch
import no.nav.syfo.sykmelding.UpdateFnrService

fun Route.registerFnrApi(updateFnrService: UpdateFnrService) {
    patch("/api/sykmelding/{sykmeldingId}") {
        val sykmeldingId = call.parameters["sykmeldingId"]!!
        if (sykmeldingId.isNullOrEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Sykmeldingid må være satt")
        }

        val fnrMap = call.receive<Map<String, String>>()

        when (val fnr = fnrMap["fnr"]) {
            null -> call.respond(HttpStatusCode.BadRequest, "fnr må være satt")
            else ->
                when (fnr.length) {
                    11 -> updateFnrService.updateFnr(sykmeldingId, fnr)
                    else -> call.respond(HttpStatusCode.BadRequest, "fnr må være 11 siffer")
                }
        }

        call.respond(HttpStatusCode.OK)
    }
}
