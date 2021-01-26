package no.nav.syfo.sykmelding

import no.nav.syfo.log
import no.nav.syfo.sykmelding.kafka.model.MottattSykmeldingKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class MottattSykmeldingKafkaProducer(
    private val kafkaProducer: KafkaProducer<String, MottattSykmeldingKafkaMessage>,
    private val topic: String
) {
    fun sendSykmelding(sykmeldingKafkaMessage: MottattSykmeldingKafkaMessage) {
        try {
            kafkaProducer.send(ProducerRecord(topic, sykmeldingKafkaMessage.sykmelding.id, sykmeldingKafkaMessage)).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved skriving til mottatt-topic")
            throw e
        }
    }
}
