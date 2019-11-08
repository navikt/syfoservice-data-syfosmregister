package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import java.io.StringReader
import java.time.Duration
import java.time.LocalDateTime
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.toSykmelding
import no.nav.syfo.objectMapper
import no.nav.syfo.utils.fellesformatUnmarshaller
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.UUID

class MapSykmeldingerFraTopicService(
    private val kafkaconsumerStringSykmelding: KafkaConsumer<String, String>,
    private val kafkaproducerReceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    private val sm2013SyfoserviceSykmeldingCleanTopic: String,
    private var counter: Int
) {

    fun run() {
        kafkaconsumerStringSykmelding.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
            val jsonMap: Map<String, String?> =
                objectMapper.readValue(objectMapper.readValue<String>(objectMapper.readValue<String>(consumerRecord.value())))
            val receivedSykmelding = toReceivedSykmelding(jsonMap)
            kafkaproducerReceivedSykmelding.send(
                ProducerRecord(
                    sm2013SyfoserviceSykmeldingCleanTopic,
                    receivedSykmelding.sykmelding.id,
                    receivedSykmelding
                )
            )
            counter++
            if (counter % 1000 == 0) {
                log.info("Melding sendt til kafka topic nr {}", counter)
            }
        }
    }

    fun toReceivedSykmelding(jsonMap: Map<String, Any?>): ReceivedSykmelding {

        val unmarshallerToHealthInformation = unmarshallerToHealthInformation(jsonMap["DOKUMENT"].toString())

        return ReceivedSykmelding(
            sykmelding = unmarshallerToHealthInformation.toSykmelding(
                sykmeldingId = UUID.randomUUID().toString(),
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
}
