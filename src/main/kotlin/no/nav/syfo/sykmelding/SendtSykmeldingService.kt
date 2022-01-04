package no.nav.syfo.sykmelding

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
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.persistering.db.postgres.getSendtSykmeldingMedSisteStatus
import no.nav.syfo.persistering.db.postgres.getSykmeldingMedSisteStatus
import no.nav.syfo.sykmelding.aivenmigrering.SykmeldingV2KafkaMessage
import no.nav.syfo.sykmelding.aivenmigrering.SykmeldingV2KafkaProducer
import no.nav.syfo.sykmelding.kafka.model.toArbeidsgiverSykmelding
import no.nav.syfo.sykmelding.model.EnkelSykmeldingDbModel

class SendtSykmeldingService(
    private val applicationState: ApplicationState,
    private val databasePostgres: DatabasePostgres,
    private val sendtSykmeldingProducer: SykmeldingV2KafkaProducer,
    private val lastMottattDato: LocalDate,
    private val sendtSykmeldingTopic: String
) {

    fun republishSendtSykmelding() {
        val sykmeldingsid = ""
        val dbmodels = databasePostgres.connection.getSendtSykmeldingMedSisteStatus(sykmeldingsid)
        if (dbmodels.size > 1) {
            log.error("Fant flere sykmeldinger")
            throw RuntimeException("Fant mer enn en sykmelding")
        }
        try {
            val mapped = mapSykmelding(dbmodels.first())
            sendtSykmeldingProducer.sendSykmelding(
                sykmeldingKafkaMessage = mapped,
                sykmeldingId = mapped.kafkaMetadata.sykmeldingId,
                topic = sendtSykmeldingTopic
            )
            log.info("Resendt sykmelding til topic")
        } catch (ex: Exception) {
            log.error("noe gikk galt med sykmelding", ex)
            throw ex
        }
    }

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
            val dbmodels = databasePostgres.connection.getSykmeldingMedSisteStatus(lastMottattDato)
            val mapped = dbmodels
                .map {
                    try {
                        mapSykmelding(it)
                    } catch (ex: Exception) {
                        log.error("noe gikk galt med sykmelidng {}, på dato {}", it.sykmeldingsDokument.id, lastMottattDato, ex)
                        throw ex
                    }
                }.forEach {
                    sendtSykmeldingProducer.sendSykmelding(
                        sykmeldingKafkaMessage = it,
                        sykmeldingId = it.kafkaMetadata.sykmeldingId,
                        topic = sendtSykmeldingTopic
                    )
                    counterSendtSykmeldinger++
                }
            lastMottattDato = lastMottattDato.plusDays(1)
        }

        log.info("Ferdig med alle sykmeldingene, totalt {}, siste dato {}", counterSendtSykmeldinger, lastMottattDato)
        runBlocking {
            loggingJob.cancelAndJoin()
        }
    }

    private fun mapSykmelding(it: EnkelSykmeldingDbModel): SykmeldingV2KafkaMessage {
        val sykmelding = it.toArbeidsgiverSykmelding()
        val metadata = KafkaMetadataDTO(
            sykmeldingId = it.id,
            timestamp = it.status.statusTimestamp.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime(),
            source = "syfoservice",
            fnr = it.fnr
        )
        val sykmeldingStatusKafkaEventDTO = SykmeldingStatusKafkaEventDTO(
            metadata.sykmeldingId,
            metadata.timestamp,
            STATUS_SENDT,
            ArbeidsgiverStatusDTO(
                it.status.arbeidsgiver!!.orgnummer,
                it.status.arbeidsgiver!!.juridiskOrgnummer,
                it.status.arbeidsgiver!!.orgNavn
            ),
            listOf(
                SporsmalOgSvarDTO(
                    tekst = "Jeg er sykmeldt fra",
                    shortName = ShortNameDTO.ARBEIDSSITUASJON,
                    svartype = SvartypeDTO.ARBEIDSSITUASJON,
                    svar = "ARBEIDSTAKER"
                )
            )
        )
        return SykmeldingV2KafkaMessage(
            sykmelding = sykmelding,
            kafkaMetadata = metadata,
            event = sykmeldingStatusKafkaEventDTO
        )
    }
}
