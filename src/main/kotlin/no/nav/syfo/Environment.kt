package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
import no.nav.syfo.utils.getFileAsString

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val sm2013SyfoserviceSykmeldingTopic: String = getEnvVar("KAFKA_SM2013_SYFOSERVICE_SYKMELDING_TOPIC", "privat-syfo-sm2013-syfoservice-sykmelding"),
    val sm2013ReceivedSykmelding: String = getEnvVar("KAFKA_SM2013_RECEIVED_SYKMELDING", "privat-syfo-sm2013-syfoservice-received-sykmelding"),
    val sm2013EiaSykmedlingTopic: String = getEnvVar("KAFKA_SM2013_EIA_SYKMELDING_TOPIC", "privat-syfo-sm2013-eia-sykmeldinger"),
    val sm2013SyfoSericeSykmeldingStatusTopic: String = getEnvVar("KAFKA_SM2013_SYFOSERVICE_SYKMELDING_STATUS_TOPIC", "privat-syfo-sm2013-syfoservice-sykmelding-status-2"),
    val sm2013SyfoSericeSykmeldingArbeidsgiverTopic: String = getEnvVar("KAFKA_SM2013_SYFOSERVICE_SYKMELDING_ARBEIDSGIVER_TOPIC", "private-syfoservice-arbeidsgiver"),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfoservice-data-syfosmregister"),
    val syfosmregisterDBURL: String = getEnvVar("SYFOSMREGISTER_DB_URL"),
    val pale2DBURL: String = getEnvVar("PALE_2_REGISTER_DB_URL"),
    val sparenaproxyDBURL: String = getEnvVar("SPARENAPROXY_DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    override val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "syfosmregister"),
    val databasePaleName: String = getEnvVar("DATABASE_PALE_NAME", "pale-2-register"),
    val databaseSparenaproxyName: String = getEnvVar("DATABASE_SPARENAPROXY_NAME", "sparenaproxy"),
    val lastIndexSyfoservice: Int = getEnvVar("LAST_INDEX_SYFOSERVICE").toInt(),
    val lastIndexSyfosmregister: String = getEnvVar("LAST_INDEX_SYFOSMREGISTER"),
    val lastIndexEia: Int = getEnvVar("LAST_INDEX_EIA").toInt(),
    val lastIndexSparenaproxy: String = getEnvVar("LAST_INDEX_SPARENAPROXY"),
    val sykmeldingCleanTopic: String = getEnvVar("SYKEMLDING_CLEAN_TOPIC", "privat-syfoservice-clean-sykmelding"),
    val sykmeldingCleanTopicFull: String = getEnvVar("SYKEMLDING_CLEAN_TOPIC_FULL", "privat-syfoservice-clean-sykmelding-full"),
    val receivedSykmeldingBackupTopic: String = getEnvVar("SYKMELDING_RECEIVED_SM_BACKUP", "privat-syfosmregister-received-sykmelding-backup"),
    val behandlingsutfallBackupTopic: String = getEnvVar("SYKMELDING_BEHANDLINGSUTFALL_BACKUP", "privat-syfosmregister-behandlingsutfall-backup"),
    val oppgaveTopic: String = getEnvVar("OPPGAVE_TOPIC", "privat-syfo-oppgave-registrerOppgave"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val idUtenBehandlingsutfallFraBackupTopic: String = getEnvVar("SYKMELDINGID_UTEN_BEHANDLINGSUTFALL", "privat-syfosmregister-id-uten-behandlingsutfall"),
    val lastIndexBackup: String = getEnvVar("LAST_INDEX_BACKUP"),
    val rerunTopic: String = getEnvVar("RERUN_TOPIC", "privat-syfo-register-rerun-tmp"),
    val sendSykmeldingTopic: String = "syfo-sendt-sykmelding",
    val bekreftSykmeldingKafkaTopic: String = "syfo-bekreftet-sykmelding",
    val mottattSykmeldingTopic: String = "syfo-mottatt-sykmelding",
    val sendSykmeldingV2Topic: String = "teamsykmelding.syfo-sendt-sykmelding",
    val bekreftSykmeldingV2KafkaTopic: String = "teamsykmelding.syfo-bekreftet-sykmelding",
    val mottattSykmeldingV2Topic: String = "teamsykmelding.syfo-mottatt-sykmelding",
    val sykmeldingStatusTopic: String = "aapen-syfo-sykmeldingstatus-leesah-v1",
    val sykmeldingStatusBackupTopic: String = "privat-syfo-register-status-backup",
    val pale2dump: String = "privat-syfo-pale2-dump-v1",
    val pale2RerunTopic: String = "pale-2-rerun-v1",
    val syfoserviceKafkaTopic: String = "teamsykmelding.syfoservice-mq",
    val endringsloggTopic: String = "privat-sykmelding-endringslogg",
    val securityTokenUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL", "http://security-token-service/rest/v1/sts/token"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    override val truststore: String? = getEnvVar("NAV_TRUSTSTORE_PATH"),
    override val truststorePassword: String? = getEnvVar("NAV_TRUSTSTORE_PASSWORD"),
    val lastIndexNlSyfoservice: Int = getEnvVar("LAST_INDEX_NL_SYFOSERVICE").toInt(),
    val nlMigreringTopic: String = "teamsykmelding.syfo-nl-migrering",
    val nlResponseTopic: String = "teamsykmelding.syfo-narmesteleder",
    val oppgavebehandlingUrl: String = getEnvVar("OPPGAVEBEHANDLING_URL"),
    val aadAccessTokenV2Url: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientIdV2: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecretV2: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    val manuellDbUrl: String = getEnvVar("SYFOSMMANUELL_BACKEND_DB_URL"),
    val databaseNameManuell: String = "syfosmmanuell-backend",
    val narmestelederUrl: String = getEnvVar("NARMESTELEDER_URL"),
    val narmestelederScope: String = getEnvVar("NARMESTELEDER_SCOPE"),
    val aivenSykmeldingStatusTopic: String = "teamsykmelding.sykmeldingstatus-leesah",
    val vedleggTopic: String = "privat-syfo-vedlegg",
    val sykmeldingBucketName: String = getEnvVar("SYKMELDING_BUCKET_NAME"),
    val paleBucketName: String = getEnvVar("PALE_BUCKET_NAME")
) : KafkaConfig

data class VaultCredentials(
    val databaseUsername: String,
    val databasePassword: String
    // val backupDbUsername: String,
    // val backupDbPassword: String
)

/*data class VaultSecrets(
    val fnr: String = getEnvVar("FNR")
)*/

data class VaultServiceUser(
    val serviceuserUsername: String,
    val serviceuserPassword: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

data class JwtVaultSecrets(
    val internalJwtWellKnownUri: String = getEnvVar("JWT_WELLKNOWN_URI"),
    val clientId: String = getFileAsString("/secrets/azuread/syfoservice-data-syfosmregister/client_id"),
    val jwtIssuer: String = getEnvVar("JWT_ISSUER")
)

data class VaultConfig(
    val jdbcUrl: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
