package no.nav.syfo.legeerklaring.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.legeerklaringObjectMapper
import no.nav.syfo.log
import no.nav.syfo.model.LegeerklaeringSak
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class LegeerkleringKafkaService(private val kafkaConsumer: KafkaConsumer<String, ByteArray>, private val topic: String, private val applicationState: ApplicationState) {

    @DelicateCoroutinesApi
    companion object {
        var counter = 0

        init {
            GlobalScope.launch(Dispatchers.IO) {
                log.info("Starting logging")
                while (true) {
                    log.info(
                        "Antall vedlegg som er lest. $counter"
                    )
                    delay(10_000)
                }
            }
        }
    }
    fun start() {
        kafkaConsumer.subscribe(listOf(topic))

        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ofSeconds(10)).forEach {
                counter++
                if (it.value().size > 1_000_000) {
                    val legeerklaring = legeerklaringObjectMapper.readValue<LegeerklaeringSak>(it.value())
                    val fellesformatSize = legeerklaring.receivedLegeerklaering.fellesformat.toByteArray()
                    log.info("Found message bigger than 1mb, key: ${it.key()}, total size: ${it.value().size}, fellesformat size: ${fellesformatSize.size}")
                }
            }
        }
    }
}
