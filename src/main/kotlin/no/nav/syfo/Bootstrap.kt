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
import no.nav.syfo.service.HentStatusFraSyfoServiceService
import no.nav.syfo.utils.getFileAsString
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

    val syfoserviceVaultSecrets = VaultCredentials(
        databasePassword = getFileAsString("/secrets/syfoservice/credentials/password"),
        databaseUsername = getFileAsString("/secrets/syfoservice/credentials/username")
    )

    val vaultConfig = VaultConfig(
        jdbcUrl = getFileAsString("/secrets/syfoservice/config/jdbc_url")
    )

//    val vaultServiceuser = VaultServiceUser(
//        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
//        serviceuserUsername = getFileAsString("/secrets/serviceuser/username")
//    )

//    val vaultConfig = VaultConfig(
//        jdbcUrl = getFileAsString("/secrets/eia/config/jdbc_url")
//    )

//    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
//    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
//        "${environment.applicationName}-consumer-3",
//        valueDeserializer = StringDeserializer::class
//    )

//    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100")

//    val producerProperties =
//        kafkaBaseConfig.toProducerConfig(environment.applicationName, valueSerializer = JacksonKafkaSerializer::class)
//    val kafkaproducerReceivedSykmelding = KafkaProducer<String, ReceivedSykmelding>(producerProperties)
//    val kafkaproducerStringSykmelding = KafkaProducer<String, String>(producerProperties)
//    val kafkaconsumerStringSykmelding = KafkaConsumer<String, String>(consumerProperties)
//    val kafkaconsumerReceivedSykmelding = KafkaConsumer<String, String>(consumerProperties)
//    val kafkaproducerEiaReceivedSykmelding = KafkaProducer<String, ReceivedSykmelding>(producerProperties)
//    val kafkaproducerEiaSykmelding = KafkaProducer<String, Eia>(producerProperties)
//    val kafkaconsumerrEiaSykmelding = KafkaConsumer<String, String>(consumerProperties)

    val databaseOracle = DatabaseOracle(vaultConfig, syfoserviceVaultSecrets)
//    val vaultCredentialService = VaultCredentialService()
//    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)

    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(environment, applicationState)
    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    applicationServer.start()
    applicationState.ready = true

//    Hent ut sykmeldigStatus fra syfoservice
    HentStatusFraSyfoServiceService(
        databaseOracle, 10_000
    ).run()

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

//    SkrivTilSyfosmRegisterServiceEia(
//        kafkaconsumerrEiaSykmelding,
//        databasePostgres,
//        environment.sm2013EiaSykmedlingTopic,
//        applicationState
//    ).run()

//    val antallSykmeldingerFor = databasePostgres.hentAntallSykmeldinger()
//    log.info("Antall sykmeldinger i datbasen for oppdatering, {}", antallSykmeldingerFor.first().antall)

//    SkrivTilSyfosmRegisterService(
//        kafkaconsumerReceivedSykmelding,
//        databasePostgres,
//        environment.sm2013SyfoserviceSykmeldingCleanTopic,
//        applicationState
//    ).run()
}
