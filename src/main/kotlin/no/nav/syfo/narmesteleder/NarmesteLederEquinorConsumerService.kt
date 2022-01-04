package no.nav.syfo.narmesteleder

import io.ktor.util.KtorExperimentalAPI
import java.time.Duration
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.log
import org.apache.kafka.clients.consumer.KafkaConsumer

class NarmesteLederEquinorConsumerService(
    private val kafkaConsumer: KafkaConsumer<String, SyfoServiceNarmesteLeder>,
    private val applicationState: ApplicationState,
    private val topic: String,
    private val databaseOracle: DatabaseOracle
) {
    private var counter = 0
    private var oppdaterte = 0

    suspend fun startConsumer() {
        GlobalScope.launch {
            var lastCounter = 0
            while (applicationState.ready) {
                if (lastCounter != counter) {
                    log.info("Lest: {} events, antall oppdaterte: {}", counter, oppdaterte)
                    lastCounter = counter
                }
                delay(10_000)
            }
        }

        kafkaConsumer.subscribe(listOf(topic))
        log.info("Starting consuming topic $topic")

        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ZERO).forEach {
                val oppdatertEpost = when {
                    it.value().nlEpost.contains("@statoil.no") -> {
                        it.value().nlEpost.replace("@statoil.no", "@equinor.com")
                    }
                    it.value().nlEpost.contains("@statoil.com") -> {
                        it.value().nlEpost.replace("@statoil.com", "@equinor.com")
                    }
                    else -> null
                }
                if (oppdatertEpost != null) {
                    databaseOracle.oppdaterEpostForEquinor(it.value().id, oppdatertEpost)
                    oppdaterte++
                }
                counter++
            }
        }
    }
}
