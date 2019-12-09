package no.nav.syfo.kafka

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingKafkaProducer(
    val sykmeldingCleanTopic: String,
    val kafkaproducerStringSykmelding: KafkaProducer<String, Map<String, Any?>>
) {

    fun publishToKafka(sykmelding: Map<String, Any?>) {
        kafkaproducerStringSykmelding.send(ProducerRecord(sykmeldingCleanTopic, sykmelding))
    }
}
