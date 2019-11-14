package no.nav.syfo.kafka

import no.nav.syfo.model.Eia
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class EiaSykmeldingKafkaProducer(
    val sm2013EiaSykmedlingTopic: String,
    val kafkaproducerStringSykmelding: KafkaProducer<String, Eia>
) {

    fun publishToKafka(eia: Eia) {
        kafkaproducerStringSykmelding.send(ProducerRecord(sm2013EiaSykmedlingTopic, eia))
    }
}
