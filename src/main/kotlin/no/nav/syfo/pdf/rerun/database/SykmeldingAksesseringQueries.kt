package no.nav.syfo.pdf.rerun.database

import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.ResultSet
import java.util.UUID
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.db.toList
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.objectMapper

fun DatabaseInterfacePostgres.getSykmeldingerByIds(sykmeldingIds: List<String>): List<ReceivedSykmelding> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT *
                FROM SYKMELDINGSOPPLYSNINGER as OPPLYSNINGER
                         INNER JOIN SYKMELDINGSDOKUMENT as DOKUMENT on OPPLYSNINGER.id = DOKUMENT.id
                WHERE OPPLYSNINGER.id = ANY (?);
                """
        ).use {
            it.setArray(1, connection.createArrayOf("VARCHAR", sykmeldingIds.toTypedArray()))
            it.executeQuery().toList { toReceivedSykmelding() }
        }
    }

fun ResultSet.toReceivedSykmelding(): ReceivedSykmelding {
    val sykmelding: no.nav.syfo.model.Sykmelding = objectMapper.readValue(getString("sykmelding"))
    return ReceivedSykmelding(
            sykmelding = sykmelding,
            tssid = getString("tss_id"),
            msgId = sykmelding.msgId,
            personNrPasient = getString("pasient_fnr"),
            personNrLege = getString("lege_fnr"),
            navLogId = UUID.randomUUID().toString(),
            mottattDato = getTimestamp("mottatt_tidspunkt").toLocalDateTime(),
            legekontorReshId = getString("legekontor_resh_id"),
            legekontorOrgNr = getString("legekontor_org_nr"),
            legekontorHerId = getString("legekontor_her_id"),
            fellesformat = "",
            legekontorOrgName = "", tlfPasient = null, rulesetVersion = null, merknader = null)
}
