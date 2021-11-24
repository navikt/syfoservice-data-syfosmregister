package no.nav.syfo.identendring

import no.nav.syfo.identendring.model.SykmeldingKafkaMessage
import no.nav.syfo.log
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SendtSykmeldingKafkaProducer(private val kafkaProducer: KafkaProducer<String, SykmeldingKafkaMessage>, private val topic: String) {
    fun sendSykmelding(sykmeldingKafkaMessage: SykmeldingKafkaMessage) {
        try {
            kafkaProducer.send(ProducerRecord(topic, sykmeldingKafkaMessage.sykmelding.id, sykmeldingKafkaMessage)).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved skriving til sendt-topic")
            throw e
        }
    }
}
