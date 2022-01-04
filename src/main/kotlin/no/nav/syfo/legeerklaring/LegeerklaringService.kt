package no.nav.syfo.legeerklaring

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.getVaultServiceUser
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.legeerklaring.LegeerklaringMapper.Companion.mapToXml
import no.nav.syfo.legeerklaring.util.extractLegeerklaering
import no.nav.syfo.legeerklaring.util.extractOrganisationHerNumberFromSender
import no.nav.syfo.legeerklaring.util.extractOrganisationNumberFromSender
import no.nav.syfo.legeerklaring.util.extractOrganisationRashNumberFromSender
import no.nav.syfo.legeerklaring.util.extractPersonIdent
import no.nav.syfo.legeerklaring.util.extractSenderOrganisationName
import no.nav.syfo.legeerklaring.util.sha256hashstring
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.utils.fellesformatMarshaller
import no.nav.syfo.utils.get
import no.nav.syfo.utils.toString
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

class LegeerklaringService(private val environment: Environment, private val applicationState: ApplicationState) {

    private val kafkaConsumer: KafkaConsumer<String?, String>
    private val simpleLegeerklaringConsumer: KafkaConsumer<String?, String>
    private val legeerklaringProducer: KafkaProducer<String, String>
    private val hashSet = HashSet<String>()
    init {

        val vaultServiceuser = getVaultServiceUser()
        val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
        val consumerProperties = kafkaBaseConfig.toConsumerConfig("${environment.applicationName}-consumer-8", StringDeserializer::class)
        val hashConsumerProperties = kafkaBaseConfig.toConsumerConfig("${environment.applicationName}-hash-5", StringDeserializer::class)

        kafkaConsumer = KafkaConsumer(consumerProperties)
        simpleLegeerklaringConsumer = KafkaConsumer(hashConsumerProperties)
        legeerklaringProducer = KafkaProducer(kafkaBaseConfig.toProducerConfig("${environment.applicationName}-producer", StringSerializer::class))
    }

    suspend fun buildUpHash() {
        var counter = 0
        val job = GlobalScope.launch {
            while (applicationState.ready) {
                log.info("Lest {} legeærklæringer og lagret i hashset", counter)
                delay(10_000)
            }
        }

        simpleLegeerklaringConsumer.subscribe(listOf("privat-syfo-pale2-avvist-v1", "privat-syfo-pale2-ok-v1"))

        var stopTime = LocalDateTime.now().plusSeconds(180)

        while (LocalDateTime.now().isBefore(stopTime)) {
            val records = simpleLegeerklaringConsumer.poll(Duration.ZERO)
            if (!records.isEmpty) {
                records.forEach {
                    val simpleMessage = objectMapper.readValue<SimpleLegeerklaeringKafkaMessage>(it.value())
                    val adjustedXml = LegeerklaringMapper.getAdjustedXml(simpleMessage)
                    val sha256 = sha256hashstring(extractLegeerklaering(adjustedXml))
                    hashSet.add(sha256)
                    counter++
                }
                stopTime = LocalDateTime.now().plusSeconds(10)
            }
        }
        simpleLegeerklaringConsumer.close()
        job.cancelAndJoin()
        log.info("Lest {} legeærklæringer og lagret i hashset", counter)
    }

    suspend fun start() {
        try {
            buildUpHash()
            var counter = 0
            var missingCounter = 0
            var duplicateCounter = 0
            GlobalScope.launch {
                while (applicationState.ready) {
                    log.info("Lest og mapped {} legeærklæringer, duplikater {}, missing {}", counter, duplicateCounter, missingCounter)
                    delay(10_000)
                }
            }
            kafkaConsumer.subscribe(listOf(environment.pale2dump))
            while (applicationState.ready) {
                val records = kafkaConsumer.poll(Duration.ofMillis(1000))
                records.forEach {
                    val fellesformat = mapToXml(it.value())
                    val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
                    val msgHead = fellesformat.get<XMLMsgHead>()
                    val ediLoggId = receiverBlock.ediLoggId
                    val msgId = msgHead.msgInfo.msgId
                    val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
                    val legeerklaringxml = extractLegeerklaering(fellesformat)
                    val sha256String = sha256hashstring(legeerklaringxml)
                    val fnrPasient = extractPersonIdent(legeerklaringxml)!!
                    val legekontorOrgName = extractSenderOrganisationName(fellesformat)
                    val fnrLege = receiverBlock.avsenderFnrFraDigSignatur
                    val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
                    val legekontorReshId = extractOrganisationRashNumberFromSender(fellesformat)?.id
                    val stringToTopic = fellesformatMarshaller.toString(fellesformat)
                    counter ++
                    if (hashSet.contains(sha256String)) {
                        duplicateCounter++
                    } else {
                        hashSet.add(sha256String)
                        log.info(
                            "found missing publishing to rerun topic, received at {}",
                            receiverBlock.mottattDatotid?.toGregorianCalendar()?.toInstant()?.atZone(
                                ZoneOffset.UTC
                            )
                        )
                        missingCounter++
                        legeerklaringProducer.send(ProducerRecord(environment.pale2RerunTopic, stringToTopic)).get()
                    }
                }
            }
        } catch (ex: Exception) {
            log.error("Error handling dump topic", ex)
            throw ex
        }
    }
}
