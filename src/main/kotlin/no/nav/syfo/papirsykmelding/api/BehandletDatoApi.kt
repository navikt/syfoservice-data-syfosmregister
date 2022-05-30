package no.nav.syfo.papirsykmelding.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.time.LocalDate

fun Route.registrerBehandletDatoApi(updateBehandletDatoService: UpdateBehandletDatoService) {
    post("/api/papirsykmelding/{sykmeldingId}/behandletdato") {
        val sykmeldingId = call.parameters["sykmeldingId"]!!
        if (sykmeldingId.isNullOrEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Sykmeldingid må være satt")
        }
        val behandletDatoDTO = call.receive<BehandletDatoDTO>()

        updateBehandletDatoService.updateBehandletDato(sykmeldingId = sykmeldingId, behandletDato = behandletDatoDTO.behandletDato)
        call.respond(HttpStatusCode.OK)
    }
}

data class BehandletDatoDTO(
    val behandletDato: LocalDate
)
