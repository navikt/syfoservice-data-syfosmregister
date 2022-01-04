package no.nav.syfo.papirsykmelding.tilsyfoservice.kafka

import no.nav.syfo.log
import no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.model.SykmeldingSyfoserviceKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingSyfoserviceKafkaProducer(private val kafkaProducer: KafkaProducer<String, SykmeldingSyfoserviceKafkaMessage>, private val topic: String) {
    fun publishSykmeldingToKafka(
        sykmeldingId: String,
        syfoserviceKafkaMessage: SykmeldingSyfoserviceKafkaMessage
    ) {
        try {
            kafkaProducer.send(
                ProducerRecord(
                    topic,
                    sykmeldingId,
                    syfoserviceKafkaMessage
                )
            ).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved skriving til syfoservice-topic: {}", e.cause)
            throw e
        }
    }
}
