package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Duration
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.log
import no.nav.syfo.model.Mapper.Companion.mapToSyfoserviceStatus
import no.nav.syfo.model.Mapper.Companion.mapToUpdateEvent
import no.nav.syfo.model.Mapper.Companion.toStatusEventList
import no.nav.syfo.model.SykmeldingStatusEvent
import no.nav.syfo.model.UpdateEvent
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.deleteAndInsertSykmelding
import no.nav.syfo.persistering.db.postgres.hentSykmelding
import no.nav.syfo.persistering.db.postgres.oppdaterSykmeldingStatus
import org.apache.kafka.clients.consumer.KafkaConsumer

class SkrivTilSyfosmRegisterSyfoService(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val databasePostgres: DatabaseInterfacePostgres,
    private val sykmeldingStatusCleanTopic: String,
    private val applicationState: ApplicationState
) {

    fun run() {
        var counter = 0
        var lastCounter = 0
        kafkaConsumer.subscribe(
            listOf(
                sykmeldingStatusCleanTopic
            )
        )
        log.info("Started kafkakonsumer")
        while (applicationState.ready) {
            val listStatusSyfoService: List<SykmeldingStatusEvent> =
                kafkaConsumer.poll(Duration.ofMillis(100)).map {
                    objectMapper.readValue<Map<String, String?>>(it.value())
                }
                    .map { mapToSyfoserviceStatus(it) }
                    .map { toStatusEventList(it) }
                    .flatten()

            if (listStatusSyfoService.isNotEmpty()) {
                databasePostgres.connection.oppdaterSykmeldingStatus(listStatusSyfoService)

                counter += listStatusSyfoService.size
                if (counter >= lastCounter + 50_000) {
                    log.info("Inserted {} statuses", counter)
                    lastCounter = counter
                }
            }
        }
    }

    fun updateId() {
        kafkaConsumer.subscribe(
            listOf(
                sykmeldingStatusCleanTopic
            )
        )
        var counter = 0
        var counterIdUpdates = 0
        var lastCounter = 0
        while (applicationState.ready) {
            val updateEvents: List<UpdateEvent> =
                kafkaConsumer.poll(Duration.ofMillis(100)).map {
                    objectMapper.readValue<Map<String, String?>>(it.value())
                }
                    .map { mapToUpdateEvent(it) }
            for (update in updateEvents) {
                try {
                    val sykmeldingDb = databasePostgres.connection.hentSykmelding(update.mottakId)
                    counter++
                    if (sykmeldingDb != null) {
                        if (sykmeldingDb.sykmeldingsopplysninger.id != update.sykmeldingId) {
                            val oldId = sykmeldingDb.sykmeldingsopplysninger.id
                            sykmeldingDb.sykmeldingsopplysninger.id = update.sykmeldingId
                            sykmeldingDb.sykmeldingsdokument.sykmelding =
                                sykmeldingDb.sykmeldingsdokument.sykmelding.copy(id = update.sykmeldingId)
                            sykmeldingDb.sykmeldingsdokument.id = update.sykmeldingId
                            databasePostgres.connection.deleteAndInsertSykmelding(
                                oldId,
                                sykmeldingDb
                            )
                            counterIdUpdates++
                        }
                    }
                } catch (ex: Exception) {
                    log.error("Noe gikk galt med mottakid {}, exeption type {}", update.mottakId, ex.javaClass)
                    applicationState.ready = false
                    break
                }

                if (counter >= lastCounter + 10_000) {
                    log.info(
                        "Updated {} sykmeldinger, mottattTidspunkt oppdatert: {}, Ider og tidspunkt oppdatert: {}",
                        counter,
                        counterIdUpdates
                    )
                    lastCounter = counter
                }
            }
        }
    }
}
