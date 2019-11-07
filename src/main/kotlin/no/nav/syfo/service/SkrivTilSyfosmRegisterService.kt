package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Duration
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.objectMapper
import org.apache.kafka.clients.consumer.KafkaConsumer

class SkrivTilSyfosmRegisterService(
    private val kafkaconsumerReceivedSykmelding: KafkaConsumer<String, String>
) {

    fun run() {
        kafkaconsumerReceivedSykmelding.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
            val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(consumerRecord.value())
            // TODO check if already stored in database
            // Then insert into DB
        }
    }
}
