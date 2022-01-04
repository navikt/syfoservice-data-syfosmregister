package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.toReceivedSykmelding
import no.nav.syfo.objectMapper
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Duration

class MapSykmeldingerFraTopicService(
    private val kafkaconsumerStringSykmelding: KafkaConsumer<String, String>,
    private val kafkaproducerReceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    private val sm2013SyfoserviceSykmeldingCleanTopic: String,
    private val sm2013SyfoserviceSykmeldingStringTopic: String,
    private var applicationState: ApplicationState
) {

    fun run() {

        var counter = 0
        kafkaconsumerStringSykmelding.subscribe(
            listOf(sm2013SyfoserviceSykmeldingStringTopic)
        )
        while (applicationState.ready) {
            kafkaconsumerStringSykmelding.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
                val jsonMap: Map<String, String?> =
                    objectMapper.readValue(objectMapper.readValue<String>(objectMapper.readValue<String>(consumerRecord.value())))

                val receivedSykmelding = toReceivedSykmelding(jsonMap)
                kafkaproducerReceivedSykmelding.send(
                    ProducerRecord(
                        sm2013SyfoserviceSykmeldingCleanTopic,
                        receivedSykmelding.sykmelding.id,
                        receivedSykmelding
                    )
                )
                counter++
                if (counter % 1000 == 0) {
                    log.info("Melding sendt til kafka topic nr {}", counter)
                }
            }
        }
    }
}
