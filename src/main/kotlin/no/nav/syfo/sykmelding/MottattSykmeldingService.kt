package no.nav.syfo.sykmelding

import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.model.Status
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.persistering.db.postgres.getMottattSykmelding
import no.nav.syfo.sykmelding.kafka.model.MottattSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.toEnkelSykmelding
import no.nav.syfo.sykmelding.model.MottattSykmeldingDbModel

class MottattSykmeldingService(
    private val applicationState: ApplicationState,
    private val databasePostgres: DatabasePostgres,
    private val mottattSykmeldingProudcer: MottattSykmeldingKafkaProducer,
    private val lastMottattDato: LocalDate
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
                        mapTilMottattSykmelding(it)
                    } catch (ex: Exception) {
                        log.error(
                            "noe gikk galt med sykmelidng {}, på dato {}",
                            it.sykmeldingsDokument.id,
                            lastMottattDato
                        )
                        throw ex
                    }
                }.forEach {
                    mottattSykmeldingProudcer.sendSykmelding(it)
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

    private fun mapTilMottattSykmelding(mottattSykmeldingDbModel: MottattSykmeldingDbModel): MottattSykmeldingKafkaMessage {
        return MottattSykmeldingKafkaMessage(
            sykmelding = mottattSykmeldingDbModel.toEnkelSykmelding(),
            kafkaMetadata = KafkaMetadataDTO(
                sykmeldingId = mottattSykmeldingDbModel.id,
                timestamp = mottattSykmeldingDbModel.mottattTidspunkt.atOffset(ZoneOffset.UTC),
                fnr = mottattSykmeldingDbModel.fnr,
                source = "syfosmregister"

            )
        )
    }
}
