package no.nav.syfo.sykmelding.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.syfo.log
import no.nav.syfo.papirsykmelding.DiagnoseService
import no.nav.syfo.papirsykmelding.tilsyfoservice.SendTilSyfoserviceService
import no.nav.syfo.sykmelding.api.model.EndreDiagnose

fun Route.registerSendToSyfoserviceApi(sendTilSyfoserviceService: SendTilSyfoserviceService) {
    post("/api/sykmelding/{sykmeldingId}/syfoservice") {
        val sykmeldingId = call.parameters["sykmeldingId"]!!
        if (sykmeldingId.isNullOrEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Sykmeldingid må være satt")
        }

        try {
            sendTilSyfoserviceService.sendTilSyfoservice(sykmeldingId)
            call.respond(HttpStatusCode.OK)
        } catch (e: Exception) {
            log.error("Kastet exception ved sending av sykmelding med id {} til syfoservice: {}", sykmeldingId, e)
            call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt ved sending til syfoservice")
        }
    }
}

fun Route.registerUpdateDiagnosisApi(diagnoseService: DiagnoseService) {
    post("/api/sykmelding/{sykmeldingId}/diagnose") {
        val sykmeldingId = call.parameters["sykmeldingId"]!!
        if (sykmeldingId.isNullOrEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Sykmeldingid må være satt")
        }

        val diagnoseDTO = call.receive<EndreDiagnose>()

        try {
            diagnoseService.endreDiagnose(sykmeldingId, diagnoseKode = diagnoseDTO.kode, system = diagnoseDTO.system)
            call.respond(HttpStatusCode.OK)
        } catch (e: Exception) {
            log.error("Kastet exception ved endring av diagnose for sykmelding med id {$sykmeldingId}, {$e}")
            call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt ved oppdatering av diagnose")
        }
    }
}
