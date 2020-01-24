package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val sm2013SyfoserviceSykmeldingTopic: String = getEnvVar("KAFKA_SM2013_SYFOSERVICE_SYKMELDING_TOPIC", "privat-syfo-sm2013-syfoservice-sykmelding"),
    val sm2013ReceivedSykmelding: String = getEnvVar("KAFKA_SM2013_RECEIVED_SYKMELDING", "privat-syfo-sm2013-syfoservice-received-sykmelding"),
    val sm2013EiaSykmedlingTopic: String = getEnvVar("KAFKA_SM2013_EIA_SYKMELDING_TOPIC", "privat-syfo-sm2013-eia-sykmeldinger"),
    val sm2013SyfoSericeSykmeldingStatusTopic: String = getEnvVar("KAFKA_SM2013_SYFOSERVICE_SYKMELDING_STATUS_TOPIC", "privat-syfo-sm2013-syfoservice-sykmelding-status-2"),
    val sm2013SyfoSericeSykmeldingArbeidsgiverTopic: String = getEnvVar("KAFKA_SM2013_SYFOSERVICE_SYKMELDING_ARBEIDSGIVER_TOPIC", "private-syfoservice-arbeidsgiver"),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfoservice-data-syfosmregister"),
    val syfosmregisterDBURL: String = getEnvVar("SYFOSMREGISTER_DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "syfosmregister"),
    val lastIndexSyfoservice: Int = getEnvVar("LAST_INDEX_SYFOSERVICE").toInt(),
    val lastIndexSyfosmregister: String = getEnvVar("LAST_INDEX_SYFOSMREGISTER"),
    val lastIndexEia: Int = getEnvVar("LAST_INDEX_EIA").toInt(),
    val sykmeldingCleanTopic: String = getEnvVar("SYKEMLDING_CLEAN_TOPIC", "privat-syfoservice-clean-sykmelding"),
    val sykmeldingCleanTopicFull: String = getEnvVar("SYKEMLDING_CLEAN_TOPIC_FULL", "privat-syfoservice-clean-sykmelding-full"),
    val receivedSykmeldingBackupTopic: String = getEnvVar("SYKMELDING_RECEIVED_SM_BACKUP", "privat-syfosmregister-received-sykmelding-backup"),
    val behandlingsutfallBackupTopic: String = getEnvVar("SYKMELDING_BEHANDLINGSUTFALL_BACKUP", "privat-syfosmregister-behandlingsutfall-backup"),
    val oppgaveTopic: String = getEnvVar("OPPGAVE_TOPIC", "privat-syfo-oppgave-registrerOppgave"),
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
