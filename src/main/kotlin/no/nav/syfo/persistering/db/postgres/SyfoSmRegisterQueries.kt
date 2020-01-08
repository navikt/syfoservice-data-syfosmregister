package no.nav.syfo.persistering.db.postgres

import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp
import java.time.LocalDateTime
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.db.toList
import no.nav.syfo.log
import no.nav.syfo.model.ArbeidsgiverStatus
import no.nav.syfo.model.Behandlingsutfall
import no.nav.syfo.model.Eia
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Sporsmal
import no.nav.syfo.model.StatusEvent
import no.nav.syfo.model.Svar
import no.nav.syfo.model.SykmeldingStatusEvent
import no.nav.syfo.model.Sykmeldingsdokument
import no.nav.syfo.model.Sykmeldingsopplysninger
import no.nav.syfo.model.toPGObject
import no.nav.syfo.model.toSykmeldingsdokument
import no.nav.syfo.model.toSykmeldingsopplysninger
import no.nav.syfo.objectMapper

data class DatabaseResult(
    val lastIndex: Int,
    val rows: List<String>,
    var databaseTime: Double = 0.0,
    var processingTime: Double = 0.0
)

fun Connection.lagreReceivedSykmelding(receivedSykmelding: ReceivedSykmelding) {
    use { connection ->
        insertSykmeldingsopplysninger(connection, toSykmeldingsopplysninger(receivedSykmelding))
        insertSykmeldingsdokument(connection, toSykmeldingsdokument(receivedSykmelding))
        connection.commit()
    }
}

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
        it.setString(6, convertToMottakid(sykmeldingsopplysninger.mottakId))
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

fun Connection.lagreBehandlingsutfall(behandlingsutfall: Behandlingsutfall) =
    use { connection ->
        connection.prepareStatement(
            """
                    INSERT INTO BEHANDLINGSUTFALL(id, behandlingsutfall) VALUES (?, ?) ON CONFLICT DO NOTHING
                """
        ).use {
            it.setString(1, behandlingsutfall.id)
            it.setObject(2, behandlingsutfall.behandlingsutfall.toPGObject())
            it.executeUpdate()
        }
        connection.commit()
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
                WHERE id = ?
                """
        ).use {
            it.setString(1, id)
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

fun Connection.insertArbeidsgiver(arbeidsgiverStatus: ArbeidsgiverStatus) {
    this.prepareStatement(
        """
                INSERT INTO arbeidsgiver(sykmelding_id, orgnummer, juridisk_orgnummer, navn) VALUES (?, ?, ?, ?)
                """
    ).use {
        it.setString(1, arbeidsgiverStatus.sykmeldingId)
        it.setString(2, arbeidsgiverStatus.orgnummer)
        it.setString(3, arbeidsgiverStatus.juridiskOrgnummer)
        it.setString(4, arbeidsgiverStatus.orgnavn)
        it.execute()
    }
}

fun DatabaseInterfacePostgres.oppdaterSykmeldingStatus(sykmeldingStatusEvents: List<SykmeldingStatusEvent>) {
    this.connection.use { connection ->
        connection.prepareStatement(
            """                
                INSERT INTO sykmeldingstatus
                (sykmelding_id, event_timestamp, event)
                VALUES (?, ?, ?)
                on conflict do nothing
            """
        ).use {
            for (status in sykmeldingStatusEvents) {

                it.setString(1, status.sykmeldingId)
                it.setTimestamp(2, Timestamp.valueOf(status.timestamp))
                it.setString(3, status.event.name)
                it.addBatch()
            }
            it.executeBatch()
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
                where sm.mottak_id = ?
            """
        ).use {
            it.setString(1, mottakId)
            it.executeQuery().toSykmelding(mottakId)
        }
    }

fun Connection.hentSykmeldingListeMedBehandlingsutfall(mottakId: String): List<SykmeldingBehandlingsutfallDbModel> =
    use { connection ->
        connection.prepareStatement(
            """
                select * from sykmeldingsopplysninger sm 
                LEFT OUTER JOIN behandlingsutfall bu on sm.id = bu.id
                where sm.mottak_id = ?
            """
        ).use {
            it.setString(1, mottakId)
            it.executeQuery().toList { toSykmeldingMedBehandlingsutfall() }
        }
    }

