package no.nav.syfo.persistering.db.postgres

import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.db.toList
import no.nav.syfo.log
import no.nav.syfo.model.Eia
import no.nav.syfo.model.SykmeldingStatusEvent
import no.nav.syfo.model.Sykmeldingsdokument
import no.nav.syfo.model.Sykmeldingsopplysninger
import no.nav.syfo.model.toPGObject
import no.nav.syfo.objectMapper

data class DatabaseResult(
    val lastIndex: Int,
    val rows: List<String>,
    var databaseTime: Double = 0.0,
    var processingTime: Double = 0.0
)

fun Connection.opprettSykmeldingsopplysninger(sykmeldingsopplysninger: Sykmeldingsopplysninger) {
    use { connection ->
        insertSykmeldingsopplysninger(connection, sykmeldingsopplysninger)

        connection.commit()
    }
}

private fun insertSykmeldingsopplysninger(
    connection: Connection,
    sykmeldingsopplysninger: Sykmeldingsopplysninger
) {
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
}

fun Connection.opprettSykmeldingsdokument(sykmeldingsdokument: Sykmeldingsdokument) {
    use { connection ->
        insertSykmeldingsdokument(connection, sykmeldingsdokument)

        connection.commit()
    }
}

private fun insertSykmeldingsdokument(
    connection: Connection,
    sykmeldingsdokument: Sykmeldingsdokument
) {
    connection.prepareStatement(
        """
            INSERT INTO SYKMELDINGSDOKUMENT(id, sykmelding) VALUES  (?, ?)
            """
    ).use {
        it.setString(1, sykmeldingsdokument.id)
        it.setObject(2, sykmeldingsdokument.sykmelding.toPGObject())
        it.executeUpdate()
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

fun Connection.oppdaterSykmeldingsopplysninger(listEia: List<Eia>) {
    use { connection ->
        connection.prepareStatement(
            """
                UPDATE SYKMELDINGSOPPLYSNINGER
                SET lege_fnr = ?,
                    legekontor_org_nr = ?,
                    legekontor_her_id = ?,
                    legekontor_resh_id = ?
                WHERE
                mottak_id = ?
            """
        ).use {
            for (eia in listEia) {
                it.setString(1, eia.legefnr)
                it.setString(2, eia.legekontorOrgnr)
                it.setString(3, eia.legekontorHer)
                it.setString(4, eia.legekontorResh)
                it.setString(5, eia.mottakid)
                it.addBatch()
            }
            it.executeBatch()
        }
        connection.commit()
    }
}

fun Connection.oppdaterSykmeldingStatus(sykmeldingStatusEvents: List<SykmeldingStatusEvent>) {
    use { connection ->
        connection.prepareStatement(
            """                
                INSERT INTO sykmeldingstatus
                (sykmelding_id, event_timestamp, event)
                SELECT id, ?, ?
                FROM sykmeldingsopplysninger where mottak_id = ? 
                on conflict do nothing
            """
        ).use {
            for (status in sykmeldingStatusEvents) {
                it.setTimestamp(1, Timestamp.valueOf(status.timestamp))
                it.setString(2, status.event.name)
                it.setString(3, convertToMottakid(status.mottakId))
                it.addBatch()
            }
            it.executeBatch()
//            log.info("Antall oppdateringer {}", numberOfUpdates.size)
        }

        connection.commit()
    }
}

fun convertToMottakid(mottakid: String): String =
    when (mottakid.length <= 63) {
        true -> mottakid
        else -> {
            log.info("Størrelsen på mottakid er: {}, mottakid: {}", mottakid.length, mottakid)
            mottakid.substring(0, 63)
        }
    }

data class AntallSykmeldinger(
    val antall: String
)

fun ResultSet.toAntallSykmeldinger(): AntallSykmeldinger =
    AntallSykmeldinger(
        antall = getString("antall")
    )

fun Connection.hentSykmelding(mottakId: String): SykmeldingDbModel? =
    use { connection ->
        connection.prepareStatement(
            """
                select * from sykmeldingsopplysninger sm 
                INNER JOIN sykmeldingsdokument sd on sm.id = sd.id
                where sm.mottak_id = ? and sm.epj_system_navn = 'SYFOSERVICE'
            """
        ).use {
            it.setString(1, mottakId)
            it.executeQuery().toSykmelding(mottakId)
        }
    }

fun Connection.deleteAndInsertSykmelding(
    oldId: String,
    sykmeldingDb: SykmeldingDbModel
) {
    use { connection ->
        connection.prepareStatement(
            """
            delete from sykmeldingstatus where sykmelding_id = ?
        """
        ).use {
            it.setString(1, oldId)
            it.execute()
        }
        connection.prepareStatement(
            """
            delete from sykmeldingsopplysninger where id = ?
        """
        ).use {
            it.setString(1, oldId)
            it.execute()
        }

        insertSykmeldingsopplysninger(connection, sykmeldingDb.sykmeldingsopplysninger)
        insertSykmeldingsdokument(connection, sykmeldingDb.sykmeldingsdokument)
        connection.commit()
    }
}

fun Connection.updateMottattTidspunkt(id: String, mottattTidspunkt: LocalDateTime) {
    use { connection ->
        connection.prepareStatement("""
            update sykmeldingsopplysninger set mottatt_tidspunkt = ?
            where id = ?
        """
        ).use {
            it.setTimestamp(1, Timestamp.valueOf(mottattTidspunkt))
            it.setString(2, id)
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun ResultSet.toSykmelding(mottakId: String): SykmeldingDbModel? {
    if (next()) {
        val sykmeldingId = getString("id")
        val sykmeldingsdokument =
            Sykmeldingsdokument(sykmeldingId, objectMapper.readValue(getString("sykmelding")))
        val sykmeldingsopplysninger = Sykmeldingsopplysninger(
            id = sykmeldingId,
            mottakId = getString("mottak_id"),
            pasientFnr = getString("pasient_fnr"),
            pasientAktoerId = getString("pasient_aktoer_id"),
            legeFnr = getString("lege_fnr"),
            legeAktoerId = getString("lege_aktoer_id"),
            legekontorOrgNr = getString("legekontor_org_nr"),
            legekontorHerId = getString("legekontor_her_id"),
            legekontorReshId = getString("legekontor_resh_id"),
            epjSystemNavn = getString("epj_system_navn"),
            epjSystemVersjon = getString("epj_system_versjon"),
            mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toLocalDateTime(),
            tssid = getString("tss_id")
        )
        return SykmeldingDbModel(sykmeldingsopplysninger, sykmeldingsdokument)
    }
    return null
}
