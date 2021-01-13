package no.nav.syfo.papirsykmelding.api

import no.nav.syfo.model.Sykmeldingsdokument
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingEndringsloggKafkaProducer(
    private val endringsloggTopic: String,
    private val kafkaproducerEndringsloggSykmelding: KafkaProducer<String, Sykmeldingsdokument>
) {

    fun publishToKafka(sykmelding: Sykmeldingsdokument) {
        kafkaproducerEndringsloggSykmelding.send(ProducerRecord(endringsloggTopic, sykmelding))
    }
}
