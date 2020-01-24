package no.nav.syfo.kafka

import no.nav.syfo.model.Behandlingsutfall
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class BehandlingsutfallKafkaProducer(
    val topic: String,
    val kafkaproducerStringBehandlingsutfall: KafkaProducer<String, Behandlingsutfall>
) {

    fun publishToKafka(behandlingsutfall: Behandlingsutfall) {
        kafkaproducerStringBehandlingsutfall.send(ProducerRecord(topic, behandlingsutfall.id, behandlingsutfall))
    }
}
