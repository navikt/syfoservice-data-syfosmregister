package no.nav.syfo.papirsykmelding.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.syfo.sykmelding.model.Periode

fun Route.registrerPeriodeApi(updatePeriodeService: UpdatePeriodeService) {
    post("/api/papirsykmelding/{sykmeldingId}/periode") {
        val sykmeldingId = call.parameters["sykmeldingId"]!!
        if (sykmeldingId.isNullOrEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Sykmeldingid må være satt")
        }

        val periodeListeDTO = call.receive<PeriodeListeDTO>()
        if (periodeListeDTO.periodeliste.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Periodelisten kan ikke være tom")
        }
        periodeListeDTO.periodeliste.forEach {
            if (it.tom.isBefore(it.fom)) {
                call.respond(HttpStatusCode.BadRequest, "FOM må være før TOM")
            }
        }

        updatePeriodeService.updatePeriode(sykmeldingId = sykmeldingId, periodeliste = periodeListeDTO.periodeliste)
        call.respond(HttpStatusCode.OK)
    }
}

data class PeriodeListeDTO(
    val periodeliste: List<Periode>
)
