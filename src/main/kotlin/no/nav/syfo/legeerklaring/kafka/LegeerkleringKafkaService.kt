package no.nav.syfo.legeerklaring.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.treeToValue
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.legeerklaring.kafka.model.LegeerklaringKafkaMessage
import no.nav.syfo.legeerklaring.kafka.model.OnPremLegeerklaringKafkaMessage
import no.nav.syfo.legeerklaring.kafka.model.ReceivedLegeerklaring
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.vedlegg.google.BucketService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Duration

class LegeerkleringKafkaService(
    private val kafkaConsumer: KafkaConsumer<String, ByteArray>,
    private val onPremTopic: String,
    private val applicationState: ApplicationState,
    val bucketService: BucketService,
    val kafkaProducer: KafkaProducer<String, ByteArray>,
    val aivenTopic: String,
    val pale2Bucket: String
) {

    @DelicateCoroutinesApi
    companion object {
        var counter = 0

        init {
            GlobalScope.launch(Dispatchers.IO) {
                log.info("Starting logging")
                while (true) {
                    log.info(
                        "Antall legeerkleringer som er migrert. $counter"
                    )
                    delay(10_000)
                }
            }
        }
    }

    suspend fun run() {
        while (applicationState.ready) {
            try {
                start()
            } catch (ex: Exception) {
                kafkaConsumer.unsubscribe()
                log.error("Error running pale2 consumer, waiting 10 seconds to restart", ex)
                delay(10_000)
            }
        }
    }

    private fun start() {
        kafkaConsumer.subscribe(listOf(onPremTopic))

        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ofSeconds(10)).forEach {

                val legeerklaringmessage = objectMapper.readValue<OnPremLegeerklaringKafkaMessage>(it.value())
                val legeerklaring = objectMapper.treeToValue<ReceivedLegeerklaring>(legeerklaringmessage.receivedLegeerklaering)
                val vedlegg = when (legeerklaringmessage.vedlegg == null) {
                    true -> bucketService.getObjects(legeerklaring.legeerklaering.id)
                    else -> legeerklaringmessage.vedlegg
                }
                val aivenMessage = LegeerklaringKafkaMessage(
                    legeerklaeringObjectId = legeerklaring.msgId,
                    validationResult = legeerklaringmessage.validationResult,
                    vedlegg = vedlegg
                )
                val byteArray = objectMapper.writeValueAsBytes(legeerklaringmessage.receivedLegeerklaering)
                val key = legeerklaring.legeerklaering.id
                val producerRecord = ProducerRecord(aivenTopic, key, objectMapper.writeValueAsBytes(aivenMessage))
                producerRecord.headers().add("source", "macgyver".toByteArray())
                bucketService.create(legeerklaring.msgId, byteArray, pale2Bucket)
                kafkaProducer.send(producerRecord).get()

                counter++
            }
        }
    }
}
