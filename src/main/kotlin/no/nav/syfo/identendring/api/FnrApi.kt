package no.nav.syfo.identendring.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.syfo.identendring.UpdateFnrService
import no.nav.syfo.identendring.UpdateIdentException
import no.nav.syfo.sykmelding.api.model.EndreFnr

fun Route.registerFnrApi(updateFnrService: UpdateFnrService) {
    post("/api/sykmelding/fnr") {

        val endreFnr = call.receive<EndreFnr>()
        when {
            endreFnr.fnr.length != 11 || endreFnr.fnr.any { !it.isDigit() } -> {
                // Hvis fnr ikke er et tall på 11 tegn så er det antakeligvis noe rart som har skjedd,
                // og vi bør undersøke ytterligere
                call.respond(HttpStatusCode.BadRequest, "fnr må være et fnr / dnr på 11 tegn")
            }
            endreFnr.nyttFnr.length != 11 || endreFnr.nyttFnr.any { !it.isDigit() } -> {
                call.respond(HttpStatusCode.BadRequest, "nyttFnr må være et fnr / dnr på 11 tegn")
            }
            else -> {
                try {
                    val updateFnr = updateFnrService.updateFnr(fnr = endreFnr.fnr, nyttFnr = endreFnr.nyttFnr)

                    if (updateFnr) {
                        call.respond(HttpStatusCode.OK, "Vellykket oppdatering.")
                    } else {
                        call.respond(HttpStatusCode.NotModified, "Ingenting ble endret.")
                    }
                } catch (e: UpdateIdentException) {
                    call.respond(HttpStatusCode.InternalServerError, e.message)
                }
            }
        }
    }

    post("/api/leder/fnr") {

        val endreFnr = call.receive<EndreFnr>()
        when {
            endreFnr.fnr.length != 11 || endreFnr.fnr.any { !it.isDigit() } -> {
                // Hvis fnr ikke er et tall på 11 tegn så er det antakeligvis noe rart som har skjedd,
                // og vi bør undersøke ytterligere
                call.respond(HttpStatusCode.BadRequest, "fnr må være et fnr / dnr på 11 tegn")
            }
            endreFnr.nyttFnr.length != 11 || endreFnr.nyttFnr.any { !it.isDigit() } -> {
                call.respond(HttpStatusCode.BadRequest, "nyttFnr må være et fnr / dnr på 11 tegn")
            }
            else -> {
                try {
                    val updateNlKoblinger = updateFnrService.updateNlFnr(fnr = endreFnr.fnr, nyttFnr = endreFnr.nyttFnr)

                    if (updateNlKoblinger) {
                        call.respond(HttpStatusCode.OK, "Vellykket oppdatering.")
                    } else {
                        call.respond(HttpStatusCode.NotModified, "Ingenting ble endret.")
                    }
                } catch (e: UpdateIdentException) {
                    call.respond(HttpStatusCode.InternalServerError, e.message)
                }
            }
        }
    }
}
