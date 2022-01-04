package no.nav.syfo.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.syfo.Environment
import java.sql.Connection

class DatabasePostgresManuell(
    private val env: Environment,
    private val vaultCredentialService: VaultCredentialService
) :
    DatabaseInterfacePostgres {
    private val dataSource: HikariDataSource

    override val connection: Connection
        get() = dataSource.connection

    init {

        val initialCredentials = vaultCredentialService.getNewCredentials(
            mountPath = env.mountPathVault,
            databaseName = env.databaseNameManuell,
            role = Role.USER
        )
        dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = env.manuellDbUrl
                username = initialCredentials.username
                password = initialCredentials.password
                maximumPoolSize = 1
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                driverClassName = "org.postgresql.Driver"
                validate()
            }
        )

        vaultCredentialService.renewCredentialsTaskData = RenewCredentialsTaskData(
            dataSource = dataSource,
            mountPath = env.mountPathVault,
            databaseName = env.databaseNameManuell,
            role = Role.USER
        )
    }
}
