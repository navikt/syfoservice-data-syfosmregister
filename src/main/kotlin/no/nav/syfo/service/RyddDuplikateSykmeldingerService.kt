package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.log
import no.nav.syfo.model.Mapper.Companion.mapToUpdateEvent
import no.nav.syfo.model.Status
import no.nav.syfo.model.UpdateEvent
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.SykmeldingBehandlingsutfallDbModel
import no.nav.syfo.persistering.db.postgres.hentSykmeldingListeMedBehandlingsutfall
import no.nav.syfo.persistering.db.postgres.lagreBehandlingsutfall
import no.nav.syfo.persistering.db.postgres.slettSykmeldingOgStatus
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.time.LocalDateTime
import java.time.Month

class RyddDuplikateSykmeldingerService(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val databasePostgres: DatabaseInterfacePostgres,
    private val sykmeldingStatusCleanTopic: String,
    private val applicationState: ApplicationState
) {

    fun ryddDuplikateSykmeldinger() {
        kafkaConsumer.subscribe(
            listOf(
                sykmeldingStatusCleanTopic
            )
        )
        var counterAll = 0
        var counter = 0
        var counterDuplikat = 0
        var counterOppdatertBehandlingsutfall = 0
        var lastCounter = 0

        GlobalScope.launch {
            while (applicationState.ready) {
                delay(30000)
                if (lastCounter != counterAll) {
                    log.info(
                        "Lest {} sykmeldinger totalt, antall som har passert filter: {}, antall duplikater: {}, antall oppdaterte behandlingsutfall: {}",
                        counterAll,
                        counter,
                        counterDuplikat,
                        counterOppdatertBehandlingsutfall
                    )
                    lastCounter = counterAll
                }
            }
        }

        while (applicationState.ready) {
            val updateEvents: List<UpdateEvent> =
                kafkaConsumer.poll(Duration.ofMillis(100)).map {
                    counterAll++
                    objectMapper.readValue<Map<String, String?>>(it.value())
                }
                    .map { mapToUpdateEvent(it) }
                    .filter { it ->
                        it.sykmeldingId.length <= 64
                    }
                    .filter { it.created.isAfter(LocalDateTime.of(2019, Month.SEPTEMBER, 1, 12, 0, 0)) }
                    .filter { it.created.isBefore(LocalDateTime.of(2019, Month.NOVEMBER, 1, 12, 0, 0)) }
            for (update in updateEvents) {
                try {
                    val sykmeldingDb =
                        databasePostgres.connection.hentSykmeldingListeMedBehandlingsutfall(convertToMottakid(update.mottakId))
                    counter++
                    if (sykmeldingDb.size == 2) {
                        val sykmeldingFraSS: SykmeldingBehandlingsutfallDbModel? = sykmeldingDb.find { it.sykmeldingsopplysninger.id == update.sykmeldingId }
                        val sykmeldingFraRegister: SykmeldingBehandlingsutfallDbModel? = sykmeldingDb.find { it.sykmeldingsopplysninger.id != update.sykmeldingId }

                        if (sykmeldingFraSS == null || sykmeldingFraRegister == null) {
                            log.error("Ingen av sykmeldingene matcher id fra syfoservice for mottakId {}, går videre...",
                                sykmeldingDb[0].sykmeldingsopplysninger.mottakId)
                            continue
                        }

                        if (sykmeldingFraSS.behandlingsutfall == null && (sykmeldingFraRegister.behandlingsutfall != null && sykmeldingFraRegister.behandlingsutfall.behandlingsutfall.status != Status.INVALID)) {
                            databasePostgres.connection.lagreBehandlingsutfall(sykmeldingFraRegister.behandlingsutfall.copy(id = sykmeldingFraSS.sykmeldingsopplysninger.id))
                            counterOppdatertBehandlingsutfall++
                        }
                        databasePostgres.connection.slettSykmeldingOgStatus(sykmeldingFraRegister.sykmeldingsopplysninger.id)
                        counterDuplikat++
                    }
                } catch (ex: Exception) {
                    log.error("Noe gikk galt med mottakid {}", update.mottakId, ex)
                    applicationState.ready = false
                    break
                }
            }
        }
    }
}
