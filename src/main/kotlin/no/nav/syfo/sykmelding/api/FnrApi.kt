package no.nav.syfo.sykmelding.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.*
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.syfo.sykmelding.UpdateFnrService
import no.nav.syfo.sykmelding.api.model.EndreFnr
import no.nav.syfo.utils.getAccessTokenFromAuthHeader

fun Route.registerFnrApi(updateFnrService: UpdateFnrService) {
    post("/api/sykmelding/fnr") {

        val endreFnr = call.receive<EndreFnr>()
        when {
            endreFnr == null -> {
                call.respond(HttpStatusCode.BadRequest, "Klarte ikke tolke forespørsel")
            }
            endreFnr.fnr.length != 11 || endreFnr.fnr.any { !it.isDigit() } -> {
                // Hvis fnr ikke er et tall på 11 tegn så er det antakeligvis noe rart som har skjedd,
                // og vi bør undersøke ytterligere
                call.respond(HttpStatusCode.BadRequest, "fnr må være et fnr / dnr på 11 tegn")
            }
            endreFnr.nyttFnr.length != 11 || endreFnr.nyttFnr.any { !it.isDigit() } -> {
                call.respond(HttpStatusCode.BadRequest, "nyttFnr må være et fnr / dnr på 11 tegn")
            }
            else -> {
                val accessToken = getAccessTokenFromAuthHeader(call.request)!!
                val updateFnr = updateFnrService.updateFnr(accessToken = accessToken, fnr = endreFnr.fnr, nyttFnr = endreFnr.nyttFnr)
                if (updateFnr) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotModified)
                }
            }
        }
    }
}
