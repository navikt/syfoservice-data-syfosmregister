package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.kafka.EiaSykmeldingKafkaProducer
import no.nav.syfo.kafka.SykmeldingKafkaProducer
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.Eia
import no.nav.syfo.service.HentSykmeldingerFraEiaService
import no.nav.syfo.service.HentSykmeldingerFraSyfoServiceService
import no.nav.syfo.service.MapSykmeldingStringToSykemldignJsonMap
import no.nav.syfo.service.SkrivBehandlingsutfallTilSyfosmRegisterService
import no.nav.syfo.service.SkrivTilSyfosmRegisterServiceEia
import no.nav.syfo.service.SkrivTilSyfosmRegisterSyfoService
import no.nav.syfo.service.UpdateStatusService
import no.nav.syfo.utils.JacksonKafkaSerializer
import no.nav.syfo.utils.getFileAsString
import no.nav.syfo.vault.RenewVaultService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
}

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfoservicedatasyfosmregister")

@KtorExperimentalAPI
fun main() {
    val environment = Environment()
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(environment, applicationState)
    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    applicationServer.start()
    applicationState.ready = true
    oppdaterStatusSyfosmregister(applicationState, environment)
}
//
// fun hentArbeidsgiverInformasjonPaaSykmelding(
//    applicationState: ApplicationState,
//    environment: Environment
// ) {
//
//    val vaultServiceuser = VaultServiceUser(
//        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
//        serviceuserUsername = getFileAsString("/secrets/serviceuser/username")
//    )
//
//    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
//    val producerProperties =
//        kafkaBaseConfig.toProducerConfig(
//            environment.applicationName,
//            valueSerializer = JacksonKafkaSerializer::class
//        )
//
//    val kafkaproducerArbeidsgiverSykmeldingString = KafkaProducer<String, String>(producerProperties)
//
//    val arbeidsgiverSykmeldingKafkaProducer =
//        ArbeidsgiverSykmeldingKafkaProducer(
//            environment.sm2013SyfoSericeSykmeldingArbeidsgiverTopic,
//            kafkaproducerArbeidsgiverSykmeldingString
//        )
//
//    val syfoserviceVaultSecrets = VaultCredentials(
//        databasePassword = getFileAsString("/secrets/syfoservice/credentials/password"),
//        databaseUsername = getFileAsString("/secrets/syfoservice/credentials/username")
//    )
//
//    val vaultConfig = VaultConfig(
//        jdbcUrl = getFileAsString("/secrets/syfoservice/config/jdbc_url")
//    )
//
//    val databaseOracle = DatabaseOracle(vaultConfig, syfoserviceVaultSecrets)
//
//    HentArbeidsGiverOgSporsmalFraSyfoServiceService(
//        arbeidsgiverSykmeldingKafkaProducer,
//        databaseOracle,
//        1_000
//    ).run()
// }

fun oppdaterStatusSyfosmregister(applicationState: ApplicationState, environment: Environment) {
    val vaultServiceuser = VaultServiceUser(
        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
        serviceuserUsername = getFileAsString("/secrets/serviceuser/username")
    )
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)

    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-sykmelding-clean-consumer-21",
        valueDeserializer = StringDeserializer::class
    )
    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10")
    val kafkaConsumerCleanSykmelding = KafkaConsumer<String, String>(consumerProperties)
    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    val updateService = UpdateStatusService(databasePostgres)
    val skrivTilSyfosmRegisterSyfoService = SkrivTilSyfosmRegisterSyfoService(
        kafkaConsumerCleanSykmelding,
        databasePostgres,
        environment.sykmeldingCleanTopic,
        applicationState,
        updateService
    )
    skrivTilSyfosmRegisterSyfoService.run()
}

fun leggInnBehandlingsstatusForSykmeldinger(applicationState: ApplicationState, environment: Environment) {
    val vaultServiceuser = VaultServiceUser(
        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
        serviceuserUsername = getFileAsString("/secrets/serviceuser/username")
    )
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)

    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-sykmelding-clean-consumer-19",
        valueDeserializer = StringDeserializer::class
    )
    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100")
    val kafkaConsumerCleanSykmelding = KafkaConsumer<String, String>(consumerProperties)
    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    val skrivBehandlingsutfallTilSyfosmRegisterService = SkrivBehandlingsutfallTilSyfosmRegisterService(
        kafkaConsumerCleanSykmelding,
        databasePostgres,
        environment.sykmeldingCleanTopic,
        applicationState
    )
    skrivBehandlingsutfallTilSyfosmRegisterService.leggInnBehandlingsutfall()
}

