package no.nav.syfo.vedlegg

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.vedlegg.google.BucketService
import no.nav.syfo.vedlegg.model.VedleggMessage
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.util.UUID

class VedleggMigreringService(
    private val vedleggOnPremConsumer: KafkaConsumer<String, VedleggMessage>,
    private val topic: String,
    private val applicationState: ApplicationState,
    private val sykmeldingBucketService: BucketService,
    private val paleBucketService: BucketService
) {
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

    @DelicateCoroutinesApi
    fun start() {
        vedleggOnPremConsumer.subscribe(listOf(topic))
        log.info("Started consuming vedleggtopic")
        while (applicationState.ready) {
            vedleggOnPremConsumer.poll(Duration.ofSeconds(1)).forEach {
                val vedlegg = it.value()
                when (vedlegg.source) {
                    "syfosmmottak" -> {
                        sykmeldingBucketService.create("${it.key()}/${UUID.randomUUID()}", objectMapper.writeValueAsBytes(vedlegg))
                    }
                    "pale-2" -> {
                        paleBucketService.create("${it.key()}/${UUID.randomUUID()}", objectMapper.writeValueAsBytes(vedlegg))
                    }
                    else -> {
                        log.error("Har mottatt vedlegg med ukjent source ${vedlegg.source}")
                        throw IllegalStateException("Ukjent source")
                    }
                }
                counter++
            }
        }
    }
}
