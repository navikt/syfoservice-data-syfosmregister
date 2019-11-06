package no.nav.syfo.aksessering.db

import java.io.StringReader
import java.sql.ResultSet
import java.time.LocalDateTime
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.toSykmelding
import no.nav.syfo.objectMapper
import no.nav.syfo.utils.fellesformatUnmarshaller

data class DatabaseResult(val lastIndex: Int, val rows: List<String>, var databaseTime: Double = 0.0, var processingTime: Double = 0.0)

fun DatabaseInterface.hentSykmeldinger(lastIndex: Int, limit: Int): DatabaseResult =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT * FROM SYKMELDING_DOK 
                WHERE SYKMELDING_DOK_ID > ?
                ORDER BY SYKMELDING_DOK_ID ASC
                FETCH NEXT ? ROWS ONLY
                """
        ).use {
            it.fetchSize = 10_000
            it.setInt(1, lastIndex)
            it.setInt(2, limit)
            val currentMillies = System.currentTimeMillis()
            val resultSet = it.executeQuery()
            val databaseEndMillies = System.currentTimeMillis()
            val databaseResult = resultSet.toJsonString(lastIndex)
            val processingMillies = System.currentTimeMillis()

            databaseResult.databaseTime = (databaseEndMillies - currentMillies) / 1000.0
            databaseResult.processingTime = (processingMillies - databaseEndMillies) / 1000.0
            return databaseResult
        }
    }

fun ResultSet.toJsonString(previusIndex: Int): DatabaseResult {
    val listOfRows = ArrayList<String>()

    val metadata = this.metaData
    val columns = metadata.columnCount
    var lastIndex = previusIndex
    while (this.next()) {
        val rowMap = HashMap<String, Any?>()
        lastIndex = getInt("SYKMELDING_DOK_ID")
        for (i in 1..columns) {
            var data: Any?
            if (metadata.getColumnClassName(i).contains("oracle.sql.TIMESTAMP")) {
                data = getTimestamp(i)
            } else if (metadata.getColumnClassName(i).contains("oracle.sql.CLOB") || metadata.getColumnName(i) == "DOKUMENT") {
                data = getString(i)
            } else {
                data = getObject(i)
            }
            rowMap[metadata.getColumnName(i)] = data
        }
        listOfRows.add(objectMapper.writeValueAsString(rowMap))
    }
    return DatabaseResult(lastIndex, listOfRows.map { objectMapper.writeValueAsString(it) })
}

fun ResultSet.toReceivedSykmelding(): ReceivedSykmelding =
    ReceivedSykmelding(
        sykmelding = unmarshallerToHealthInformation(getString("dokument")).toSykmelding(
            sykmeldingId = getString("melding_id"),
            pasientAktoerId = getString("aktor_id"),
            legeAktoerId = "",
            msgId = "",
            signaturDato = getTimestamp("created").toLocalDateTime()
        ),
        personNrPasient = unmarshallerToHealthInformation(getString("dokument")).pasient.fodselsnummer.id,
        tlfPasient = unmarshallerToHealthInformation(getString("dokument")).pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
        personNrLege = "",
        navLogId = getString("mottak_id"),
        msgId = "",
        legekontorOrgNr = "",
        legekontorOrgName = "",
        legekontorHerId = "",
        legekontorReshId = "",
        mottattDato = LocalDateTime.now(),
        rulesetVersion = unmarshallerToHealthInformation(getString("dokument")).regelSettVersjon,
        fellesformat = "",
        tssid = ""
    )

fun unmarshallerToHealthInformation(healthInformation: String): HelseOpplysningerArbeidsuforhet =
    fellesformatUnmarshaller.unmarshal(StringReader(healthInformation)) as HelseOpplysningerArbeidsuforhet

fun DatabaseInterface.hentAntallSykmeldinger(): List<AntallSykmeldinger> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                        SELECT COUNT(MOTTAK_ID) AS antall
                        FROM SYKMELDING_DOK
                        WHERE created < to_timestamp('2019-11-04','YYYY-MM-DD')
                        """
        ).use {
            it.executeQuery().toList { toAntallSykmeldinger() }
        }
    }

data class AntallSykmeldinger(
    val antall: String
)

fun ResultSet.toAntallSykmeldinger(): AntallSykmeldinger =
    AntallSykmeldinger(
        antall = getString("antall")
    )
