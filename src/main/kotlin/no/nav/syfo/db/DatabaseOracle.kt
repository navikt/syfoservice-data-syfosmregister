package no.nav.syfo.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.syfo.VaultConfig
import no.nav.syfo.VaultCredentials
import java.sql.Connection
import java.sql.ResultSet

class DatabaseOracle(
    private val vaultConfig: VaultConfig,
    private val vaultCredentialService: VaultCredentials
) : DatabaseInterfaceOracle {

    private val dataSource: HikariDataSource

    override val connection: Connection
        get() = dataSource.connection

    init {
        dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = vaultConfig.jdbcUrl
                username = vaultCredentialService.databaseUsername
                password = vaultCredentialService.databasePassword
                maximumPoolSize = 3
                isAutoCommit = false
                driverClassName = "oracle.jdbc.OracleDriver"
                validate()
            }
        )
    }
}

fun <T> ResultSet.toList(mapper: ResultSet.() -> T): List<T> = mutableListOf<T>().apply {
    while (next()) {
        add(mapper())
    }
}

interface DatabaseInterfaceOracle {
    val connection: Connection
}
