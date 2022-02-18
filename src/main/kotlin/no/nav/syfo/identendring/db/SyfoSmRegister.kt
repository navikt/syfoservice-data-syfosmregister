package no.nav.syfo.identendring.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.db.toList
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun DatabaseInterfacePostgres.updateFnr(fnr: String, nyttFnr: String): Int {
    connection.use { connection ->
        var updated: Int
        connection.prepareStatement(
            """
            UPDATE sykmeldingsopplysninger set pasient_fnr = ? where pasient_fnr = ?;
        """
        ).use {
            it.setString(1, nyttFnr)
            it.setString(2, fnr)
            updated = it.executeUpdate()
            log.info("Updated {} sykmeldingsdokument", updated)
        }
        connection.commit()
        return updated
    }
}

fun DatabaseInterfacePostgres.getSykmeldingerMedFnrUtenBehandlingsutfall(fnr: String): List<SykmeldingDbModelUtenBehandlingsutfall> =
    connection.use { connection ->
        return connection.getSykmeldingMedSisteStatusForFnrUtenBehandlingsutfall(fnr)
    }

private fun Connection.getSykmeldingMedSisteStatusForFnrUtenBehandlingsutfall(fnr: String): List<SykmeldingDbModelUtenBehandlingsutfall> =
    this.prepareStatement(
        """
                    SELECT opplysninger.id,
                    mottatt_tidspunkt,
                    legekontor_org_nr,
                    sykmelding,
                    status.event,
                    status.timestamp,
                    arbeidsgiver.orgnummer,
                    arbeidsgiver.juridisk_orgnummer,
                    arbeidsgiver.navn,
                    merknader
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                        LEFT OUTER JOIN arbeidsgiver as arbeidsgiver on arbeidsgiver.sykmelding_id = opplysninger.id
                        LEFT OUTER JOIN sykmeldingstatus AS status ON opplysninger.id = status.sykmelding_id AND
                                                                   status.timestamp = (SELECT timestamp
                                                                                             FROM sykmeldingstatus
                                                                                             WHERE sykmelding_id = opplysninger.id
                                                                                             ORDER BY timestamp DESC
                                                                                             LIMIT 1)
                    where opplysninger.pasient_fnr = ?
                    and not exists(select 1 from sykmeldingstatus where sykmelding_id = opplysninger.id and event in ('SLETTET'));
                    """
    ).use {
        it.setString(1, fnr)
        it.executeQuery().toList { toSykmeldingDbModelUtenBehandlingsutfall() }
    }

fun ResultSet.toSykmeldingDbModelUtenBehandlingsutfall(): SykmeldingDbModelUtenBehandlingsutfall {
    val mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toInstant().atOffset(ZoneOffset.UTC)
    return SykmeldingDbModelUtenBehandlingsutfall(
        sykmeldingsDokument = objectMapper.readValue(getString("sykmelding"), Sykmelding::class.java),
        id = getString("id"),
        mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toInstant().atOffset(ZoneOffset.UTC),
        legekontorOrgNr = getString("legekontor_org_nr"),
        status = getStatus(mottattTidspunkt),
        merknader = getString("merknader")?.let { objectMapper.readValue<List<Merknad>>(it) }
    )
}

private fun ResultSet.getStatus(mottattTidspunkt: OffsetDateTime): StatusDbModel {
    return when (val status = getString("event")) {
        null -> StatusDbModel(StatusEvent.APEN.name, mottattTidspunkt, null)
        else -> {
            val status_timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC)
            val arbeidsgiverDbModel = when (status) {
                StatusEvent.SENDT.name -> ArbeidsgiverDbModel(
                    orgnummer = getString("orgnummer"),
                    juridiskOrgnummer = getString("juridisk_orgnummer"),
                    orgNavn = getString("navn")
                )
                else -> null
            }
            return StatusDbModel(status, status_timestamp, arbeidsgiverDbModel)
        }
    }
}
