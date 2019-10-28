package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val databaseUrl: String = getEnvVar("DATABASE_URL")
)

data class VaultCredentials(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val databaseUsername: String,
    val databasePassword: String

)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
