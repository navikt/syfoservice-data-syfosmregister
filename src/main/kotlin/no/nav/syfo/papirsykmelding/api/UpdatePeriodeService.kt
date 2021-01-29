package no.nav.syfo.papirsykmelding.api

import java.time.ZoneId
import java.time.ZoneOffset
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.aksessering.db.oracle.getSykmeldingsDokument
import no.nav.syfo.aksessering.db.oracle.updateDocument
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.db.DatabasePostgres
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
import no.nav.syfo.sykmelding.EnkelSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.MottattSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.model.MottattSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.toEnkelSykmelding
import no.nav.syfo.sykmelding.model.EnkelSykmeldingDbModel
import no.nav.syfo.sykmelding.model.Periode

class UpdatePeriodeService(
    private val databaseoracle: DatabaseOracle,
    private val databasePostgres: DatabasePostgres,
    private val sykmeldingEndringsloggKafkaProducer: SykmeldingEndringsloggKafkaProducer,
    private val mottattSykmeldingProudcer: MottattSykmeldingKafkaProducer,
    private val sendtSykmeldingProducer: EnkelSykmeldingKafkaProducer,
    private val bekreftetSykmeldingKafkaProducer: EnkelSykmeldingKafkaProducer
) {
    fun updatePeriode(sykmeldingId: String, periodeliste: List<Periode>) {
        val result = databaseoracle.getSykmeldingsDokument(sykmeldingId)
        val sykmeldingsdokument = databasePostgres.connection.hentSykmeldingsdokument(sykmeldingId)

        if (result.rows.isNotEmpty() && sykmeldingsdokument != null) {
            log.info("Oppdaterer sykmeldingsperioder for id {}", sykmeldingId)
            val document = result.rows.first()
            if (document != null) {
                log.info(
                    "Endrer perioder fra ${objectMapper.writeValueAsString(sykmeldingsdokument.sykmelding.perioder)}" +
                            " til ${objectMapper.writeValueAsString(periodeliste)} for id $sykmeldingId"
                )
                sykmeldingEndringsloggKafkaProducer.publishToKafka(sykmeldingsdokument)

                document.aktivitet.periode.clear()
                document.aktivitet.periode.addAll(periodeliste.map { tilSyfoservicePeriode(it) })

                databaseoracle.updateDocument(document, sykmeldingId)
                databasePostgres.updatePeriode(periodeliste, sykmeldingId)

                val sykmelding = databasePostgres.connection.getEnkelSykmelding(sykmeldingId)
                if (sykmelding == null) {
                    log.error("Fant ikke sykmeldingen vi nettopp endret..")
                    throw RuntimeException("Fant ikke sykmeldingen vi nettopp endret")
                }

                mottattSykmeldingProudcer.sendSykmelding(mapTilMottattSykmelding(sykmelding))

                if (sykmelding.status.statusEvent == "SENDT") {
                    log.info("Sykmelding er sendt")
                    val sendtSykmelding = databasePostgres.connection.getSendtSykmeldingMedSisteStatus(sykmeldingId).firstOrNull()
                    sendtSykmeldingProducer.sendSykmelding(mapTilSendtSykmelding(sendtSykmelding!!))
                    log.info("Sendt sykmelding til SENDT-topic")
                } else if (sykmelding.status.statusEvent == "BEKREFTET") {
                    log.info("Sykmelding er bekreftet")
                    val bekreftetSykmelding = databasePostgres.connection.getSykmeldingMedSisteStatusBekreftet(sykmeldingId)
                    val sporsmals = databasePostgres.connection.hentSporsmalOgSvar(sykmeldingId)
                    bekreftetSykmeldingKafkaProducer.sendSykmelding(mapTilBekreftetSykmelding(bekreftetSykmelding!!, sporsmals))
                    log.info("Sendt sykmelding til BEKREFTET-topic")
                }
            }
        } else {
            log.info("Fant ikke sykmelding med id {}", sykmeldingId)
            throw RuntimeException("Fant ikke sykmelding med id $sykmeldingId")
        }
    }

    private fun mapTilMottattSykmelding(enkelSykmeldingDbModel: EnkelSykmeldingDbModel): MottattSykmeldingKafkaMessage {
        return MottattSykmeldingKafkaMessage(
            sykmelding = enkelSykmeldingDbModel.toEnkelSykmelding(),
            kafkaMetadata = KafkaMetadataDTO(
                sykmeldingId = enkelSykmeldingDbModel.id,
                timestamp = enkelSykmeldingDbModel.mottattTidspunkt.atOffset(ZoneOffset.UTC),
                fnr = enkelSykmeldingDbModel.fnr,
                source = "macgyver"

            )
        )
    }

    private fun mapTilSendtSykmelding(enkelSykmeldingDbModel: EnkelSykmeldingDbModel): SykmeldingKafkaMessage {
        val sykmelding = enkelSykmeldingDbModel.toEnkelSykmelding()
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
        return SykmeldingKafkaMessage(
            sykmelding = sykmelding,
            kafkaMetadata = metadata,
            event = sykmeldingStatusKafkaEventDTO
        )
    }

    private fun mapTilBekreftetSykmelding(enkelSykmeldingDbModel: EnkelSykmeldingDbModel, sporsmals: List<Sporsmal>): SykmeldingKafkaMessage {
        val sykmelding = enkelSykmeldingDbModel.toEnkelSykmelding()
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

    private fun tilSyfoservicePeriode(periode: Periode): HelseOpplysningerArbeidsuforhet.Aktivitet.Periode {
        if (periode.aktivitetIkkeMulig != null) {
            return HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = periode.fom
                periodeTOMDato = periode.tom
                aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig().apply {
                    medisinskeArsaker = if (periode.aktivitetIkkeMulig.medisinskArsak != null) {
                        ArsakType().apply {
                            beskriv = periode.aktivitetIkkeMulig.medisinskArsak.beskrivelse
                            arsakskode.addAll(periode.aktivitetIkkeMulig.medisinskArsak.arsak.map {
                                CS().apply {
                                    v = it.codeValue
                                    dn = it.name
                                }
                            })
                        }
                    } else {
                        null
                    }
                    arbeidsplassen = if (periode.aktivitetIkkeMulig.arbeidsrelatertArsak != null) {
                        ArsakType().apply {
                            beskriv = periode.aktivitetIkkeMulig.arbeidsrelatertArsak.beskrivelse
                            arsakskode.addAll(periode.aktivitetIkkeMulig.arbeidsrelatertArsak.arsak.map {
                                CS().apply {
                                    v = it.codeValue
                                    dn = it.name
                                }
                            })
                        }
                    } else {
                        null
                    }
                }
                avventendeSykmelding = null
                gradertSykmelding = null
                behandlingsdager = null
                isReisetilskudd = null
            }
        }

        if (periode.gradert != null) {
            return HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = periode.fom
                periodeTOMDato = periode.tom
                aktivitetIkkeMulig = null
                avventendeSykmelding = null
                gradertSykmelding = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.GradertSykmelding().apply {
                    isReisetilskudd = periode.gradert.reisetilskudd
                    sykmeldingsgrad = Integer.valueOf(periode.gradert.grad)
                }
                behandlingsdager = null
                isReisetilskudd = null
            }
        }
        if (!periode.avventendeInnspillTilArbeidsgiver.isNullOrEmpty()) {
            return HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = periode.fom
                periodeTOMDato = periode.tom
                aktivitetIkkeMulig = null
                avventendeSykmelding = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AvventendeSykmelding().apply {
                    innspillTilArbeidsgiver = periode.avventendeInnspillTilArbeidsgiver
                }
                gradertSykmelding = null
                behandlingsdager = null
                isReisetilskudd = null
            }
        }
        if (periode.behandlingsdager != null) {
            return HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = periode.fom
                periodeTOMDato = periode.tom
                aktivitetIkkeMulig = null
                avventendeSykmelding = null
                gradertSykmelding = null
                behandlingsdager = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.Behandlingsdager().apply {
                    antallBehandlingsdagerUke = periode.behandlingsdager
                }
                isReisetilskudd = null
            }
        }
        if (periode.reisetilskudd) {
            return HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = periode.fom
                periodeTOMDato = periode.tom
                aktivitetIkkeMulig = null
                avventendeSykmelding = null
                gradertSykmelding = null
                behandlingsdager = null
                isReisetilskudd = true
            }
        }
        throw IllegalStateException("Har mottatt periode som ikke er av kjent type")
    }
}
