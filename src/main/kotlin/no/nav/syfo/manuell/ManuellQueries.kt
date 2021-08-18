package no.nav.syfo.manuell

import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.ResultSet
import no.nav.syfo.db.DatabasePostgresManuell
import no.nav.syfo.db.toList
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.toPGObject
import no.nav.syfo.objectMapper

fun DatabasePostgresManuell.oppdaterManuellOppgave(manuellOppgave: ManuellOppgave) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE MANUELLOPPGAVE
            SET validationresult = ?,
                opprinnelig_validationresult = ?
            WHERE oppgaveid = ?;
            """
        ).use {
            it.setObject(1, manuellOppgave.validationResult.toPGObject())
            it.setObject(2, manuellOppgave.opprinneligValidationResult!!.toPGObject())
            it.setInt(3, manuellOppgave.oppgaveid)
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabasePostgresManuell.hentAktuelleManuellOppgaver(): List<ManuellOppgave> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                select validationresult, oppgaveid, opprinnelig_validationresult
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
        oppgaveid = getInt("oppgaveid"),
        opprinneligValidationResult = getString("opprinnelig_validationresult")?.let { objectMapper.readValue<ValidationResult>(it) }
    )
