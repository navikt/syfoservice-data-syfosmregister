package no.nav.syfo.kafka

import no.nav.syfo.model.ReceivedSykmelding
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class ReceivedSykmeldingKafkaProducer(
    val topic: String,
    val kafkaproducerStringSykmelding: KafkaProducer<String, ReceivedSykmelding>
) {

    fun publishToKafka(receivedSykmelding: ReceivedSykmelding) {
        kafkaproducerStringSykmelding.send(ProducerRecord(topic, receivedSykmelding.sykmelding.id, receivedSykmelding))
    }
}
