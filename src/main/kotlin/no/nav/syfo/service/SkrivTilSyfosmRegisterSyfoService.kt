package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Duration
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.log
import no.nav.syfo.model.Mapper.Companion.mapToSyfoserviceStatus
import no.nav.syfo.model.Mapper.Companion.mapToUpdateEvent
import no.nav.syfo.model.Mapper.Companion.toStatusEventList
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SykmeldingStatusEvent
import no.nav.syfo.model.UpdateEvent
import no.nav.syfo.model.toReceivedSykmelding
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.deleteAndInsertSykmelding
import no.nav.syfo.persistering.db.postgres.erSykmeldingsopplysningerLagret
import no.nav.syfo.persistering.db.postgres.hentSykmelding
import no.nav.syfo.persistering.db.postgres.lagreReceivedSykmelding
import no.nav.syfo.persistering.db.postgres.oppdaterSykmeldingStatus
import org.apache.kafka.clients.consumer.KafkaConsumer

class SkrivTilSyfosmRegisterSyfoService(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val databasePostgres: DatabaseInterfacePostgres,
    private val sykmeldingStatusCleanTopic: String,
    private val applicationState: ApplicationState,
    private val lastIndexSyfoservice: Int
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
                    .filter { it ->
                        it.sykmeldingId.length <= 64
                    }
            for (update in updateEvents) {
                try {
                    val sykmeldingDb = databasePostgres.connection.hentSykmelding(convertToMottakid(update.mottakId))
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
                    log.error("Noe gikk galt med mottakid {}", update.mottakId, ex)
                    applicationState.ready = false
                    break
                }

                if (counter >= lastCounter + 1000) {
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

    fun insertMissingSykmeldinger() {
        kafkaConsumer.subscribe(
            listOf(
                sykmeldingStatusCleanTopic
            )
        )
        var lastCount = 0
        var totalCounter = 0
        var insertedCounter = 0
        while (applicationState.ready) {
            val receivedSykmeldings: List<ReceivedSykmelding> =
                kafkaConsumer.poll(Duration.ofMillis(100)).map {
                    totalCounter++
                    objectMapper.readValue<Map<String, String?>>(it.value())
                }
                    .asSequence()
                    .filter { checkLastIndex(it) }
                    .map { toReceivedSykmelding(it) }
                    .toList()

            for (receivedSykmelding in receivedSykmeldings) {
                if (!databasePostgres.connection.erSykmeldingsopplysningerLagret(
                        receivedSykmelding.sykmelding.id,
                        receivedSykmelding.navLogId
                    )
                ) {
                    databasePostgres.connection.lagreReceivedSykmelding(receivedSykmelding)
                    insertedCounter++
                    log.info("Inserted total {} sykmeldinger, id {}", insertedCounter, receivedSykmelding.sykmelding.id)
                }
            }
            if (totalCounter > lastCount + 50_000) {
                log.info("search through {} sykmeldinger", totalCounter)
                lastCount = totalCounter
            }
        }
    }

    private fun checkLastIndex(jsonMap: Map<String, String?>): Boolean {
        return (jsonMap["SYKMELDING_DOK_ID"] ?: error("SYKMELDING_DOK_ID is missing")).toInt() > lastIndexSyfoservice
    }
}
