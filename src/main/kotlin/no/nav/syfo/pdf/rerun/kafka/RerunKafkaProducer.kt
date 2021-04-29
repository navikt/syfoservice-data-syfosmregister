package no.nav.syfo.pdf.rerun.kafka

import no.nav.syfo.Environment
import no.nav.syfo.objectMapper
import no.nav.syfo.pdf.rerun.service.RerunKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class RerunKafkaProducer(val kafkaProducer: KafkaProducer<String, String>, val environment: Environment) {
    fun publishToKafka(RerunKafkaMessage: RerunKafkaMessage) {
        kafkaProducer.send(ProducerRecord(environment.rerunTopic, RerunKafkaMessage.receivedSykmelding.sykmelding.id, objectMapper.writeValueAsString(RerunKafkaMessage))).get()
    }
}
