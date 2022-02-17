package no.nav.syfo.narmesteleder

import no.nav.syfo.log
import no.nav.syfo.narmesteleder.kafkamodel.KafkaMetadata
import no.nav.syfo.narmesteleder.kafkamodel.NlResponse
import no.nav.syfo.narmesteleder.kafkamodel.NlResponseKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import java.time.OffsetDateTime
import java.time.ZoneOffset

class NarmesteLederResponseKafkaProducer(
    private val topic: String,
    private val kafkaProducerNlResponse: KafkaProducer<String, NlResponseKafkaMessage>
) {

    fun publishToKafka(nlResponseKafkaMessage: NlResponseKafkaMessage, orgnummer: String) {
        try {
            kafkaProducerNlResponse.send(
                ProducerRecord(
                    topic,
                    orgnummer,
                    nlResponseKafkaMessage
                )
            ).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved skriving av nlResponse: ${e.message}")
            throw e
        }
    }

    fun publishToKafka(nlResponse: NlResponse, nlId: String) {
        val kafkaMessage = NlResponseKafkaMessage(
            kafkaMetadata = KafkaMetadata(OffsetDateTime.now(ZoneOffset.UTC), "macgyver"),
            nlResponse = nlResponse
        )
        kafkaProducerNlResponse.send(
            ProducerRecord(
                topic,
                nlResponse.orgnummer,
                kafkaMessage
            )
        ) { _: RecordMetadata?, exception: Exception? ->
            if (exception != null) {
                log.error("Noe gikk galt ved skriving av nlResponse for id $nlId: ${exception.message}")
            }
        }
    }
}
