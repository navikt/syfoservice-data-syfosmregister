package no.nav.syfo.legeerklaring.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.legeerklaring.kafka.model.ReceivedLegeerklaring
import no.nav.syfo.legeerklaring.kafka.model.Status
import no.nav.syfo.legeerklaring.kafka.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.utils.getFileAsString
import no.nav.syfo.vedlegg.google.BucketService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.TopicPartition
import java.time.Duration

data class LegeerklaeringSak(
    val receivedLegeerklaering: ReceivedLegeerklaring,
    val validationResult: ValidationResult,
    val vedlegg: List<String>?
)

class LegeerkleringKafkaServiceTest : FunSpec({
    context("test legeerklaring mapper") {
        val kafkaConsumer = mockk<KafkaConsumer<String, ByteArray>>(relaxed = true)
        val bucketService = mockk<BucketService>(relaxed = true)
        val applicationState = ApplicationState(true, true)
        val kafkaProducer = mockk<KafkaProducer<String, ByteArray>>(relaxed = true)
        val receivedLegeerklaring = objectMapper.readValue<ReceivedLegeerklaring>(getFileAsString("src/test/resources/legeerklaring.json"))
        val legeErklaringService = LegeerkleringKafkaService(kafkaConsumer, "topic", applicationState, bucketService, kafkaProducer, "aiven-topci", "pale2bucket")
        test("Should read message and upload to bucket") {
            val simpleLegeerklaringKafkaMessage = LegeerklaeringSak(
                receivedLegeerklaering = receivedLegeerklaring,
                validationResult = ValidationResult(
                    status = Status.OK,
                    ruleHits = emptyList()
                ),
                vedlegg = null
            )

            val consumerRecords = ConsumerRecords<String, ByteArray>(
                mutableMapOf<TopicPartition, List<ConsumerRecord<String, ByteArray>>>(
                    TopicPartition("1", 1) to listOf(
                        ConsumerRecord(
                            "topic", 1, 1, "String",
                            objectMapper.writeValueAsBytes(simpleLegeerklaringKafkaMessage)
                        )
                    )
                )
            )
            every { bucketService.getObjects(receivedLegeerklaring.legeerklaering.id.toString()) } returns emptyList()
            every { kafkaConsumer.poll(any<Duration>()) } answers {
                applicationState.ready = false
                consumerRecords
            }

            legeErklaringService.run()

            verify(exactly = 1) { bucketService.create(match { it.startsWith(receivedLegeerklaring.msgId) }, any(), any()) }
            verify(exactly = 1) { kafkaProducer.send(any()) }
        }
    }
})
