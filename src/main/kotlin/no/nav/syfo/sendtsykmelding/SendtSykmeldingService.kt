package no.nav.syfo.sendtsykmelding

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.persistering.db.postgres.getSykmeldingMedSisteStatus
import no.nav.syfo.sendtsykmelding.kafka.model.SendtSykmeldingKafkaMessage
import no.nav.syfo.sendtsykmelding.kafka.model.toSendtSykmelding

class SendtSykmeldingService(
    private val applicationState: ApplicationState,
    private val databasePostgres: DatabasePostgres,
    private val sendtSykmeldingProducer: SendtSykmeldingKafkaProducer,
    private val lastMottattDato: LocalDate
) {

    fun run() {
        var counterSendtSykmeldinger = 0
        var lastMottattDato = lastMottattDato
        val loggingJob = GlobalScope.launch {
            while (applicationState.ready) {
                log.info(
                    "Antall sykmeldinger som er sendt: {}, lastMottattDato {}",
                    counterSendtSykmeldinger,
                    lastMottattDato
                )
                delay(30_000)
            }
        }

        while (lastMottattDato.isBefore(LocalDate.now().plusDays(1))) {
            databasePostgres.connection.getSykmeldingMedSisteStatus(lastMottattDato).map {
                val sykmelding = it.toSendtSykmelding()
                val metadata = KafkaMetadataDTO(
                    sykmeldingId = it.id,
                    timestamp = it.status.statusTimestamp.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime(),
                    source = "syfoservice",
                    fnr = it.fnr
                )
                val sykmeldingStatusKafkaEventDTO = SykmeldingStatusKafkaEventDTO(
                    metadata.sykmeldingId,
                    metadata.timestamp,
                    StatusEventDTO.SENDT,
                    ArbeidsgiverStatusDTO(it.status.arbeidsgiver!!.orgnummer, it.status.arbeidsgiver!!.juridiskOrgnummer, it.status.arbeidsgiver!!.orgNavn),
                    listOf(SporsmalOgSvarDTO(
                        tekst = "Jeg er sykmeldt fra",
                        shortName = ShortNameDTO.ARBEIDSSITUASJON,
                        svartype = SvartypeDTO.ARBEIDSSITUASJON,
                        svar = "ARBEIDSTAKER"
                    ))
                )
                SendtSykmeldingKafkaMessage(
                    sykmelding = sykmelding,
                    kafkaMetadataDTO = metadata,
                    sendtEvent = sykmeldingStatusKafkaEventDTO
                )
            }.forEach {
                sendtSykmeldingProducer.sendSykmelding(it)
                counterSendtSykmeldinger++
            }
            lastMottattDato = lastMottattDato.plusDays(1)
        }
        log.info("Ferdig med alle sykmeldingene, totalt {}", counterSendtSykmeldinger)
        runBlocking {
            loggingJob.cancelAndJoin()
        }
    }
}