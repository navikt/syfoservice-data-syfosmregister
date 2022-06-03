package no.nav.syfo

import no.nav.syfo.kafka.KafkaCredentials
import no.nav.syfo.utils.getFileAsString

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfoservice-data-syfosmregister"),
    val syfosmregisterDBURL: String = getEnvVar("SYFOSMREGISTER_DB_URL"),
    val pale2DBURL: String = getEnvVar("PALE_2_REGISTER_DB_URL"),
    val sparenaproxyDBURL: String = getEnvVar("SPARENAPROXY_DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "syfosmregister"),
    val databasePaleName: String = getEnvVar("DATABASE_PALE_NAME", "pale-2-register"),
    val databaseSparenaproxyName: String = getEnvVar("DATABASE_SPARENAPROXY_NAME", "sparenaproxy"),
    val lastIndexSyfoservice: Int = getEnvVar("LAST_INDEX_SYFOSERVICE").toInt(),
    val lastIndexSyfosmregister: String = getEnvVar("LAST_INDEX_SYFOSMREGISTER"),
    val lastIndexEia: Int = getEnvVar("LAST_INDEX_EIA").toInt(),
    val lastIndexSparenaproxy: String = getEnvVar("LAST_INDEX_SPARENAPROXY"),
    val lastIndexBackup: String = getEnvVar("LAST_INDEX_BACKUP"),
    val sendSykmeldingV2Topic: String = "teamsykmelding.syfo-sendt-sykmelding",
    val bekreftSykmeldingV2KafkaTopic: String = "teamsykmelding.syfo-bekreftet-sykmelding",
    val mottattSykmeldingV2Topic: String = "teamsykmelding.syfo-mottatt-sykmelding",
    val aivenEndringsloggTopic: String = "teamsykmelding.macgyver-sykmelding-endringslogg",
    val securityTokenUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL", "http://security-token-service/rest/v1/sts/token"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val lastIndexNlSyfoservice: Int = getEnvVar("LAST_INDEX_NL_SYFOSERVICE").toInt(),
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
    val sykmeldingBucketName: String = getEnvVar("SYKMELDING_BUCKET_NAME"),
    val paleBucketName: String = getEnvVar("PALE_VEDLEGG_BUCKET_NAME"),
    val historiskTopic: String = "teamsykmelding.sykmelding-historisk",
    val legeerklaringTopic: String = "teamsykmelding.legeerklaering",
    val pale2Bucket: String = getEnvVar("PALE_BUCKET_NAME"),
    val narmestelederRequestTopic: String = "teamsykmelding.syfo-nl-request",
    val papirSmRegistreringTopic: String = "teamsykmelding.papir-sm-registering",
    val manuellTopic: String = "teamsykmelding.sykmelding-manuell"
)

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
