package no.nav.syfo.sykmeldt.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.patch
import no.nav.syfo.sykmelding.UpdateFnrService
import no.nav.syfo.sykmeldt.model.EndreFnr
import no.nav.syfo.utils.getAccessTokenFromAuthHeader

fun Route.registerUserFnrApi(updateFnrService: UpdateFnrService) {
    patch("/api/sykmeldt") {

        val sykmeldingId = call.parameters["sykmeldingId"]!!
        if (sykmeldingId.isNullOrEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Sykmeldingid må være satt")
        }

        val endreFnr = call.receiveOrNull<EndreFnr>()

        when {
            endreFnr == null -> {
                call.respond(HttpStatusCode.BadRequest, "Klarte ikke tolke forespørsel")
            }
            endreFnr.fnr.length != 11 || endreFnr.fnr.toIntOrNull() == null -> {
                // Hvis fnr ikke er et tall på 11 tegn så er det antakeligvis noe rart som har skjedd,
                // og vi bør undersøke ytterligere
                call.respond(HttpStatusCode.BadRequest, "fnr må være et fnr / dnr på 11 tegn")
            }
            endreFnr.nyttFnr.length != 11 || endreFnr.nyttFnr.toIntOrNull() == null -> {
                call.respond(HttpStatusCode.BadRequest, "nyttFnr må være et fnr / dnr på 11 tegn")
            }
            else -> {
                val accessToken = getAccessTokenFromAuthHeader(call.request)!!
                val updateFnr = updateFnrService.updateFnr(accessToken, endreFnr.fnr, endreFnr.nyttFnr)
                if (updateFnr) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotModified)
                }
            }
        }
    }
}
