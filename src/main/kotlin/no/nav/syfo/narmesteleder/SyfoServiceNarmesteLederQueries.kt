package no.nav.syfo.narmesteleder

import java.sql.ResultSet
import no.nav.syfo.aksessering.db.oracle.DatabaseResult
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.db.toList

fun DatabaseInterfaceOracle.hentNarmesteLederSyfoService(
    lastIndex: Int,
    limit: Int
): DatabaseResult<SyfoServiceNarmesteLeder> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT NAERMESTE_LEDER_ID, 
                AKTOR_ID, 
                ORGNUMMER, 
                NL_AKTOR_ID, 
                NL_TELEFONNUMMER, 
                NL_EPOST, 
                AKTIV_FOM,
                AKTIV_TOM,
                AG_FORSKUTTERER FROM NAERMESTE_LEDER
                WHERE NAERMESTE_LEDER_ID > ?
                AND NAERMESTE_LEDER_ID <= ?
                ORDER BY NAERMESTE_LEDER_ID ASC
                FETCH NEXT ? ROWS ONLY
                """
        ).use {
            it.setInt(1, lastIndex)
            it.setInt(2, limit + lastIndex)
            it.setInt(3, limit)
            val resultSet = it.executeQuery()
            return DatabaseResult(
                lastIndex = limit + lastIndex,
                rows = resultSet.toList { mapToSyfoServiceNarmesteLeder() })
        }
    }

private fun ResultSet.mapToSyfoServiceNarmesteLeder() =
    SyfoServiceNarmesteLeder(
        id = getLong("NAERMESTE_LEDER_ID"),
        aktorId = getString("AKTOR_ID"),
        orgnummer = getString("ORGNUMMER"),
        nlAktorId = getString("NL_AKTOR_ID"),
        nlTelefonnummer = getString("NL_TELEFONNUMMER"),
        nlEpost = getString("NL_EPOST"),
        aktivFom = getDate("AKTIV_FOM").toLocalDate(),
        aktivTom = getDate("AKTIV_TOM")?.toLocalDate(),
        agForskutterer = getBoolean("AG_FORSKUTTERER")
    )

fun DatabaseInterfaceOracle.hentAntallNarmesteLederKoblinger(): List<AntallNarmesteLederKoblinger> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                        SELECT COUNT(NAERMESTE_LEDER_ID) AS antall
                        FROM NAERMESTE_LEDER
                        """
        ).use {
            it.executeQuery().toList { toAntallNarmesteLederKoblinger() }
        }
    }

data class AntallNarmesteLederKoblinger(
    val antall: String
)

fun ResultSet.toAntallNarmesteLederKoblinger(): AntallNarmesteLederKoblinger =
    AntallNarmesteLederKoblinger(
        antall = getString("antall")
    )

fun DatabaseInterfaceOracle.finnSisteNarmesteLeder(): String =
    connection.use { connection ->
        connection.prepareStatement(
            """
                        SELECT NAERMESTE_LEDER_ID 
                        FROM NAERMESTE_LEDER 
                        ORDER BY NAERMESTE_LEDER_ID DESC 
                        FETCH FIRST 1 ROWS ONLY
                        """
        ).use {
            it.executeQuery().toList { getString("NAERMESTE_LEDER_ID") }.first()
        }
    }
