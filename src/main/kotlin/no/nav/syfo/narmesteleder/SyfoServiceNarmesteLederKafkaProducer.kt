package no.nav.syfo.narmesteleder

import no.nav.syfo.log
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SyfoServiceNarmesteLederKafkaProducer(
    private val narmesteLederMigreringTopic: String,
    private val kafkaProducerSyfoServiceNarmesteLeder: KafkaProducer<String, SyfoServiceNarmesteLeder>
) {

    fun publishToKafka(syfoServiceNarmesteLeder: SyfoServiceNarmesteLeder) {
        try {
            kafkaProducerSyfoServiceNarmesteLeder.send(ProducerRecord(narmesteLederMigreringTopic, syfoServiceNarmesteLeder.id.toString(), syfoServiceNarmesteLeder)).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved skriving av nl-id ${syfoServiceNarmesteLeder.id} til migreringstopic: ${e.message}")
            throw e
        }
    }
}
