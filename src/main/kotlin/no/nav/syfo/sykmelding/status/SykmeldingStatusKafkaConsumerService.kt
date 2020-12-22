package no.nav.syfo.sykmelding.status

import com.fasterxml.jackson.module.kotlin.readValue
import kafka.Kafka
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.kafka.KafkaCredentials
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.log
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.objectMapper
import no.nav.syfo.utils.JacksonKafkaSerializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SykmeldingStatusKafkaConsumerService(private val env: Environment, credentials: KafkaCredentials) {
    val kafkaConsumer: KafkaConsumer<String, String>

    init {
        val kafkaBaseConfig = loadBaseConfig(env, credentials)

        val consumerProperties = kafkaBaseConfig.toConsumerConfig(
            "syfo-macgyver-consumer",
            valueDeserializer = StringDeserializer::class
        )
        consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100")
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
            listOf(env.sykmeldingStatusTopic)
        )
        while (!done) {
            kafkaConsumer.poll(Duration.ofMillis(100)).forEach {
                val status = objectMapper.readValue<SykmeldingStatusKafkaMessageDTO>(it.value())
                lastDate = OffsetDateTime.ofInstant(Instant.ofEpochMilli(it.timestamp()), ZoneOffset.UTC)
                if(it.key() == sykmelidngId) {
                    statuser.add(status)
                }
                if(statuser.size > 1) {
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
