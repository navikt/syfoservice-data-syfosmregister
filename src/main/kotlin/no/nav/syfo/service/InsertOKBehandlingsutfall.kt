package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.model.Behandlingsutfall
import no.nav.syfo.model.Mapper
import no.nav.syfo.model.Status
import no.nav.syfo.model.UpdateEvent
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.lagreBehandlingsutfallAndCommit
import no.nav.syfo.persistering.db.postgres.sykmeldingHarBehandlingsutfall
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class InsertOKBehandlingsutfall(
    val kafkaConsumer: KafkaConsumer<String, String>,
    val databasePostgres: DatabasePostgres,
    val sykmeldingStatusCleanTopic: String,
    val applicationState: ApplicationState
) {

    fun run() {
        kafkaConsumer.subscribe(
            listOf(
                sykmeldingStatusCleanTopic
            )
        )
        var counterAll = 0
        var counterOppdatertBehandlingsutfall = 0
        var lastCounter = 0
        GlobalScope.launch {
            while (applicationState.ready) {
                if (lastCounter != counterAll) {
                    log.info(
                        "Lest {} sykmeldinger totalt, antall oppdaterte behandlingsutfall {}",
                        counterAll, counterOppdatertBehandlingsutfall
                    )
                    lastCounter = counterAll
                }
                delay(30000)
            }
        }
        while (applicationState.ready) {
            val updateEvents: List<UpdateEvent> =
                kafkaConsumer.poll(Duration.ofMillis(100)).map {
                    counterAll++
                    objectMapper.readValue<Map<String, String?>>(it.value())
                }
                    .map { Mapper.mapToUpdateEvent(it) }
                    .filter { it ->
                        it.sykmeldingId.length <= 64
                    }
            for (update in updateEvents) {
                try {
                    if (!databasePostgres.connection.sykmeldingHarBehandlingsutfall(update.sykmeldingId)) {
                        databasePostgres.connection.lagreBehandlingsutfallAndCommit(
                            Behandlingsutfall(
                                update.sykmeldingId,
                                ValidationResult(
                                    Status.OK,
                                    emptyList()
                                )
                            )
                        )
                        counterOppdatertBehandlingsutfall++
                    }
                } catch (ex: Exception) {
                    log.error("Noe gikk galt med sykmeldingId {}", update.sykmeldingId, ex)
                    applicationState.ready = false
                    break
                }
            }
        }
    }
}