fun Connection.hentSykmeldingIdManglerBehandlingsutfall(msgId: String): String? =
    use { connection ->
        connection.prepareStatement(
            """
                select sd.id from sykmeldingsdokument sd
                where NOT exists(select 1 from behandlingsutfall where id = sd.id) AND sd.sykmelding->>'msgId' = ?;
            """
        ).use {
            it.setString(1, msgId)
            it.executeQuery().getId()
        }
    }

fun Connection.sykmeldingHarBehandlingsutfall(sykmeldingId: String): Boolean =
    use { connection ->
        connection.prepareStatement(
            """
                SELECT 1 FROM behandlingsutfall WHERE id=?
            """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeQuery().next()
        }
    }

fun ResultSet.getId(): String? {
    return if (next()) {
        getString("id")
    } else null
}

fun Connection.hentSykmeldingMedId(sykmeldingId: String): SykmeldingDbModel? =
    use { connection ->
        connection.prepareStatement(
            """
                select * from sykmeldingsopplysninger sm 
                INNER JOIN sykmeldingsdokument sd on sm.id = sd.id
                where sm.epj_system_navn = 'SYFOSERVICE' and sm.id = ?
            """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toSykmelding(sykmeldingId)
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

fun Connection.slettSykmeldingOgStatus(
    id: String
) {
    use { connection ->
        connection.prepareStatement(
            """
            delete from sykmeldingstatus where sykmelding_id = ?
        """
        ).use {
            it.setString(1, id)
            it.execute()
        }
        connection.prepareStatement(
            """
            delete from sykmeldingsopplysninger where id = ?
        """
        ).use {
            it.setString(1, id)
            it.execute()
        }
        connection.commit()
    }
}

fun Connection.opprettBehandlingsutfall(behandlingsutfall: Behandlingsutfall) =
    use { connection ->
        connection.prepareStatement(
            """
                    INSERT INTO BEHANDLINGSUTFALL(id, behandlingsutfall) VALUES (?, ?)
                """
        ).use {
            it.setString(1, behandlingsutfall.id)
            it.setObject(2, behandlingsutfall.behandlingsutfall.toPGObject())
            it.executeUpdate()
        }

        connection.commit()
    }

fun DatabaseInterfacePostgres.getStatusesForSykmelding(id: String): List<SykmeldingStatusEvent> =
    this.connection.use { connection ->
        connection.prepareStatement(
            """
           select * from sykmeldingstatus where sykmelding_id = ? order by event_timestamp 
        """
        ).use {
            it.setString(1, id)
            it.executeQuery().toList { toStatusEvent() }
        }
    }

fun ResultSet.toStatusEvent(): SykmeldingStatusEvent {
    return SykmeldingStatusEvent(
        getString("sykmelding_id"),
        getTimestamp("event_timestamp").toLocalDateTime(),
        StatusEvent.valueOf(getString("event"))
    )
}

fun DatabaseInterfacePostgres.deleteSykmeldingStatus(sykmeldingId: String, kafkaTimestamp: LocalDateTime) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            DELETE FROM sykmeldingstatus WHERE sykmelding_id = ? AND event_timestamp < ?
        """
        ).use {
            it.setString(1, sykmeldingId)
            it.setTimestamp(2, Timestamp.valueOf(kafkaTimestamp))
            it.execute()
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

fun ResultSet.toSykmeldingMedBehandlingsutfall(): SykmeldingBehandlingsutfallDbModel {
    val sykmeldingId = getString("id")
    val behandlingsutfall = if (getString("behandlingsutfall") != null) Behandlingsutfall(
        sykmeldingId,
        objectMapper.readValue(getString("behandlingsutfall"))
    ) else null
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
    return SykmeldingBehandlingsutfallDbModel(sykmeldingsopplysninger, behandlingsutfall)
}

fun Connection.lagreSporsmalOgSvar(sporsmal: Sporsmal) {
    var spmId: Int?
    spmId = this.finnSporsmal(sporsmal)
    if (spmId == null) {
        spmId = this.lagreSporsmal(sporsmal)
    }
    this.lagreSvar(spmId, sporsmal.svar)
}

private fun Connection.finnSporsmal(sporsmal: Sporsmal): Int? {

    this.prepareStatement(
        """
                SELECT sporsmal.id
                FROM sporsmal
                WHERE shortName=? AND tekst=?;
                """
    ).use {
        it.setString(1, sporsmal.shortName.name)
        it.setString(2, sporsmal.tekst)
        val rs = it.executeQuery()
        return if (rs.next()) rs.getInt(1) else null
    }
}

private fun Connection.lagreSporsmal(sporsmal: Sporsmal): Int {
    var spmId: Int? = null
    this.prepareStatement(
        """
                INSERT INTO sporsmal(shortName, tekst) VALUES (?, ?)
                """,
        Statement.RETURN_GENERATED_KEYS
    ).use {
        it.setString(1, sporsmal.shortName.name)
        it.setString(2, sporsmal.tekst)
        it.execute()
        if (it.generatedKeys.next()) {
            spmId = it.generatedKeys.getInt(1)
        }
    }

    return spmId ?: throw RuntimeException("Fant ikke id for spørsmål som nettopp ble lagret")
}

private fun Connection.lagreSvar(sporsmalId: Int, svar: Svar) {
    this.prepareStatement(
        """
                INSERT INTO svar(sykmelding_id, sporsmal_id, svartype, svar) VALUES (?, ?, ?, ?)
                """
    ).use {
        it.setString(1, svar.sykmeldingId)
        it.setInt(2, sporsmalId)
        it.setString(3, svar.svartype.name)
        it.setString(4, svar.svar)
        it.execute()
    }
}

fun Connection.hentArbeidsgiverStatus(sykmeldingId: String): List<ArbeidsgiverStatus> =
    use { connection ->
        connection.prepareStatement(
            """
                 SELECT orgnummer,
                        juridisk_orgnummer,
                        navn,
                        sykmelding_id
                   FROM arbeidsgiver
                  WHERE sykmelding_id = ?
            """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toList { tilArbeidsgiverStatus() }
        }
    }

fun ResultSet.tilArbeidsgiverStatus(): ArbeidsgiverStatus =
    ArbeidsgiverStatus(
        sykmeldingId = getString("sykmelding_id"),
        orgnummer = getString("orgnummer"),
        juridiskOrgnummer = getString("juridisk_orgnummer"),
        orgnavn = getString("navn")
    )

fun DatabaseInterfacePostgres.svarFinnesFraFor(sykmeldingId: String): Boolean =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT 1 FROM svar WHERE sykmelding_id=?;
                """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeQuery().next()
        }
    }

