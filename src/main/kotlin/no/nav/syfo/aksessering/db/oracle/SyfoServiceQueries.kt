package no.nav.syfo.aksessering.db.oracle

import java.sql.ResultSet
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper

data class DatabaseResult<T>(val lastIndex: Int, val rows: List<T>, var databaseTime: Double = 0.0, var processingTime: Double = 0.0)

fun DatabaseInterfaceOracle.hentSykmeldingerSyfoService(lastIndex: Int, limit: Int): DatabaseResult<String> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT * FROM SYKMELDING_DOK 
                WHERE SYKMELDING_DOK_ID > ?
                ORDER BY SYKMELDING_DOK_ID ASC
                FETCH NEXT ? ROWS ONLY
                """
        ).use {
            it.setInt(1, lastIndex)
            it.setInt(2, limit)
            val currentMillies = System.currentTimeMillis()
            val resultSet = it.executeQuery()
            val databaseEndMillies = System.currentTimeMillis()
            val databaseResult = resultSet.toJsonStringSyfoService(lastIndex)
            val processingMillies = System.currentTimeMillis()

            databaseResult.databaseTime = (databaseEndMillies - currentMillies) / 1000.0
            databaseResult.processingTime = (processingMillies - databaseEndMillies) / 1000.0
            return databaseResult
        }
    }

fun ResultSet.toJsonStringSyfoService(previusIndex: Int): DatabaseResult<String> {
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
    return DatabaseResult(lastIndex, listOfRows)
}

fun DatabaseInterfaceOracle.hentAntallSykmeldingerSyfoService(): List<AntallSykmeldinger> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                        SELECT COUNT(MOTTAK_ID) AS antall
                        FROM SYKMELDING_DOK
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
