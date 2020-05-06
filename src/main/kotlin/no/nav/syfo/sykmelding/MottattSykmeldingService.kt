package no.nav.syfo.sykmelding

import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.persistering.db.postgres.getSykmeldingMedSisteStatus
import no.nav.syfo.sykmelding.kafka.model.MottattSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.toEnkelSykmelding
import no.nav.syfo.sykmelding.model.EnkelSykmeldingDbModel

class MottattSykmeldingService(
    private val applicationState: ApplicationState,
    private val databasePostgres: DatabasePostgres,
    private val mottattSykmeldingProudcer: MottattSykmeldingKafkaProducer,
    private val lastMottattDato: LocalDate
) {
    fun run() {
        var counter = 0
        var lastMottattDato = lastMottattDato
        val loggingJob = GlobalScope.launch {
            while (applicationState.ready) {
                log.info(
                    "Antall sykmeldinger som er sendt til mottatt topic: {}, lastMottattDato {}",
                    counter,
                    lastMottattDato
                )
                delay(30_000)
            }
        }
        while (lastMottattDato.isBefore(LocalDate.now().plusDays(1))) {
            val dbmodels = databasePostgres.connection.getSykmeldingMedSisteStatus(lastMottattDato)
            val mapped = dbmodels
                .map {
                    try {
                        mapTilMottattSykmelding(it)
                    } catch (ex: Exception) {
                        log.error(
                            "noe gikk galt med sykmelidng {}, på dato {}",
                            it.sykmeldingsDokument.id,
                            lastMottattDato,
                            ex
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
        runBlocking {
            loggingJob.cancelAndJoin()
        }
    }

    private fun mapTilMottattSykmelding(it: EnkelSykmeldingDbModel): MottattSykmeldingKafkaMessage {
        val sykmelding = it.toEnkelSykmelding()
        val metadata = KafkaMetadataDTO(
            sykmeldingId = it.id,
            fnr = it.fnr,
            source = "syfosmregister",
            timestamp = it.mottattTidspunkt.atOffset(ZoneOffset.UTC)
        )
        return MottattSykmeldingKafkaMessage(sykmelding = sykmelding, kafkaMetadata = metadata)
    }
}
