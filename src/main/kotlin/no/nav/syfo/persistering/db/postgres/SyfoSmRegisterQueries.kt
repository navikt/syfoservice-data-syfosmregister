package no.nav.syfo.persistering.db.postgres

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.db.toList
import no.nav.syfo.model.Eia
import no.nav.syfo.model.SykmeldingStatusEvent
import no.nav.syfo.model.Sykmeldingsdokument
import no.nav.syfo.model.Sykmeldingsopplysninger
import no.nav.syfo.model.toPGObject

data class DatabaseResult(
    val lastIndex: Int,
    val rows: List<String>,
    var databaseTime: Double = 0.0,
    var processingTime: Double = 0.0
)

fun Connection.opprettSykmeldingsopplysninger(sykmeldingsopplysninger: Sykmeldingsopplysninger) {
    use { connection ->
        connection.prepareStatement(
            """
            INSERT INTO SYKMELDINGSOPPLYSNINGER(
                id,
                pasient_fnr,
                pasient_aktoer_id,
                lege_fnr,
                lege_aktoer_id,
                mottak_id,
                legekontor_org_nr,
                legekontor_her_id,
                legekontor_resh_id,
                epj_system_navn,
                epj_system_versjon,
                mottatt_tidspunkt,
                tss_id)
            VALUES  (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
        ).use {
            it.setString(1, sykmeldingsopplysninger.id)
            it.setString(2, sykmeldingsopplysninger.pasientFnr)
            it.setString(3, sykmeldingsopplysninger.pasientAktoerId)
            it.setString(4, sykmeldingsopplysninger.legeFnr)
            it.setString(5, sykmeldingsopplysninger.legeAktoerId)
            it.setString(6, sykmeldingsopplysninger.mottakId)
            it.setString(7, sykmeldingsopplysninger.legekontorOrgNr)
            it.setString(8, sykmeldingsopplysninger.legekontorHerId)
            it.setString(9, sykmeldingsopplysninger.legekontorReshId)
            it.setString(10, sykmeldingsopplysninger.epjSystemNavn)
            it.setString(11, sykmeldingsopplysninger.epjSystemVersjon)
            it.setTimestamp(12, Timestamp.valueOf(sykmeldingsopplysninger.mottattTidspunkt))
            it.setString(13, sykmeldingsopplysninger.tssid)
            it.executeUpdate()
        }

        connection.commit()
    }
}

fun Connection.opprettSykmeldingsdokument(sykmeldingsdokument: Sykmeldingsdokument) {
    use { connection ->
        connection.prepareStatement(
            """
            INSERT INTO SYKMELDINGSDOKUMENT(id, sykmelding) VALUES  (?, ?)
            """
        ).use {
            it.setString(1, sykmeldingsdokument.id)
            it.setObject(2, sykmeldingsdokument.sykmelding.toPGObject())
            it.executeUpdate()
        }

        connection.commit()
    }
}

fun DatabaseInterfacePostgres.hentAntallSykmeldinger(): List<AntallSykmeldinger> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                        SELECT COUNT(MOTTAK_ID) AS antall
                        FROM SYKMELDINGSOPPLYSNINGER
                        """
        ).use {
            it.executeQuery().toList { toAntallSykmeldinger() }
        }
    }

fun Connection.erSykmeldingsopplysningerLagret(id: String, mottakId: String) =
    use { connection ->
        connection.prepareStatement(
            """
                SELECT *
                FROM SYKMELDINGSOPPLYSNINGER
                WHERE id = ? OR mottak_id = ?;
                """
        ).use {
            it.setString(1, id)
            it.setString(2, mottakId)
            it.executeQuery().next()
        }
    }

fun DatabaseInterfacePostgres.registerStatus(sykmeldingStatusEvent: SykmeldingStatusEvent) {
    connection.use { connection ->
        connection.prepareStatement(
            """
                    INSERT INTO sykmeldingstatus(sykmelding_id, event_timestamp, event) VALUES (?, ?, ?)
                    """
        ).use {
            it.setString(1, sykmeldingStatusEvent.id)
            it.setTimestamp(2, Timestamp.valueOf(sykmeldingStatusEvent.timestamp))
            it.setString(3, sykmeldingStatusEvent.event.name)
            it.execute()
        }
        connection.commit()
    }
}

fun Connection.oppdaterSykmeldingsopplysninger(eia: Eia) {
    use { connection ->
        connection.prepareStatement(
            """
                UPDATE SYKMELDINGSOPPLYSNINGER
                SET pasient_fnr = ?,
                    lege_fnr = ?,
                    legekontor_org_nr = ?,
                    legekontor_her_id = ?,
                    legekontor_resh_id = ?
                WHERE
                mottak_id = ?
            """
        ).use {
            it.setString(1, eia.pasientfnr)
            it.setString(2, eia.legefnr)
            it.setString(3, eia.legekontorOrgnr)
            it.setString(4, eia.legekontorHer)
            it.setString(5, eia.legekontorResh)
            it.setString(6, eia.mottakid)
            it.executeUpdate()
        }

        connection.commit()
    }
}

data class AntallSykmeldinger(
    val antall: String
)

fun ResultSet.toAntallSykmeldinger(): AntallSykmeldinger =
    AntallSykmeldinger(
        antall = getString("antall")
    )
