package no.nav.syfo.persistering.db.postgres

import no.nav.syfo.db.DatabaseInterfacePostgresUtenVault
import no.nav.syfo.db.toList
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate

fun DatabaseInterfacePostgresUtenVault.hentAntallSykmeldinger(): List<AntallSykmeldingerBackup> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                        SELECT COUNT(MOTTAK_ID) AS antall
                        FROM SYKMELDINGSOPPLYSNINGER
                        """
        ).use {
            it.executeQuery().toList { toAntallSykmeldingerBackup() }
        }
    }

data class AntallSykmeldingerBackup(
    val antall: String
)

fun ResultSet.toAntallSykmeldingerBackup(): AntallSykmeldingerBackup =
    AntallSykmeldingerBackup(
        antall = getString("antall")
    )

fun DatabaseInterfacePostgresUtenVault.hentSykmeldingsIderUtenBehandlingsutfall(
    lastMottattTidspunkt: LocalDate
): List<String> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                select sm.id from sykmeldingsopplysninger sm 
                where NOT exists(select 1 from behandlingsutfall where id = sm.id)
                AND sm.epj_system_navn != 'SYFOSERVICE'
                AND sm.mottatt_tidspunkt >= ?
                AND sm.mottatt_tidspunkt < ?
                """
        ).use {
            it.setTimestamp(1, Timestamp.valueOf(lastMottattTidspunkt.atStartOfDay()))
            it.setTimestamp(2, Timestamp.valueOf(lastMottattTidspunkt.plusDays(1).atStartOfDay()))
            it.executeQuery().toList { hentId() }
        }
    }

fun ResultSet.hentId(): String {
    return getString("id")
}
