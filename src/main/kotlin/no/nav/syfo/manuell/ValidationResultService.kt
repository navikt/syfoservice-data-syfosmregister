package no.nav.syfo.manuell

import no.nav.syfo.db.DatabasePostgresManuell
import no.nav.syfo.log
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult

class ValidationResultService(
    private val databasePostgres: DatabasePostgresManuell
) {
    fun run() {
        val aktuelleOppgaver = databasePostgres.hentAktuelleManuellOppgaver()
        log.info("Fant ${aktuelleOppgaver.size} oppgaver som skal patches")

        aktuelleOppgaver.forEach {
            log.info("Oppdaterer oppgave med id ${it.sykmeldingId}")
            val oppdatertOppgave = ManuellOppgave(
                validationResult = ValidationResult(Status.OK, emptyList()),
                sykmeldingId = it.sykmeldingId,
                opprinneligValidationResult = it.validationResult
            )
            databasePostgres.oppdaterManuellOppgave(oppdatertOppgave)
        }
        log.info("Ferdig med patching")
    }
}
