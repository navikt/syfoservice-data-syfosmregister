package no.nav.syfo.sykmelding.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.delete
import no.nav.syfo.log
import no.nav.syfo.sykmelding.DeleteSykmeldingService

fun Route.registerDeleteSykmeldingApi(deleteSykmeldingService: DeleteSykmeldingService) {
    delete("/api/sykmelding/{sykmeldingId}") {
        val sykmeldingId = call.parameters["sykmeldingId"]!!
        if (sykmeldingId.isNullOrEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Sykmeldingid må være satt")
        }
        try {
            deleteSykmeldingService.deleteSykmelding(sykmeldingId)
            call.respond(HttpStatusCode.OK)
        } catch (e: Exception) {
            log.error("Kastet exception ved sending av sykmelding med id {} til syfoservice: {}", sykmeldingId, e)
            call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt ved sending til syfoservice")
        }
    }
}
