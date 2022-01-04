package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Duration

class MapSykmeldingStringToSykemldignJsonMap(
    private val kafkaconsumerStringSykmelding: KafkaConsumer<String, String>,
    private val cleanSykmeldingProducer: KafkaProducer<String, Map<String, String?>>,
    private val cleanSykmeldingTopic: String,
    private val sm2013SyfoserviceSykmeldingStringTopic: String,
    private var applicationState: ApplicationState
) {
    fun run() {
        var counter = 0
        kafkaconsumerStringSykmelding.subscribe(
            listOf(sm2013SyfoserviceSykmeldingStringTopic)
        )
        log.info("Started kafkaConsumer on topic {}", kafkaconsumerStringSykmelding)
        while (applicationState.ready) {
            kafkaconsumerStringSykmelding.poll(Duration.ofMillis(100)).forEach { consumerRecord ->
                val jsonMap: Map<String, String?> =
                    objectMapper.readValue(objectMapper.readValue<String>(objectMapper.readValue<String>(consumerRecord.value())))

                cleanSykmeldingProducer.send(
                    ProducerRecord(
                        cleanSykmeldingTopic,
                        jsonMap["MOTTAK_ID"].toString(),
                        jsonMap
                    )
                )
                counter++
                if (counter % 10_000 == 0) {
                    log.info("Melding sendt til kafka topic nr {}", counter)
                }
            }
        }
    }
}
