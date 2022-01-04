package no.nav.syfo.manuell

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.db.DatabasePostgresManuell
import no.nav.syfo.db.toList
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.toPGObject
import no.nav.syfo.objectMapper
import java.sql.ResultSet

fun DatabasePostgresManuell.oppdaterManuellOppgave(manuellOppgave: ManuellOppgave) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE MANUELLOPPGAVE
            SET validationresult = ?,
                opprinnelig_validationresult = ?
            WHERE id = ?;
            """
        ).use {
            it.setObject(1, manuellOppgave.validationResult.toPGObject())
            it.setObject(2, manuellOppgave.opprinneligValidationResult!!.toPGObject())
            it.setString(3, manuellOppgave.sykmeldingId)
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabasePostgresManuell.hentAktuelleManuellOppgaver(): List<ManuellOppgave> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                select validationresult, id, opprinnelig_validationresult
                from manuelloppgave 
                where ferdigstilt=true and validationresult->>'status' = 'MANUAL_PROCESSING';
                """
        ).use {
            it.executeQuery().toList { toManuellOppgave() }
        }
    }

fun ResultSet.toManuellOppgave(): ManuellOppgave =
    ManuellOppgave(
        validationResult = objectMapper.readValue(getString("validationresult")),
        sykmeldingId = getString("id"),
        opprinneligValidationResult = getString("opprinnelig_validationresult")?.let { objectMapper.readValue<ValidationResult>(it) }
    )
