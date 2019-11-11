package no.nav.syfo.kafka

import no.nav.syfo.model.ReceivedSykmelding
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class RecivedSykmeldingKafkaProducer(
    val sm2013SyfoserviceSykmeldingTopic: String,
    val kafkaproducerStringSykmelding: KafkaProducer<String, ReceivedSykmelding>
) {

    fun publishToKafka(receivedSykmelding: ReceivedSykmelding) {
        kafkaproducerStringSykmelding.send(ProducerRecord(sm2013SyfoserviceSykmeldingTopic, receivedSykmelding))
    }
}
