package no.nav.syfo.kafka

import no.nav.syfo.log
import no.nav.syfo.model.Sykmeldingsdokument
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingEndringsloggKafkaProducer(
    private val endringsloggTopic: String,
    private val kafkaproducerEndringsloggSykmelding: KafkaProducer<String, Sykmeldingsdokument>
) {

    fun publishToKafka(sykmelding: Sykmeldingsdokument) {
        try {
            kafkaproducerEndringsloggSykmelding.send(ProducerRecord(endringsloggTopic, sykmelding)).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved skriving til endringslogg-topic: {}", e.cause)
            throw e
        }
    }
}
