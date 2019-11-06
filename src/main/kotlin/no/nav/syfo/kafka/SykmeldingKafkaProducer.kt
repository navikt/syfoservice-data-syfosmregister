package no.nav.syfo.kafka

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingKafkaProducer(
    val sm2013SyfoserviceSykmeldingTopic: String,
    val kafkaproducerStringSykmelding: KafkaProducer<String, String>
) {

    fun publishToKafka(sykmelding: String) {
        kafkaproducerStringSykmelding.send(ProducerRecord(sm2013SyfoserviceSykmeldingTopic, sykmelding))
    }
}
