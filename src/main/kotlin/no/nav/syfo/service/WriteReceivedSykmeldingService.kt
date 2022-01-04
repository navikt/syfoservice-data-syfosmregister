package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.kafka.ReceivedSykmeldingKafkaProducer
import no.nav.syfo.log
import no.nav.syfo.model.Mapper.Companion.mapToUpdateEvent
import no.nav.syfo.model.toReceivedSykmelding
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.hentSykmeldingMedId
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class WriteReceivedSykmeldingService(
    private val applicationState: ApplicationState,
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val kafkaProducer: ReceivedSykmeldingKafkaProducer,
    private val sykmeldingTopic: String,
    private val databaseInterfacePostgres: DatabaseInterfacePostgres
) {

    fun run() {
        var counter = 0
        GlobalScope.launch {
            var lastCounter = counter
            while (applicationState.ready) {
                if (lastCounter != counter) {
                    log.info("Skrevet {} sykmeldinger til topic", counter)
                    lastCounter = counter
                }
                delay(120_000)
            }
        }
        kafkaConsumer.subscribe(listOf(sykmeldingTopic))
        while (applicationState.ready) {
            val sykmeldinger = kafkaConsumer.poll(Duration.ofMillis(100)).map {
                mapToUpdateEvent(objectMapper.readValue(it.value()))
            }
            for (update in sykmeldinger) {
                val sykmeldingDbModel = databaseInterfacePostgres.connection.hentSykmeldingMedId(update.sykmeldingId)!!
                val receivedSykmelding = toReceivedSykmelding(sykmeldingDbModel)
                kafkaProducer.publishToKafka(receivedSykmelding)
                counter++
            }
        }
    }
}
