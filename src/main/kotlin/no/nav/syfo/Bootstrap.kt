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
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.utils.JacksonKafkaSerializer
import no.nav.syfo.utils.getFileAsString
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

    val vaultSecrets = VaultCredentials(
        databasePassword = getFileAsString("/secrets/credentials/password"),
        databaseUsername = getFileAsString("/secrets/credentials/username")
    )

    val vaultServiceuser = VaultServiceUser(
        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
        serviceuserUsername = getFileAsString("/secrets/serviceuser/username")
    )

    val vaultConfig = VaultConfig(
        jdbcUrl = getFileAsString("/secrets/config/jdbc_url")
    )

    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-consumer-3",
        valueDeserializer = StringDeserializer::class
    )
    val producerProperties =
        kafkaBaseConfig.toProducerConfig(environment.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    val kafkaproducerReceivedSykmelding = KafkaProducer<String, ReceivedSykmelding>(producerProperties)
    val kafkaproducerStringSykmelding = KafkaProducer<String, String>(producerProperties)
    val kafkaconsumerStringSykmelding = KafkaConsumer<String, String>(consumerProperties)
    val kafkaconsumerReceivedSykmelding = KafkaConsumer<String, ReceivedSykmelding>(consumerProperties)

    val database = DatabaseOracle(vaultConfig, vaultSecrets)

    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(environment, applicationState)
    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    applicationServer.start()
    applicationState.ready = true

    // Hent ut sykmeldigner fra syfoservice
    // HentSykmeldingerFraSyfoServiceService(
    // SykmeldingKafkaProducer(environment.sm2013SyfoserviceSykmeldingTopic, kafkaproducerStringSykmelding),
    // database, 10_000).run()

    // kafkaconsumerStringSykmelding.subscribe(
    //    listOf(environment.sm2013SyfoserviceSykmeldingTopic)
    // )
    // var counter = 0

    while (applicationState.ready) {
        // Map sykmeldinger fra intern format
        // MapSykmeldingerFraTopicService(
        // kafkaconsumerStringSykmelding,
        // kafkaproducerReceivedSykmelding,
        // environment.sm2013SyfoserviceSykmeldingCleanTopic,
        // counter).run()
    }
}
