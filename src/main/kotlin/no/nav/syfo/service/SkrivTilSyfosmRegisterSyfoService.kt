package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.aksessering.db.oracle.hentFravaerForSykmelding
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.log
import no.nav.syfo.model.FravarsPeriode
import no.nav.syfo.model.Mapper.Companion.mapToUpdateEvent
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.UpdateEvent
import no.nav.syfo.model.toReceivedSykmelding
import no.nav.syfo.model.toSykmeldingsdokument
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.SykmeldingDbModel
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
    var filterList = listOf(
        "1805031428norh13329.1",
        "1912050914gild39509.1"
    )
    var lastTimestamp = LocalDateTime.of(2010, 1, 1, 0, 0)
    fun run() {
        var counter = 0
        var lastCounter = 0
        var updateCounter = 0
        kafkaConsumer.subscribe(
            listOf(
                sykmeldingStatusCleanTopic
            )
        )
        GlobalScope.launch {
            while (applicationState.ready) {
                if (lastCounter != counter) {
                    log.info(
                        "Lest {} sykmeldinger totalt, antall som ikke er i syfosmregister {}, lastTimestamp {}",
                        counter, updateCounter, lastTimestamp
                    )
                    lastCounter = counter
                }
                delay(60000)
            }
        }
        while (applicationState.ready) {
            val listStatusSyfoService: List<UpdateEvent> =
                kafkaConsumer.poll(Duration.ofMillis(100)).map {
                    counter++
                    val map = objectMapper.readValue<Map<String, Any?>>(it.value())
                    val sykmeldingStatusTopicEvent = mapToUpdateEvent(map)
                    lastTimestamp = sykmeldingStatusTopicEvent.created
                    sykmeldingStatusTopicEvent
                }

            for (sykmeldingStatusTopicEvent in listStatusSyfoService) {
                if (!databasePostgres.connection.erSykmeldingsopplysningerLagret(sykmeldingStatusTopicEvent.sykmeldingId)) {
                    log.info(
                        "Fant ikke sykmelding med id {} og mottakId {}",
                        sykmeldingStatusTopicEvent.sykmeldingId,
                        sykmeldingStatusTopicEvent.mottakId
                    )
                    updateCounter++
                }
            }
        }
    }

    private fun getFravaer(map: Map<String, Any?>): List<FravarsPeriode>? {
        if (map.containsKey("SPM_HAR_FRAVAER") && map["SPM_HAR_FRAVAER"] == "1") {
            val fravaerResult = databaseOracle.hentFravaerForSykmelding(map["SPM_SM_SPORSMAL_ID"] as Int)
            return fravaerResult.rows.map {
                FravarsPeriode(
                    fom = LocalDate.parse(
                        it["FOM"]?.toString()?.substring(
                            0,
                            10
                        )
                    ), tom = LocalDate.parse(it["TOM"]?.toString()?.substring(0, 10))
                )
            }
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
        var insertedSykmeldinger = 0
        var lastCounter = 0

        GlobalScope.launch {
            while (applicationState.ready) {
                if (lastCounter != counter) {
                    log.info(
                        "Lest {} sykmeldinger totalt, antall oppdaterte ider {}, antall nye sykmeldinger {}, lastTimestamp {}",
                        counter, counterIdUpdates, insertedSykmeldinger, lastTimestamp
                    )
                    lastCounter = counter
                }
                delay(120_000)
            }
        }
        while (applicationState.ready) {
            val updateEvents: List<ReceivedSykmelding> =
                kafkaConsumer.poll(Duration.ofMillis(100)).map {
                    objectMapper.readValue<Map<String, String?>>(it.value())
                }
                    .filter { it ->
                        counter++
                        lastTimestamp = LocalDateTime.parse((it["CREATED"].toString().substring(0, 19)))
                        filterList.contains(it["MELDING_ID"])
                    }
                    .map { toReceivedSykmelding(it) }
            for (update in updateEvents) {
                try {
                    log.info("updating sykmelding with id {}, mottak_id {}", update.sykmelding.id, update.navLogId)
                    val sykmeldingDb = databasePostgres.connection.hentSykmelding(convertToMottakid(update.navLogId))
                    if (sykmeldingDb != null) {
                        log.info("found sykmelding in DB, updating id and inserting sykmeldingsdokument")
                        if (sykmeldingDb.sykmeldingsopplysninger.id != update.sykmelding.id) {
                            val oldId = sykmeldingDb.sykmeldingsopplysninger.id
                            val newSykmeldingModel = SykmeldingDbModel(
                                sykmeldingDb.sykmeldingsopplysninger.copy(id = update.sykmelding.id),
                                toSykmeldingsdokument(update)
                            )
                            databasePostgres.connection.deleteAndInsertSykmelding(
                                oldId,
                                newSykmeldingModel
                            )
                            counterIdUpdates++
                        }
                    } else {
                        log.info("inserting sykmelding with id {}, mottak_id {}", update.sykmelding.id, update.navLogId)
                        databasePostgres.connection.lagreReceivedSykmelding(update)
                        insertedSykmeldinger++
                    }
                } catch (ex: Exception) {
                    log.error("Noe gikk galt med mottakid {}", update.navLogId, ex)
                    applicationState.ready = false
                    break
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
                        filterList.contains(it["MELDING_ID"])
                    }
                    .map { toReceivedSykmelding(it) }
                    .toList()

            for (receivedSykmelding in receivedSykmeldings) {

                if (!databasePostgres.connection.erSykmeldingsopplysningerLagret(
                        receivedSykmelding.navLogId
                    )
                ) {
                    log.info("did not find mottakId {}, inserting sykmelding", receivedSykmelding.navLogId)
                    databasePostgres.connection.lagreReceivedSykmelding(receivedSykmelding)
                    insertedCounter++
                }
            }
        }
    }
}
