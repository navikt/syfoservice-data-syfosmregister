package no.nav.syfo.aksessering.db.oracle

import java.sql.ResultSet
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.db.toList

data class DatabaseResult<T>(
    val lastIndex: Int,
    val rows: List<T>
)

fun DatabaseInterfaceOracle.hentFravaerForSykmelding(sporsmalId: Int): DatabaseResult<MutableMap<String, Any?>> {
    connection.use { connection ->
        connection.prepareStatement(
            """
                 select * from sm_sporsmal_periode where sm_sporsmal_id = ?
                """
        ).use {
            it.setInt(1, sporsmalId)
            val resultSet = it.executeQuery()
            val databaseResult = resultSet.toJsonStringSyfoServiceFravaer()
            return databaseResult
        }
    }
}

fun DatabaseInterfaceOracle.hentSykmeldingerSyfoService(lastIndex: Int, limit: Int): DatabaseResult<MutableMap<String, Any?>> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT * FROM SYKMELDING_DOK sd 
                left outer join sm_arbeidsgiver sa on (sa.arbeidsgiver_id = sd.arbeidsgiver_id)
                left outer join sm_sporsmal sp on (sd.sykmelding_dok_id = sp.sykmelding_id)
                WHERE SYKMELDING_DOK_ID > ?
                ORDER BY SYKMELDING_DOK_ID ASC
                FETCH NEXT ? ROWS ONLY
                """
        ).use {
            it.setInt(1, lastIndex)
            it.setInt(2, limit)
            val resultSet = it.executeQuery()
            val databaseResult = resultSet.toJsonStringSyfoService(lastIndex)
            return databaseResult
        }
    }

fun ResultSet.toJsonStringSyfoServiceFravaer(previusIndex: Int = 0): DatabaseResult<MutableMap<String, Any?>> {
    val listOfRows = ArrayList<HashMap<String, Any?>>()

    val metadata = this.metaData
    val columns = metadata.columnCount
    while (this.next()) {
        val rowMap = HashMap<String, Any?>()
        for (i in 1..columns) {
            var data: Any?
            if (metadata.getColumnClassName(i).contains("oracle.sql.TIMESTAMP")) {
                data = getTimestamp(i)
            } else {
                data = getObject(i)
            }
            rowMap[metadata.getColumnName(i)] = data
        }
        listOfRows.add(rowMap)
    }
    return DatabaseResult(0, listOfRows)
}

fun ResultSet.toJsonStringSyfoService(previusIndex: Int): DatabaseResult<MutableMap<String, Any?>> {
    val listOfRows = ArrayList<MutableMap<String, Any?>>()

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
            } else if (metadata.getColumnName(i) == "HAR_FRAVAER") {
                data = getString(i)
            } else if (metadata.getColumnName(i) == "SM_SPORSMAL_ID") {
                data = getInt(i)
            }
            else {
                data = getObject(i)
            }
            rowMap[metadata.getColumnName(i)] = data
        }
        listOfRows.add(rowMap)
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
