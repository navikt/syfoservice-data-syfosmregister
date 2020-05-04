package no.nav.syfo.service

import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import org.apache.kafka.clients.consumer.KafkaConsumer

class CheckTombstoneService(val tombstoneConsumer: KafkaConsumer<String, String?>, val applicationState: ApplicationState) {
    private val idToCheck = "e8486578-c9a1-4e02-a11d-a1b3d27e1e60"
    suspend fun run() {
        var counter = 0
        var nullCounter = 0
        val loggingJob = GlobalScope.launch {
            while (applicationState.ready) {
                log.info(
                    "Antall sykmeldinger sjekket: {}, antall null {}",
                    counter,
                    nullCounter
                )
                delay(60_000)
            }
        }

        var lastTime = LocalDateTime.now()

        while (lastTime.isAfter(LocalDateTime.now().minusMinutes(5))) {
            val records = tombstoneConsumer.poll(Duration.ofMillis(0))
            records.forEach {
                counter++
                if (it.value() == null) {
                    nullCounter++
                }
            }
            if (!records.isEmpty) {
                lastTime = LocalDateTime.now()
            }
            delay(100)
        }
        log.info("All done")
        loggingJob.cancelAndJoin()
    }
}