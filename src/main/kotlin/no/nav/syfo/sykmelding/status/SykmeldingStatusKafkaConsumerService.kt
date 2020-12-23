package no.nav.syfo.sykmelding.status

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.Environment
import no.nav.syfo.kafka.KafkaCredentials
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.log
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.objectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer

class SykmeldingStatusKafkaConsumerService(private val env: Environment, credentials: KafkaCredentials) {
    val kafkaConsumer: KafkaConsumer<String, String>

    init {
        val kafkaBaseConfig = loadBaseConfig(env, credentials)

        val consumerProperties = kafkaBaseConfig.toConsumerConfig(
            "syfo-macgyver-consumer",
            valueDeserializer = StringDeserializer::class
        )
        consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100")
        consumerProperties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        kafkaConsumer = KafkaConsumer(consumerProperties)
    }

    suspend fun start() {
        val sykmelidngId = "00066f8f-6a57-4475-a6c5-d2dfb0e7652a"
        val statuser = mutableListOf<SykmeldingStatusKafkaMessageDTO>()
        var logging = true
        var lastDate: OffsetDateTime? = null
        var counter = 0
        var done = false

        val logger = GlobalScope.launch {
            while (logging) {
                log.info("Lest: $counter events, timestamp $lastDate")
                delay(10_000)
            }
        }
        kafkaConsumer.subscribe(
            listOf(env.sykmeldingStatusTopic), object : ConsumerRebalanceListener {
                override fun onPartitionsRevoked(partitions: MutableCollection<TopicPartition>?) {
                    TODO("Not yet implemented")
                }

                override fun onPartitionsAssigned(partitions: MutableCollection<TopicPartition>?) {
                    kafkaConsumer.seekToBeginning(partitions)
                }
            }
        )

        while (!done) {
            kafkaConsumer.poll(Duration.ofMillis(100)).forEach {
                val status = objectMapper.readValue<SykmeldingStatusKafkaMessageDTO>(it.value())
                lastDate = OffsetDateTime.ofInstant(Instant.ofEpochMilli(it.timestamp()), ZoneOffset.UTC)
                counter++
                if (it.key() == sykmelidngId) {
                    statuser.add(status)
                }
                if (statuser.size > 1) {
                    done = true
                }
            }
        }
        log.info("Done")
        statuser.forEach {
            log.info("Status ${it.event.statusEvent} timestamp: ${it.event.timestamp}")
        }
        logging = false
        logger.join()
    }
}
