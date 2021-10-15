package no.nav.syfo.sykmelding.aivenmigrering

import no.nav.syfo.log
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingV2KafkaProducer(
    private val kafkaProducer: KafkaProducer<String, SykmeldingV2KafkaMessage?>
) {
    fun sendSykmelding(sykmeldingKafkaMessage: SykmeldingV2KafkaMessage?, sykmeldingId: String, topic: String) {
        try {
            kafkaProducer.send(ProducerRecord(topic, sykmeldingId, sykmeldingKafkaMessage)).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved skriving til topic $topic, sykmeldingsid $sykmeldingId")
            throw e
        }
    }
}
