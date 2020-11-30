package no.nav.syfo.papirsykmelding.tilsyfoservice.kafka

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.log
import no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.model.KafkaMessageMetadata
import no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.model.SykmeldingSyfoserviceKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingSyfoserviceKafkaProducer(private val kafkaProducer: KafkaProducer<String, SykmeldingSyfoserviceKafkaMessage>, private val topic: String) {
    fun publishSykmeldingToKafka(sykmeldingId: String, helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet) {
        try {
            kafkaProducer.send(ProducerRecord(topic, sykmeldingId, SykmeldingSyfoserviceKafkaMessage(
                metadata = KafkaMessageMetadata(sykmeldingId),
                helseopplysninger = helseOpplysningerArbeidsuforhet
            )
            )).get()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved skriving til syfoservice-topic: {}", e.cause)
            throw e
        }
    }
}
