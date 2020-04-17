package no.nav.syfo.sendtsykmelding

import no.nav.syfo.sendtsykmelding.kafka.model.SendtSykmeldingKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SendtSykmeldingKafkaProducer(private val kafkaProducer: KafkaProducer<String, SendtSykmeldingKafkaMessage>, private val topic: String) {
    fun sendSykmelding(sykmeldingKafkaMessage: SendtSykmeldingKafkaMessage) {
        kafkaProducer.send(ProducerRecord(topic, sykmeldingKafkaMessage.sykmelding.id, sykmeldingKafkaMessage))
    }
}
