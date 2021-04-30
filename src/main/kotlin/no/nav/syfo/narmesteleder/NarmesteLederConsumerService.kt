package no.nav.syfo.narmesteleder

import io.ktor.util.KtorExperimentalAPI
import java.time.Duration
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import org.apache.kafka.clients.consumer.KafkaConsumer

@KtorExperimentalAPI
class NarmesteLederConsumerService(
    private val kafkaConsumer: KafkaConsumer<String, SyfoServiceNarmesteLeder>,
    private val applicationState: ApplicationState,
    private val topic: String,
    private val narmesteLederMappingService: NarmesteLederMappingService,
    private val narmesteLederResponseKafkaProducer: NarmesteLederResponseKafkaProducer
) {
    private var counter = 0
    private var feiledeEvents = 0

    suspend fun startConsumer() {
        GlobalScope.launch {
            var lastCounter = 0
            while (applicationState.ready) {
                if (lastCounter != counter) {
                    log.info("Lest: {} events, antall feil: {}", counter, feiledeEvents)
                    lastCounter = counter
                }
                delay(10_000)
            }
        }

        kafkaConsumer.subscribe(listOf(topic))
        log.info("Starting consuming topic $topic")

        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ZERO).forEach {
                try {
                    val nlResponse = narmesteLederMappingService.mapSyfoServiceNarmesteLederTilNlResponse(it.value())
                    narmesteLederResponseKafkaProducer.publishToKafka(nlResponse)
                    counter++
                } catch (e: IllegalStateException) {
                    log.error("Noe gikk galt for key ${it.key()}")
                    feiledeEvents++
                }
            }
            delay(1L)
        }
    }
}