fun hentSykemldingerFraEia(environment: Environment) {
    val vaultSecrets = VaultCredentials(
        databasePassword = getFileAsString("/secrets/eia/credentials/password"),
        databaseUsername = getFileAsString("/secrets/eia/credentials/username")
    )
    val vaultServiceuser = VaultServiceUser(
        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
        serviceuserUsername = getFileAsString("/secrets/serviceuser/username")
    )

    val vaultConfig = VaultConfig(
        jdbcUrl = getFileAsString("/secrets/eia/config/jdbc_url")
    )

    val databaseOracle = DatabaseOracle(vaultConfig, vaultSecrets)
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
    val producerProperties =
        kafkaBaseConfig.toProducerConfig(
            environment.applicationName,
            valueSerializer = JacksonKafkaSerializer::class
        )

    val kafkaproducerEiaSykmelding = KafkaProducer<String, Eia>(producerProperties)
    HentSykmeldingerFraEiaService(
        EiaSykmeldingKafkaProducer(
            environment.sm2013EiaSykmedlingTopic,
            kafkaproducerEiaSykmelding
        ),
        databaseOracle, 10_000, environment.lastIndexEia
    ).run()
}

fun hentSykemeldingerFraSyfoserviceOgPubliserTilTopic(environment: Environment, applicationState: ApplicationState) {
    val vaultConfig = VaultConfig(
        jdbcUrl = getFileAsString("/secrets/syfoservice/config/jdbc_url")
    )
    val vaultServiceuser = VaultServiceUser(
        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
        serviceuserUsername = getFileAsString("/secrets/serviceuser/username")
    )

    val syfoserviceVaultSecrets = VaultCredentials(
        databasePassword = getFileAsString("/secrets/syfoservice/credentials/password"),
        databaseUsername = getFileAsString("/secrets/syfoservice/credentials/username")
    )
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
    val producerProperties =
        kafkaBaseConfig.toProducerConfig(environment.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    val kafkaProducerClean = KafkaProducer<String, Map<String, Any?>>(producerProperties)

    val databaseOracle = DatabaseOracle(vaultConfig, syfoserviceVaultSecrets)
    HentSykmeldingerFraSyfoServiceService(
        SykmeldingKafkaProducer(environment.sykmeldingCleanTopic, kafkaProducerClean),
        databaseOracle, 10_000, environment.lastIndexSyfoservice
    ).run()
}

fun oppdaterFraEia(applicationState: ApplicationState, environment: Environment) {
    val vaultServiceuser = VaultServiceUser(
        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
        serviceuserUsername = getFileAsString("/secrets/serviceuser/username")
    )
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-eia-consumer",
        valueDeserializer = StringDeserializer::class
    )
    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500")
    val kafkaconsumerrEiaSykmelding = KafkaConsumer<String, String>(consumerProperties)
    SkrivTilSyfosmRegisterServiceEia(
        kafkaconsumerrEiaSykmelding,
        databasePostgres,
        environment.sm2013EiaSykmedlingTopic,
        applicationState
    ).run()
}

// fun readFromJsonMapTopicAndInsertMissingSykmeldinger(applicationState: ApplicationState, environment: Environment) {
//    val vaultServiceuser = VaultServiceUser(
//        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
//        serviceuserUsername = getFileAsString("/secrets/serviceuser/username")
//    )
//    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
//
//    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
//        "${environment.applicationName}-sykmelding-clean-consumer-13",
//        valueDeserializer = StringDeserializer::class
//    )
//    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100")
//    val kafkaConsumerCleanSykmelding = KafkaConsumer<String, String>(consumerProperties)
//    kafkaConsumerCleanSykmelding.subscribe(
//        listOf(environment.sykmeldingCleanTopic)
//    )
//    val vaultCredentialService = VaultCredentialService()
//    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
//    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
//    val skrivTilSyfosmRegisterSysoService = SkrivTilSyfosmRegisterSyfoService(
//        kafkaConsumerCleanSykmelding,
//        databasePostgres,
//        environment.sykmeldingCleanTopic,
//        applicationState
//    )
// }

fun runMapStringToJsonMap(
    applicationState: ApplicationState,
    environment: Environment
) {

    val vaultServiceuser = VaultServiceUser(
        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
        serviceuserUsername = getFileAsString("/secrets/serviceuser/username")
    )
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)

    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-sykmelding-string-consumer-1",
        valueDeserializer = StringDeserializer::class
    )
    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1000")
    val kafkaConsumerStringSykmelding = KafkaConsumer<String, String>(consumerProperties)

    val producerProperties =
        kafkaBaseConfig.toProducerConfig(environment.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    val kafkaProducerClean = KafkaProducer<String, Map<String, String?>>(producerProperties)

    MapSykmeldingStringToSykemldignJsonMap(
        kafkaConsumerStringSykmelding,
        kafkaProducerClean,
        environment.sykmeldingCleanTopic,
        environment.sm2013SyfoserviceSykmeldingTopic,
        applicationState
    ).run()
}
