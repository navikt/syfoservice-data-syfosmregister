package no.nav.syfo.sykmelding

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.model.Status
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.persistering.db.postgres.getMottattSykmelding
import no.nav.syfo.sykmelding.aivenmigrering.SykmeldingV2KafkaMessage
import no.nav.syfo.sykmelding.aivenmigrering.SykmeldingV2KafkaProducer
import no.nav.syfo.sykmelding.kafka.model.toArbeidsgiverSykmelding
import no.nav.syfo.sykmelding.model.MottattSykmeldingDbModel
import java.time.LocalDate
import java.time.ZoneOffset

class MottattSykmeldingService(
    private val applicationState: ApplicationState,
    private val databasePostgres: DatabasePostgres,
    private val mottattSykmeldingProudcer: SykmeldingV2KafkaProducer,
    private val lastMottattDato: LocalDate,
    private val mottattSykmeldingTopic: String
) {
    suspend fun run() {
        var counter = 0
        var lastMottattDato = lastMottattDato
        val loggingJob = GlobalScope.launch {
            while (applicationState.ready) {
                log.info(
                    "Antall sykmeldinger som er sendt til mottatt topic: {}, lastMottattDato {}",
                    counter,
                    lastMottattDato
                )
                delay(60_000)
            }
        }
        while (lastMottattDato.isBefore(LocalDate.now().plusDays(1))) {
            val dbmodels = databasePostgres.connection.getMottattSykmelding(lastMottattDato)
            val mapped = dbmodels
                .filter {
                    it.behandlingsutfall.status != Status.INVALID
                }
                .map {
                    try {
                        mapSykmelding(it)
                    } catch (ex: Exception) {
                        log.error(
                            "noe gikk galt med sykmelidng {}, på dato {}",
                            it.sykmeldingsDokument.id,
                            lastMottattDato
                        )
                        throw ex
                    }
                }.forEach {
                    mottattSykmeldingProudcer.sendSykmelding(
                        sykmeldingKafkaMessage = it,
                        sykmeldingId = it.kafkaMetadata.sykmeldingId,
                        topic = mottattSykmeldingTopic
                    )
                    counter++
                }
            lastMottattDato = lastMottattDato.plusDays(1)
        }

        log.info(
            "Ferdig med alle sykmeldingene, totalt {}, siste dato {}",
            counter,
            lastMottattDato
        )
    }

    private fun mapSykmelding(mottattSykmeldingDbModel: MottattSykmeldingDbModel): SykmeldingV2KafkaMessage {
        return SykmeldingV2KafkaMessage(
            sykmelding = mottattSykmeldingDbModel.toArbeidsgiverSykmelding(),
            kafkaMetadata = KafkaMetadataDTO(
                sykmeldingId = mottattSykmeldingDbModel.id,
                timestamp = mottattSykmeldingDbModel.mottattTidspunkt.atOffset(ZoneOffset.UTC),
                fnr = mottattSykmeldingDbModel.fnr,
                source = "syfosmregister"
            ),
            event = null
        )
    }
}
