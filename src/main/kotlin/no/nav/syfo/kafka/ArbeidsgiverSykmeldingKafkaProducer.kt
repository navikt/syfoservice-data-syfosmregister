package no.nav.syfo.kafka

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class ArbeidsgiverSykmeldingKafkaProducer(
    val sm2013SyfoSericeSykmeldingArbeidsgiverTopic: String,
    val kafkaproducerArbeidsgiverStringSykmelding: KafkaProducer<String, String>
) {

    fun publishToKafka(arbeidgiverString: String) {
        kafkaproducerArbeidsgiverStringSykmelding.send(ProducerRecord(sm2013SyfoSericeSykmeldingArbeidsgiverTopic, arbeidgiverString))
    }
}
