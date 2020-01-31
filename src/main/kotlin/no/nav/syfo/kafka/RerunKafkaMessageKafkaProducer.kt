package no.nav.syfo.kafka

import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class RerunKafkaMessageKafkaProducer(
    private val topic: String,
    private val kafkaproducerRerunKafkaMessage: KafkaProducer<String, RerunKafkaMessage>
) {

    fun publishToKafka(rerunKafkaMessage: RerunKafkaMessage) {
        kafkaproducerRerunKafkaMessage.send(ProducerRecord(topic, rerunKafkaMessage))
    }
}

data class RerunKafkaMessage(val receivedSykmelding: ReceivedSykmelding, val validationResult: ValidationResult)
