package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Duration
import java.time.LocalDate
import no.nav.syfo.aksessering.db.oracle.hentFravaerForSykmelding
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.log
import no.nav.syfo.model.FravarsPeriode
import no.nav.syfo.model.Mapper.Companion.mapToSykmeldingStatusTopicEvent
import no.nav.syfo.model.Mapper.Companion.mapToUpdateEvent
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SykmeldingStatusTopicEvent
import no.nav.syfo.model.UpdateEvent
import no.nav.syfo.model.toReceivedSykmelding
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.deleteAndInsertSykmelding
import no.nav.syfo.persistering.db.postgres.erSykmeldingsopplysningerLagret
import no.nav.syfo.persistering.db.postgres.hentSykmelding
import no.nav.syfo.persistering.db.postgres.lagreReceivedSykmelding
import org.apache.kafka.clients.consumer.KafkaConsumer

class SkrivTilSyfosmRegisterSyfoService(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val databasePostgres: DatabaseInterfacePostgres,
    private val sykmeldingStatusCleanTopic: String,
    private val applicationState: ApplicationState,
    private val updateStatusService: UpdateStatusService,
    private val databaseOracle: DatabaseOracle
) {

    fun run() {
        var counter = 0
        var lastCounter = 0
        var emptyCounter = 0
        kafkaConsumer.subscribe(
            listOf(
                sykmeldingStatusCleanTopic
            )
        )
        log.info("Started kafkakonsumer")
        val map = HashMap<String, Int>()
        while (applicationState.ready) {
            val listStatusSyfoService: List<SykmeldingStatusTopicEvent> =
                kafkaConsumer.poll(Duration.ofMillis(100)).map {
                    val map = objectMapper.readValue<Map<String, Any?>>(it.value())
                    mapToSykmeldingStatusTopicEvent(map, getFravaer(map))
                }.filter { it ->
                    it.sykmeldingId.length <= 64
                }

            for (sykmeldingStatusTopicEvent in listStatusSyfoService) {
                updateStatusService.updateSykemdlingStatus(sykmeldingStatusTopicEvent)
            }

            if (listStatusSyfoService.isNotEmpty()) {
                counter += listStatusSyfoService.size
                if (counter >= lastCounter + 10_000) {
                    log.info("Mapped {} statuses", counter)
                    lastCounter = counter
                }
            }
        }
    }

    private fun getFravaer(map: Map<String, Any?>): List<FravarsPeriode>? {
        if (map.containsKey("SPM_HAR_FRAVAER") && map["SPM_HAR_FRAVAER"] == "1") {
            val fravaerResult = databaseOracle.hentFravaerForSykmelding(map["SPM_SM_SPORSMAL_ID"] as Int)
            return fravaerResult.rows.map { FravarsPeriode(fom = LocalDate.parse(it["FOM"]?.toString()?.substring(0, 10)), tom = LocalDate.parse(it["TOM"]?.toString()?.substring(0, 10))) }
        }
        return null
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
                    .filter {
                        (it["MELDING_ID"] ?: error("Missing MELDING_ID")).length <= 64
                    }
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
}
