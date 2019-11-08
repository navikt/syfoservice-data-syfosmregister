package no.nav.syfo.aksessering.db.postgres

import java.sql.ResultSet
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.db.toList

data class DatabaseResult(
    val lastIndex: Int,
    val rows: List<String>,
    var databaseTime: Double = 0.0,
    var processingTime: Double = 0.0
)

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

data class AntallSykmeldinger(
    val antall: String
)

fun ResultSet.toAntallSykmeldinger(): AntallSykmeldinger =
    AntallSykmeldinger(
        antall = getString("antall")
    )
