package no.nav.syfo.sykmelding

import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class EnkelSykmeldingKafkaProducer(private val kafkaProducer: KafkaProducer<String, SykmeldingKafkaMessage>, private val topic: String) {
    fun sendSykmelding(sykmeldingKafkaMessage: SykmeldingKafkaMessage) {
        kafkaProducer.send(ProducerRecord(topic, sykmeldingKafkaMessage.sykmelding.id, sykmeldingKafkaMessage)).get()
    }
}
