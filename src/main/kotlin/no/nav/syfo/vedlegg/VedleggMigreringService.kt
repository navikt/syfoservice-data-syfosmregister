package no.nav.syfo.vedlegg

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import no.nav.syfo.vedlegg.google.BucketUploadService
import no.nav.syfo.vedlegg.model.VedleggMessage
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class VedleggMigreringService(
    private val vedleggOnPremConsumer: KafkaConsumer<String, VedleggMessage>,
    private val topic: String,
    private val applicationState: ApplicationState,
    private val sykmeldingBucketUploadService: BucketUploadService,
    private val paleBucketUploadService: BucketUploadService
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
                        sykmeldingBucketUploadService.create(it.key(), vedlegg)
                    }
                    "pale-2" -> {
                        paleBucketUploadService.create(it.key(), vedlegg)
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
