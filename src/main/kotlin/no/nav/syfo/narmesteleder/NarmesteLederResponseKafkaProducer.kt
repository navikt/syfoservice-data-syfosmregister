package no.nav.syfo.narmesteleder

import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.kafkamodel.KafkaMetadata
import no.nav.syfo.narmesteleder.kafkamodel.NlResponse
import no.nav.syfo.narmesteleder.kafkamodel.NlResponseKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class NarmesteLederResponseKafkaProducer(
    private val topic: String,
    private val kafkaProducerNlResponse: KafkaProducer<String, NlResponseKafkaMessage>
) {

    fun publishToKafka(nlResponse: NlResponse) {
        try {
            val kafkaMessage = NlResponseKafkaMessage(
                kafkaMetadata = KafkaMetadata(OffsetDateTime.now(ZoneOffset.UTC), "macgyver"),
                nlResponse = nlResponse
            )
            kafkaProducerNlResponse.send(ProducerRecord(topic, nlResponse.orgnummer, kafkaMessage)).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved skriving av nl-response til topic: ${e.message}")
            throw e
        }
    }
}
