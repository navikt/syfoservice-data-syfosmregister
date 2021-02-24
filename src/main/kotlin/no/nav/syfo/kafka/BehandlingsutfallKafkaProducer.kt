package no.nav.syfo.kafka

import no.nav.syfo.model.ValidationResult
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class BehandlingsutfallKafkaProducer(
    val topic: String,
    val kafkaproducerStringBehandlingsutfall: KafkaProducer<String, ValidationResult>
) {

    fun publishToKafka(behandlingsutfall: ValidationResult, sykmeldingId: String) {
        kafkaproducerStringBehandlingsutfall.send(ProducerRecord(topic, sykmeldingId, behandlingsutfall)).get()
    }
}
