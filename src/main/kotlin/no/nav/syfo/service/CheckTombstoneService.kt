package no.nav.syfo.service

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.time.LocalDateTime

class CheckTombstoneService(val tombstoneConsumer: KafkaConsumer<String, String?>, val applicationState: ApplicationState) {
    suspend fun run() {
        var counter = 0
        var nullCounter = 0
        var duplicateCounter = 0
        val hashSet = hashSetOf<String>()
        val loggingJob = GlobalScope.launch {
            while (applicationState.ready) {
                log.info(
                    "Antall sykmeldinger sjekket: {}, antall null {}, duplikater {}",
                    counter,
                    nullCounter,
                    duplicateCounter
                )
                delay(20_000)
            }
        }

        var lastTime = LocalDateTime.now()

        while (lastTime.isAfter(LocalDateTime.now().minusMinutes(5))) {
            val records = tombstoneConsumer.poll(Duration.ofMillis(0))
            records.forEach {
                counter++
                if (hashSet.contains(it.key())) {
                    duplicateCounter++
                } else {
                    hashSet.add(it.key())
                }
                if (it.value() == null) {
                    nullCounter++
                }
            }
            if (!records.isEmpty) {
                lastTime = LocalDateTime.now()
            }
            delay(1)
        }
        log.info("All done")
        loggingJob.cancelAndJoin()
    }
}
