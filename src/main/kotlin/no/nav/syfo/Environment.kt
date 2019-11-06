package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val sm2013SyfoserviceSykmeldingTopic: String = getEnvVar("KAFKA_SM2013_SYFOSERVICE_SYKMELDING_TOPIC", "privat-syfo-sm2013-syfoservice-sykmelding"),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfoservice-data-syfosmregister"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL")
) : KafkaConfig

data class VaultCredentials(
    val databaseUsername: String,
    val databasePassword: String
)

data class VaultServiceUser(
    val serviceuserUsername: String,
    val serviceuserPassword: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

data class VaultConfig(
    val jdbcUrl: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
