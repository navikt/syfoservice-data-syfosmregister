package no.nav.syfo.service

import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import org.apache.kafka.clients.consumer.KafkaConsumer

class CheckTombstoneService(val tombstoneConsumer: KafkaConsumer<String, String?>, val applicationState: ApplicationState) {
    private val idToCheck = "e8486578-c9a1-4e02-a11d-a1b3d27e1e60"
    fun run() {
        var counter = 0
        val loggingJob = GlobalScope.launch {
            while (applicationState.ready) {
                log.info(
                    "Antall sykmeldinger sjekket: {}",
                    counter
                )
                delay(10_000)
            }
        }

        var lastTime = LocalDateTime.now()

        while (lastTime.isBefore(LocalDateTime.now().minusMinutes(5))) {
            val records = tombstoneConsumer.poll(Duration.ofMillis(0))
            records.forEach {
                counter++
                if (it.key() == idToCheck) {
                    when (it.value()) {
                        null -> log.info("Found tombstone for id {}", it.key())
                        else -> log.info("Found bekreftet sykmelding for id {}", it.key())
                    }
                }
            }
            if (!records.isEmpty) {
                lastTime = LocalDateTime.now()
            }
        }
        log.info("All done")
        runBlocking {
            loggingJob.cancelAndJoin()
        }
    }
}
