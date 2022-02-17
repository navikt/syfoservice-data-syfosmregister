package no.nav.syfo.sykmelding.aivenmigrering

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import no.nav.syfo.model.Sykmeldingsdokument
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Duration

@DelicateCoroutinesApi
class AivenMigreringService(
    private val onPremConsumer: KafkaConsumer<String, Sykmeldingsdokument>,
    private val aivenProducer: KafkaProducer<String, Sykmeldingsdokument>,
    private val topics: Map<String, String>,
    private val applicationState: ApplicationState
) {

    @DelicateCoroutinesApi
    companion object {
        var counter = 0

        init {
            GlobalScope.launch(Dispatchers.IO) {
                log.info("Starting logging")
                while (true) {
                    log.info(
                        "Antall meldinger som er lest. $counter"
                    )
                    delay(60_000)
                }
            }
        }
    }

    fun start() {
        onPremConsumer.subscribe(topics.keys)
        log.info("Started consuming topics")
        while (applicationState.ready) {
            onPremConsumer.poll(Duration.ofSeconds(1)).forEach {
                val producerRecord = ProducerRecord(
                    topics[it.topic()]!!, it.key(), it.value()
                )
                producerRecord.headers().add("source", "on-prem".toByteArray())
                try {
                    aivenProducer.send(producerRecord).get()
                } catch (e: Exception) {
                    log.error("Error sending message to kafka", e)
                    throw e
                }
                counter++
            }
        }
    }
}
