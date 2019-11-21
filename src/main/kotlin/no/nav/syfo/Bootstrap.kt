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
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.service.MapSykmeldingStringToSykemldignJsonMap
import no.nav.syfo.service.SkrivTilSyfosmRegisterServiceEia
import no.nav.syfo.service.SkrivTilSyfosmRegisterSysoServiceStatus
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

//    val vaultSecrets = VaultCredentials(
//        databasePassword = getFileAsString("/secrets/eia/credentials/password"),
//        databaseUsername = getFileAsString("/secrets/eia/credentials/username")
//    )

//    val syfoserviceVaultSecrets = VaultCredentials(
//        databasePassword = getFileAsString("/secrets/syfoservice/credentials/password"),
//        databaseUsername = getFileAsString("/secrets/syfoservice/credentials/username")
//    )
//
//    val vaultConfig = VaultConfig(
//        jdbcUrl = getFileAsString("/secrets/syfoservice/config/jdbc_url")
//    )
//
//    val vaultServiceuser = VaultServiceUser(
//        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
//        serviceuserUsername = getFileAsString("/secrets/serviceuser/username")
//    )

//    val vaultConfig = VaultConfig(
//        jdbcUrl = getFileAsString("/secrets/eia/config/jdbc_url")
//    )

//    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
//    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
//        "${environment.applicationName}-sykmelding-string-consumer",
//        valueDeserializer = StringDeserializer::class
//    )
//
//    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500")
//
//    val producerProperties =
//        kafkaBaseConfig.toProducerConfig(environment.applicationName, valueSerializer = JacksonKafkaSerializer::class)
//    val kafkaproducerReceivedSykmelding = KafkaProducer<String, ReceivedSykmelding>(producerProperties)
//    val kafkaproducerStringSykmelding = KafkaProducer<String, String>(producerProperties)

//    val kafkaconsumerReceivedSykmelding = KafkaConsumer<String, String>(consumerProperties)
//    val kafkaproducerEiaReceivedSykmelding = KafkaProducer<String, ReceivedSykmelding>(producerProperties)
//    val kafkaproducerEiaSykmelding = KafkaProducer<String, Eia>(producerProperties)
//    val kafkaconsumerrEiaSykmelding = KafkaConsumer<String, String>(consumerProperties)

//    val kafkaconsumerStatusSyfoServiceSykmelding = KafkaConsumer<String, String>(consumerProperties)

//    val databaseOracle = DatabaseOracle(vaultConfig, syfoserviceVaultSecrets)
//    val vaultCredentialService = VaultCredentialService()
//    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)

    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(environment, applicationState)
    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    applicationServer.start()
    applicationState.ready = true

//    Hent ut sykmeldigStatus fra syfoservice
//    HentStatusFraSyfoServiceService(
//        SyfoServiceSykmeldingStatusKafkaProducer(
//            environment.sm2013SyfoSericeSykmeldingStatusTopic,
//            kafkaproducerStatusSyfoServiceSykmelding
//        ),
//        databaseOracle, 10_000
//    ).run()

//    Hent ut sykmeldigner fra eia
//    HentSykmeldingerFraEiaService(
//        EiaSykmeldingKafkaProducer(
//            environment.sm2013EiaSykmedlingTopic,
//            kafkaproducerEiaSykmelding
//        ),
//        databaseOracle, 10_000, environment.lastIndex
//    ).run()

    // Hent ut sykmeldigner fra syfoservice
    // HentSykmeldingerFraSyfoServiceService(
    // SykmeldingKafkaProducer(environment.sm2013SyfoserviceSykmeldingTopic, kafkaproducerStringSykmelding),
    // databaseOracle, 10_000).run()

    // mapper som sykmelding som er av typen string og sender dei til ny topic
    /*
    MapSykmeldingerFraTopicService(
        kafkaconsumerStringSykmelding,
        kafkaproducerReceivedSykmelding,
        environment.sm2013SyfoserviceSykmeldingCleanTopic,
        environment.sm2013SyfoserviceSykmeldingTopic,
        applicationState
    ).run()
     */

//    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()

//    val antallSykmeldingerFor = databasePostgres.hentAntallSykmeldinger()
//    log.info("Antall sykmeldinger i datbasen for oppdatering, {}", antallSykmeldingerFor.first().antall)

//    SkrivTilSyfosmRegisterService(
//        kafkaconsumerReceivedSykmelding,
//        databasePostgres,
//        environment.sm2013SyfoserviceSykmeldingCleanTopic,
//        applicationState
//    ).run()

//    SkrivTilSyfosmRegisterSysoServiceStatus(
//        kafkaconsumerStatusSyfoServiceSykmelding,
//        databasePostgres,
//        environment.sm2013SyfoSericeSykmeldingStatusTopic,
//        applicationState
//    ).run()
    // readFromJsonMapTopic(applicationState, environment)
    oppdaterFraEia(applicationState, environment)
//    readFromJsonMapTopic(applicationState, environment)
}

fun oppdaterFraEia(applicationState: ApplicationState, environment: Environment) {
    val vaultServiceuser = VaultServiceUser(
        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
        serviceuserUsername = getFileAsString("/secrets/serviceuser/username")
    )
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-eia-consumer-10",
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

fun readFromJsonMapTopic(applicationState: ApplicationState, environment: Environment) {
    val vaultServiceuser = VaultServiceUser(
        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
        serviceuserUsername = getFileAsString("/secrets/serviceuser/username")
    )
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)

    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-sykmelding-clean-consumer-2",
        valueDeserializer = StringDeserializer::class
    )
    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500")
    val kafkaConsumerCleanSykmelding = KafkaConsumer<String, String>(consumerProperties)
    kafkaConsumerCleanSykmelding.subscribe(
        listOf(environment.sykmeldingCleanTopic)
    )
    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    val skrivTilSyfosmRegisterSysoService = SkrivTilSyfosmRegisterSysoServiceStatus(
        kafkaConsumerCleanSykmelding,
        databasePostgres,
        environment.sykmeldingCleanTopic,
        applicationState
    )
    skrivTilSyfosmRegisterSysoService.run()
}

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
