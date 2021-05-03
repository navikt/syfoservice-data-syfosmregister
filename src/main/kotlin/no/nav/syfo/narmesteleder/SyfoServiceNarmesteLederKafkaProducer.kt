package no.nav.syfo.narmesteleder

import no.nav.syfo.log
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

class SyfoServiceNarmesteLederKafkaProducer(
    private val narmesteLederMigreringTopic: String,
    private val kafkaProducerSyfoServiceNarmesteLeder: KafkaProducer<String, SyfoServiceNarmesteLeder>
) {

    fun publishToKafka(syfoServiceNarmesteLeder: SyfoServiceNarmesteLeder) {
        kafkaProducerSyfoServiceNarmesteLeder.send(
            ProducerRecord(
                narmesteLederMigreringTopic,
                syfoServiceNarmesteLeder.id.toString(),
                syfoServiceNarmesteLeder
            )
        ) { metadata: RecordMetadata?, exception: Exception? ->
            if (exception != null) {
                log.error("Noe gikk galt ved skriving av nl-id ${syfoServiceNarmesteLeder.id} til migreringstopic: ${exception.message}")
            }
        }
    }
}
