package no.nav.syfo.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import no.nav.syfo.Environment

enum class Role {
    ADMIN, USER, READONLY;

    override fun toString() = name.toLowerCase()
}

class DatabasePostgres(
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
            databaseName = env.databaseName,
            role = Role.USER
        )
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = env.syfosmregisterDBURL
            username = initialCredentials.username
            password = initialCredentials.password
            maximumPoolSize = 3
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        })

        vaultCredentialService.renewCredentialsTaskData = RenewCredentialsTaskData(
            dataSource = dataSource,
            mountPath = env.mountPathVault,
            databaseName = env.databaseName,
            role = Role.USER
        )
    }
}

interface DatabaseInterfacePostgres {
    val connection: Connection
}
