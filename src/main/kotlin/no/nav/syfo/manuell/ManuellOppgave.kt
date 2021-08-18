package no.nav.syfo.manuell

import no.nav.syfo.model.ValidationResult

data class ManuellOppgave(
    val validationResult: ValidationResult,
    val oppgaveid: Int,
    val opprinneligValidationResult: ValidationResult?
)
