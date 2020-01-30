package no.nav.syfo.kafka

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingIdKafkaProducer(
    val idUtenBehandlingsutfallFraBackupTopic: String,
    val kafkaproducerSykmeldingId: KafkaProducer<String, String>
) {

    fun publishToKafka(sykmeldingId: String) {
        kafkaproducerSykmeldingId.send(ProducerRecord(idUtenBehandlingsutfallFraBackupTopic, sykmeldingId))
    }
}
