package no.nav.syfo.manuell

import no.nav.syfo.db.DatabasePostgresManuell
import no.nav.syfo.log

class ValidationResultService(
    private val databasePostgres: DatabasePostgresManuell
) {
    fun run() {
        val aktuelleOppgaver = databasePostgres.hentAktuelleManuellOppgaver()
        log.info("Fant ${aktuelleOppgaver.size} oppgaver som skal patches")

        /*aktuelleOppgaver.forEach {
            log.info("Oppdaterer oppgave med id ${it.oppgaveid}")
            val oppdatertOppgave = ManuellOppgave(
                validationResult = ValidationResult(Status.OK, emptyList()),
                oppgaveid = it.oppgaveid,
                opprinneligValidationResult = it.validationResult
            )
            databasePostgres.oppdaterManuellOppgave(oppdatertOppgave)
        }*/
        log.info("Ferdig med patching")
    }
}
