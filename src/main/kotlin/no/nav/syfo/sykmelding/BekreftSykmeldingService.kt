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
import no.nav.syfo.model.ShortName
import no.nav.syfo.model.Sporsmal
import no.nav.syfo.model.Svartype
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.persistering.db.postgres.getSykmeldingMedSisteStatusBekreftet
import no.nav.syfo.persistering.db.postgres.hentSporsmalOgSvar
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.toEnkelSykmelding
import no.nav.syfo.sykmelding.model.EnkelSykmeldingDbModel

class BekreftSykmeldingService(
    private val applicationState: ApplicationState,
    private val databasePostgres: DatabasePostgres,
    private val enkelSykmeldingKafkaProducer: EnkelSykmeldingKafkaProducer,
    private val lastMottattDato: LocalDate
) {

    fun run() {
        var counterBekreftetSykmeldinger = 0
        var lastMottattDato = lastMottattDato
        val loggingJob = GlobalScope.launch {
            while (applicationState.ready) {
                log.info(
                    "Antall sykmeldinger som er sendt: {}, lastMottattDato {}",
                    counterBekreftetSykmeldinger,
                    lastMottattDato
                )
                delay(30_000)
            }
        }
        while (lastMottattDato.isBefore(LocalDate.now().plusDays(1))) {
            val dbmodels = databasePostgres.connection.getSykmeldingMedSisteStatusBekreftet(lastMottattDato)
            val mapped = dbmodels
                .map {
                    try {
                        val sporsmals = databasePostgres.connection.hentSporsmalOgSvar(it.id)
                        mapTilSykmelding(it, sporsmals)
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
                    enkelSykmeldingKafkaProducer.sendSykmelding(it)
                    counterBekreftetSykmeldinger++
                }
            lastMottattDato = lastMottattDato.plusDays(1)
        }

        log.info(
            "Ferdig med alle sykmeldingene, totalt {}, siste dato {}",
            counterBekreftetSykmeldinger,
            lastMottattDato
        )
        runBlocking {
            loggingJob.cancelAndJoin()
        }
    }

    private fun mapTilSykmelding(
        it: EnkelSykmeldingDbModel,
        sporsmals: List<Sporsmal>
    ): SykmeldingKafkaMessage {
        val sykmelding = it.toEnkelSykmelding()
        val sporsmalDto = sporsmals.map {
            SporsmalOgSvarDTO(
                it.tekst,
                mapToShortName(it.shortName),
                mapToSvarType(it.svar.svartype),
                it.svar.svar
            )
        }
        val metadata = KafkaMetadataDTO(
            sykmeldingId = it.id,
            timestamp = it.status.statusTimestamp.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC)
                .toOffsetDateTime(),
            source = "syfoservice",
            fnr = it.fnr
        )
        val sykmeldingStatusKafkaEventDTO = SykmeldingStatusKafkaEventDTO(
            metadata.sykmeldingId,
            metadata.timestamp,
            STATUS_BEKREFTET,
            null,
            sporsmalDto
        )
        return SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            kafkaMetadata = metadata,
            event = sykmeldingStatusKafkaEventDTO
        )
    }

    private fun mapToSvarType(svartype: Svartype): SvartypeDTO {
        return when (svartype) {
            Svartype.ARBEIDSSITUASJON -> SvartypeDTO.ARBEIDSSITUASJON
            Svartype.PERIODER -> SvartypeDTO.PERIODER
            Svartype.JA_NEI -> SvartypeDTO.JA_NEI
        }
    }

    private fun mapToShortName(shortName: ShortName): ShortNameDTO {
        return when (shortName) {
            ShortName.ARBEIDSSITUASJON -> ShortNameDTO.ARBEIDSSITUASJON
            ShortName.NY_NARMESTE_LEDER -> ShortNameDTO.NY_NARMESTE_LEDER
            ShortName.FRAVAER -> ShortNameDTO.FRAVAER
            ShortName.PERIODE -> ShortNameDTO.PERIODE
            ShortName.FORSIKRING -> ShortNameDTO.FORSIKRING
        }
    }
}
