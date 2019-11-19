package no.nav.syfo.kafka

import no.nav.syfo.model.StatusSyfoService
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SyfoServiceSykmeldingStatusKafkaProducer(
    val sm2013SyfoSericeSykmeldingStatusTopic: String,
    val kafkaproducerStatusSyfoService: KafkaProducer<String, StatusSyfoService>
) {

    fun publishToKafka(statusSyfoService: StatusSyfoService) {
        kafkaproducerStatusSyfoService.send(ProducerRecord(sm2013SyfoSericeSykmeldingStatusTopic, statusSyfoService))
    }
}
