package no.nav.syfo.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.syfo.Environment
import no.nav.syfo.VaultCredentials
import java.sql.Connection

class DatabasePostgresUtenVault(
    private val env: Environment,
    private val vaultCredentials: VaultCredentials
) :
    DatabaseInterfacePostgresUtenVault {
    private val dataSource: HikariDataSource

    override val connection: Connection
        get() = dataSource.connection

    init {

        dataSource = HikariDataSource(
            HikariConfig().apply {
                // jdbcUrl = env.syfosmregisterBackupDBURL
                // username = vaultCredentials.backupDbUsername
                // password = vaultCredentials.backupDbPassword
                maximumPoolSize = 1
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                driverClassName = "org.postgresql.Driver"
                validate()
            }
        )
    }
}

interface DatabaseInterfacePostgresUtenVault {
    val connection: Connection
}
