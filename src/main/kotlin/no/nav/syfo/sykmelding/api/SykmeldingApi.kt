package no.nav.syfo.sykmelding.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.syfo.log
import no.nav.syfo.papirsykmelding.DiagnoseService
import no.nav.syfo.service.GjenapneSykmeldingService
import no.nav.syfo.sykmelding.api.model.EndreBiDiagnoseRequest
import no.nav.syfo.sykmelding.api.model.EndreDiagnose

fun Route.registerUpdateDiagnosisApi(diagnoseService: DiagnoseService) {
    post("/api/sykmelding/{sykmeldingId}/diagnose") {
        val sykmeldingId = call.parameters["sykmeldingId"]!!
        if (sykmeldingId.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Sykmeldingid må være satt")
        }

        val diagnoseDTO = call.receive<EndreDiagnose>()

        try {
            diagnoseService.endreDiagnose(sykmeldingId, diagnoseKode = diagnoseDTO.kode, system = diagnoseDTO.system)
            call.respond(HttpStatusCode.OK)
        } catch (e: Exception) {
            log.error("Kastet exception ved endring av diagnose for sykmelding med id $sykmeldingId, ${e.message}")
            call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt ved oppdatering av diagnose")
        }
    }
}

fun Route.registerUpdateBiDiagnosisApi(diagnoseService: DiagnoseService) {
    post("/api/sykmelding/{sykmeldingId}/bidiagnose") {
        val sykmeldingId = call.parameters["sykmeldingId"]!!
        if (sykmeldingId.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Sykmeldingid må være satt")
        }

        val diagnoseDTO = call.receive<EndreBiDiagnoseRequest>()

        try {
            diagnoseService.endreBiDiagnose(sykmeldingId, diagnoseDTO.diagnoser)
            call.respond(HttpStatusCode.OK)
        } catch (e: Exception) {
            log.error("Kastet exception ved endring av diagnose for sykmelding med id $sykmeldingId, ${e.message}")
            call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt ved oppdatering av diagnose")
        }
    }
}

fun Route.registerGjenapneSykmeldingApi(gjenapneSykmeldingService: GjenapneSykmeldingService) {
    post("/api/sykmelding/{sykmeldingId}/gjenapne") {
        val sykmeldingId = call.parameters["sykmeldingId"]!!
        if (sykmeldingId.isEmpty() || sykmeldingId == "null") {
            call.respond(HttpStatusCode.BadRequest, "Sykmeldingid må være satt")
        }

        try {
            gjenapneSykmeldingService.gjenapneSykmelding(sykmeldingId)
            call.respond(HttpStatusCode.OK)
        } catch (e: Exception) {
            log.error("Kastet exception ved gjenåpning av sykmelding med id $sykmeldingId, ${e.message}")
            call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt ved gjenåpning av sykmelding")
        }
    }
}
