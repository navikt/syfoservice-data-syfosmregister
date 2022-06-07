package no.nav.syfo.papirsykmelding.api

import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.kafka.SykmeldingEndringsloggKafkaProducer
import no.nav.syfo.log
import no.nav.syfo.model.ShortName
import no.nav.syfo.model.Sporsmal
import no.nav.syfo.model.Svartype
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.getEnkelSykmelding
import no.nav.syfo.persistering.db.postgres.getSendtSykmeldingMedSisteStatus
import no.nav.syfo.persistering.db.postgres.getSykmeldingMedSisteStatusBekreftet
import no.nav.syfo.persistering.db.postgres.hentSporsmalOgSvar
import no.nav.syfo.persistering.db.postgres.hentSykmeldingsdokument
import no.nav.syfo.persistering.db.postgres.updatePeriode
import no.nav.syfo.sykmelding.aivenmigrering.SykmeldingV2KafkaMessage
import no.nav.syfo.sykmelding.aivenmigrering.SykmeldingV2KafkaProducer
import no.nav.syfo.sykmelding.kafka.model.toArbeidsgiverSykmelding
import no.nav.syfo.sykmelding.model.EnkelSykmeldingDbModel
import no.nav.syfo.sykmelding.model.Periode
import java.time.ZoneId
import java.time.ZoneOffset

class UpdatePeriodeService(
    private val databasePostgres: DatabasePostgres,
    private val sykmeldingEndringsloggKafkaProducer: SykmeldingEndringsloggKafkaProducer,
    private val sykmeldingProducer: SykmeldingV2KafkaProducer,
    private val mottattSykmeldingTopic: String,
    private val sendtSykmeldingTopic: String,
    private val bekreftetSykmeldingTopic: String
) {
    fun updatePeriode(sykmeldingId: String, periodeliste: List<Periode>) {
        val sykmeldingsdokument = databasePostgres.connection.hentSykmeldingsdokument(sykmeldingId)

        if (sykmeldingsdokument != null) {
            log.info(
                "Endrer perioder fra ${objectMapper.writeValueAsString(sykmeldingsdokument.sykmelding.perioder)}" +
                    " til ${objectMapper.writeValueAsString(periodeliste)} for id $sykmeldingId"
            )
            sykmeldingEndringsloggKafkaProducer.publishToKafka(sykmeldingsdokument)

            databasePostgres.updatePeriode(periodeliste, sykmeldingId)

            val sykmelding = databasePostgres.connection.getEnkelSykmelding(sykmeldingId)
            if (sykmelding == null) {
                log.error("Fant ikke sykmeldingen vi nettopp endret..")
                throw RuntimeException("Fant ikke sykmeldingen vi nettopp endret")
            }

            sykmeldingProducer.sendSykmelding(
                sykmeldingKafkaMessage = mapTilMottattSykmelding(sykmelding),
                sykmeldingId = sykmeldingId,
                topic = mottattSykmeldingTopic
            )

            if (sykmelding.status.statusEvent == "SENDT") {
                log.info("Sykmelding er sendt")
                val sendtSykmelding = databasePostgres.connection.getSendtSykmeldingMedSisteStatus(sykmeldingId).firstOrNull()
                sykmeldingProducer.sendSykmelding(
                    sykmeldingKafkaMessage = mapTilSendtSykmelding(sendtSykmelding!!),
                    sykmeldingId = sykmeldingId,
                    topic = sendtSykmeldingTopic
                )
                log.info("Sendt sykmelding til SENDT-topic")
            } else if (sykmelding.status.statusEvent == "BEKREFTET") {
                log.info("Sykmelding er bekreftet")
                val bekreftetSykmelding = databasePostgres.connection.getSykmeldingMedSisteStatusBekreftet(sykmeldingId)
                val sporsmals = databasePostgres.connection.hentSporsmalOgSvar(sykmeldingId)
                sykmeldingProducer.sendSykmelding(
                    sykmeldingKafkaMessage = mapTilBekreftetSykmelding(bekreftetSykmelding!!, sporsmals),
                    sykmeldingId = sykmeldingId,
                    topic = bekreftetSykmeldingTopic
                )
                log.info("Sendt sykmelding til BEKREFTET-topic")
            }
        } else {
            log.info("Fant ikke sykmelding med id {}", sykmeldingId)
            throw RuntimeException("Fant ikke sykmelding med id $sykmeldingId")
        }
    }

    private fun mapTilMottattSykmelding(enkelSykmeldingDbModel: EnkelSykmeldingDbModel): SykmeldingV2KafkaMessage {
        return SykmeldingV2KafkaMessage(
            sykmelding = enkelSykmeldingDbModel.toArbeidsgiverSykmelding(),
            kafkaMetadata = KafkaMetadataDTO(
                sykmeldingId = enkelSykmeldingDbModel.id,
                timestamp = enkelSykmeldingDbModel.mottattTidspunkt.atOffset(ZoneOffset.UTC),
                fnr = enkelSykmeldingDbModel.fnr,
                source = "macgyver"
            ),
            event = null
        )
    }

    private fun mapTilSendtSykmelding(enkelSykmeldingDbModel: EnkelSykmeldingDbModel): SykmeldingV2KafkaMessage {
        val sykmelding = enkelSykmeldingDbModel.toArbeidsgiverSykmelding()
        val metadata = KafkaMetadataDTO(
            sykmeldingId = enkelSykmeldingDbModel.id,
            timestamp = enkelSykmeldingDbModel.status.statusTimestamp.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime(),
            source = "macgyver",
            fnr = enkelSykmeldingDbModel.fnr
        )
        val sykmeldingStatusKafkaEventDTO = SykmeldingStatusKafkaEventDTO(
            metadata.sykmeldingId,
            metadata.timestamp,
            STATUS_SENDT,
            ArbeidsgiverStatusDTO(
                enkelSykmeldingDbModel.status.arbeidsgiver!!.orgnummer,
                enkelSykmeldingDbModel.status.arbeidsgiver.juridiskOrgnummer,
                enkelSykmeldingDbModel.status.arbeidsgiver.orgNavn
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

    private fun mapTilBekreftetSykmelding(enkelSykmeldingDbModel: EnkelSykmeldingDbModel, sporsmals: List<Sporsmal>): SykmeldingV2KafkaMessage {
        val sykmelding = enkelSykmeldingDbModel.toArbeidsgiverSykmelding()
        val sporsmalDto = sporsmals.map {
            SporsmalOgSvarDTO(
                it.tekst,
                mapToShortName(it.shortName),
                mapToSvarType(it.svar.svartype),
                it.svar.svar
            )
        }
        val metadata = KafkaMetadataDTO(
            sykmeldingId = enkelSykmeldingDbModel.id,
            timestamp = enkelSykmeldingDbModel.status.statusTimestamp.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC)
                .toOffsetDateTime(),
            source = "macgyver",
            fnr = enkelSykmeldingDbModel.fnr
        )
        val sykmeldingStatusKafkaEventDTO = SykmeldingStatusKafkaEventDTO(
            metadata.sykmeldingId,
            metadata.timestamp,
            STATUS_BEKREFTET,
            null,
            sporsmalDto
        )
        return SykmeldingV2KafkaMessage(
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
