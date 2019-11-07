package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.toSykmelding
import no.nav.syfo.utils.MapperFunctions.Companion.toReceivedSykmelding
import no.nav.syfo.utils.fellesformatUnmarshaller
import no.nav.syfo.utils.getFileAsString
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.StringReader
import java.time.Duration
import java.time.LocalDateTime

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

    /*
    val vaultConfig = VaultConfig(
        jdbcUrl = getFileAsString("/secrets/config/jdbc_url")
    )*/

    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-consumer",
        valueDeserializer = StringDeserializer::class
    )
    // val producerProperties = kafkaBaseConfig.toProducerConfig(environment.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    // val kafkaproducerStringSykmelding = KafkaProducer<String, String>(producerProperties)
    val kafkaconsumerStringSykmelding = KafkaConsumer<String, String>(consumerProperties)

    // val database = Database(vaultConfig, vaultSecrets)

    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(environment, applicationState)
    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    applicationServer.start()
    applicationState.ready = true

    kafkaconsumerStringSykmelding.subscribe(
        listOf(environment.sm2013SyfoserviceSykmeldingTopic)
    )


    while (applicationState.ready) {
        kafkaconsumerStringSykmelding.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
            log.info("got kafkatopic")
            log.info(consumerRecord.value())
            val jsonMap: Map<String, String?> = objectMapper.readValue(objectMapper.readValue<String>(consumerRecord.value()))
            val receivedSykmelding = toReceivedSykmelding(jsonMap)
            log.info("Mapped to ReceivedSykmelding")
        }
    }


    // val sykmeldingKafkaProducer = SykmeldingKafkaProducer(environment.sm2013SyfoserviceSykmeldingTopic, kafkaproducerStringSykmelding)

    // SykmeldingService(sykmeldingKafkaProducer, database, 10_000).run()
}


fun toReceivedSykmelding(jsonMap: Map<String, Any?>): ReceivedSykmelding {

    val unmarshallerToHealthInformation = unmarshallerToHealthInformation(jsonMap["DOKUMENT"].toString())

    return ReceivedSykmelding(
        sykmelding = unmarshallerToHealthInformation.toSykmelding(
            sykmeldingId = jsonMap["MELDING_ID"].toString(),
            pasientAktoerId = jsonMap["AKTOR_ID"].toString(),
            legeAktoerId = "",
            msgId = "",
            signaturDato = LocalDateTime.parse((jsonMap["CREATED"].toString().substring(0, 19)))
        ),
        personNrPasient = unmarshallerToHealthInformation.pasient.fodselsnummer.id,
        tlfPasient = unmarshallerToHealthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
        personNrLege = "",
        navLogId = jsonMap["MOTTAK_ID"].toString(),
        msgId = "",
        legekontorOrgNr = "",
        legekontorOrgName = "",
        legekontorHerId = "",
        legekontorReshId = "",
        mottattDato = LocalDateTime.now(),
        rulesetVersion = unmarshallerToHealthInformation.regelSettVersjon,
        fellesformat = "",
        tssid = ""
    )
}

fun unmarshallerToHealthInformation(healthInformation: String): HelseOpplysningerArbeidsuforhet =
    fellesformatUnmarshaller.unmarshal(StringReader(healthInformation)) as HelseOpplysningerArbeidsuforhet