private fun Connection.slettAlleSvar(sykmeldingId: String) {
    this.slettSvar(sykmeldingId)
}

private fun Connection.slettSvar(sykmeldingId: String) {
    this.prepareStatement(
        """
                DELETE FROM svar WHERE sykmelding_id=?;
                """
    ).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}

fun DatabaseInterfacePostgres.slettOgInsertArbeidsgiver(
    sykmeldingId: String,
    sporsmals: List<Sporsmal>,
    arbeidsgiverStatus: ArbeidsgiverStatus
) {
    connection.use { connection ->
        connection.slettAlleSvar(sykmeldingId)
        sporsmals.forEach { sporsmal ->
            connection.lagreSporsmalOgSvar(sporsmal)
        }
        connection.insertArbeidsgiver(arbeidsgiverStatus)
        connection.commit()
    }
}

fun DatabaseInterfacePostgres.lagreSporsmalOgSvarOgArbeidsgiver(
    sporsmals: List<Sporsmal>,
    arbeidsgiverStatus: ArbeidsgiverStatus?
) {
    connection.use { connection ->
        sporsmals.forEach { sporsmal ->
            connection.lagreSporsmalOgSvar(sporsmal)
        }
        if (arbeidsgiverStatus != null) {
            connection.insertArbeidsgiver(arbeidsgiverStatus)
        }
        connection.commit()
    }
}
