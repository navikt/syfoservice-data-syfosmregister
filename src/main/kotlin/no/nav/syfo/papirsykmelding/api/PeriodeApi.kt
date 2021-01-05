package no.nav.syfo.papirsykmelding.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import java.time.LocalDate

fun Route.registrerPeriodeApi(updatePeriodeService: UpdatePeriodeService) {
    post("/api/papirsykmelding/{sykmeldingId}/periode") {
        val sykmeldingId = call.parameters["sykmeldingId"]!!
        if (sykmeldingId.isNullOrEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Sykmeldingid må være satt")
        }

        val periodeDTO = call.receive<PeriodeDTO>()
        if (periodeDTO.tom.isBefore(periodeDTO.fom)) {
            call.respond(HttpStatusCode.BadRequest, "FOM må være før TOM")
        }

        updatePeriodeService.updatePeriode(sykmeldingId = sykmeldingId, fom = periodeDTO.fom, tom = periodeDTO.tom)
        call.respond(HttpStatusCode.OK)
    }
}

data class PeriodeDTO(
    val fom: LocalDate,
    val tom: LocalDate
)
