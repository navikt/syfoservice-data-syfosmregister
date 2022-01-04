package no.nav.syfo.sparenaproxy

import no.nav.syfo.db.DatabaseSparenaproxyPostgres
import no.nav.syfo.db.toList
import java.sql.Timestamp
import java.time.LocalDate
import java.time.OffsetDateTime

fun DatabaseSparenaproxyPostgres.getPlanlagte8Ukersmeldinger(lastOpprettetTidspunkt: OffsetDateTime): List<PlanlagtMeldingDbModel> =
    connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT * FROM planlagt_melding WHERE type='8UKER' AND opprettet>=? AND opprettet<?;
            """
        ).use {
            it.setTimestamp(1, Timestamp.from(lastOpprettetTidspunkt.toInstant()))
            it.setTimestamp(2, Timestamp.from(lastOpprettetTidspunkt.plusDays(1).toInstant()))
            it.executeQuery().toList { toPlanlagtMeldingDbModel() }
        }
    }

fun DatabaseSparenaproxyPostgres.planlagt4UkersmeldingFinnes(fnr: String, startdato: LocalDate): Boolean =
    connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT 1 FROM planlagt_melding WHERE fnr=? AND startdato=? AND type=?;
            """
        ).use {
            it.setString(1, fnr)
            it.setObject(2, startdato)
            it.setString(3, BREV_4_UKER_TYPE)
            it.executeQuery().next()
        }
    }

fun DatabaseSparenaproxyPostgres.lagrePlanlagtMelding(planlagtMeldingDbModel: PlanlagtMeldingDbModel) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            INSERT INTO planlagt_melding(
                id,
                fnr,
                startdato,
                type,
                opprettet,
                sendes,
                avbrutt)
            VALUES (?, ?, ?, ?, ?, ?, ?)
             """
        ).use {
            it.setObject(1, planlagtMeldingDbModel.id)
            it.setString(2, planlagtMeldingDbModel.fnr)
            it.setObject(3, planlagtMeldingDbModel.startdato)
            it.setString(4, planlagtMeldingDbModel.type)
            it.setTimestamp(5, java.sql.Timestamp.from(planlagtMeldingDbModel.opprettet.toInstant()))
            it.setTimestamp(6, java.sql.Timestamp.from(planlagtMeldingDbModel.sendes.toInstant()))
            it.setTimestamp(7, planlagtMeldingDbModel.avbrutt?.let { java.sql.Timestamp.from(planlagtMeldingDbModel.avbrutt.toInstant()) })
            it.execute()
        }
        connection.commit()
    }
}
