package no.nav.syfo.kafka

import no.nav.syfo.model.ReceivedSykmelding
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class ReceivedSykmeldingKafkaProducer(
    val sm2013ReceivedSykmelding: String,
    val kafkaproducerStringSykmelding: KafkaProducer<String, ReceivedSykmelding>
) {

    fun publishToKafka(receivedSykmelding: ReceivedSykmelding) {
        kafkaproducerStringSykmelding.send(ProducerRecord(sm2013ReceivedSykmelding, receivedSykmelding.sykmelding.id, receivedSykmelding))
    }
}
