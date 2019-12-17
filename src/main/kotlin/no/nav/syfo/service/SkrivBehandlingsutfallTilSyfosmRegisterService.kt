package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Duration
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.log
import no.nav.syfo.model.Behandlingsutfall
import no.nav.syfo.model.Mapper
import no.nav.syfo.model.Status
import no.nav.syfo.model.UpdateEvent
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.hentSykmeldingMedId
import no.nav.syfo.persistering.db.postgres.opprettBehandlingsutfall
import org.apache.kafka.clients.consumer.KafkaConsumer

class SkrivBehandlingsutfallTilSyfosmRegisterService(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val databasePostgres: DatabaseInterfacePostgres,
    private val sykmeldingStatusCleanTopic: String,
    private val applicationState: ApplicationState
) {

    fun leggInnBehandlingsutfall() {
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
                    .map { Mapper.mapToUpdateEvent(it) }
                    .filter { it ->
                        it.sykmeldingId.length <= 64
                    }
            for (update in updateEvents) {
                try {
                    // sjekker om sm finnes (det skal den gjøre)
                    val sykmeldingDb = databasePostgres.connection.hentSykmeldingMedId(update.sykmeldingId)
                    counter++
                    if (sykmeldingDb != null) {
                        // legg inn behandlingsutfall
                        databasePostgres.connection.opprettBehandlingsutfall(
                            Behandlingsutfall(
                                id = update.sykmeldingId,
                                behandlingsutfall = ValidationResult(Status.OK, emptyList())
                            )
                        )
                        counterIdUpdates++
                    }
                } catch (ex: Exception) {
                    log.error("Noe gikk galt med sykmeldingid {}", update.sykmeldingId, ex)
                    applicationState.ready = false
                    break
                }

                if (counter >= lastCounter + 1000) {
                    log.info(
                        "Behandlet {} sykmeldinger, opprettet behandlingsutfall for {} sykmeldinger",
                        counter,
                        counterIdUpdates
                    )
                    lastCounter = counter
                }
            }
        }
    }
}
