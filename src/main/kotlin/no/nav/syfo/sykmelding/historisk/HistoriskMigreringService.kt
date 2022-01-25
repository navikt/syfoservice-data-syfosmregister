package no.nav.syfo.sykmelding.historisk

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Duration
import java.util.UUID

class HistoriskMigreringService(
    private val onPremConsumer: KafkaConsumer<String, String?>,
    private val aivenProducer: KafkaProducer<String, String?>,
    private val topics: List<String>,
    private val historiskTopic: String,
    private val applicationState: ApplicationState,
    private val environment: Environment
) {
    @DelicateCoroutinesApi
    companion object {
        var counter = 0
        var counterAvvist = 0
        var counterOk = 0
        var counterManuellBehandling = 0
        var counterBehandlingsutfall = 0
        var counterManuell = 0
        var counterSmReg = 0

        init {
            GlobalScope.launch(Dispatchers.IO) {
                log.info("Starting logging")
                while (true) {
                    log.info(
                        "Antall meldinger som er lest: $counter. Fra avvist: $counterAvvist, fra manuell behandling: $counterManuellBehandling, " +
                            "fra ok: $counterOk, fra behandlingsutfall: $counterBehandlingsutfall, fra manuell: $counterManuell, fra smreg: $counterSmReg"
                    )
                    delay(60_000)
                }
            }
        }
    }

    @DelicateCoroutinesApi
    fun start() {
        onPremConsumer.subscribe(topics)
        log.info("Started consuming topics")
        while (applicationState.ready) {
            onPremConsumer.poll(Duration.ofSeconds(1)).forEach {
                val key = it.key() ?: "gen_${UUID.randomUUID()}"
                val producerRecord = ProducerRecord(
                    historiskTopic, key, it.value()
                )
                producerRecord.headers().add("topic", it.topic().toByteArray())

                aivenProducer.send(producerRecord) { _, error ->
                    log.error("Error producing to kafka: key ${it.key()}, fra topic ${it.topic()}", error)
                    applicationState.ready = false
                }

                when (it.topic()) {
                    environment.avvistBehandlingTopic -> {
                        counterAvvist++
                    }
                    environment.automatiskBehandlingTopic -> {
                        counterOk++
                    }
                    environment.manuellBehandlingTopic -> {
                        counterManuellBehandling++
                    }
                    environment.behandlingsutfallTopic -> {
                        counterBehandlingsutfall++
                    }
                    environment.manuellTopic -> {
                        counterManuell++
                    }
                    environment.smRegistreringTopic -> {
                        counterSmReg++
                    }
                }
                counter++
            }
        }
    }
}
