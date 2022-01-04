package no.nav.syfo.sykmelding.sykmelding.model

import no.nav.syfo.identendring.model.periodeErInnenforKoronaregler
import no.nav.syfo.model.ShortName
import no.nav.syfo.model.Sporsmal
import no.nav.syfo.model.Svar
import no.nav.syfo.model.Svartype
import no.nav.syfo.sykmelding.model.Adresse
import no.nav.syfo.sykmelding.model.AktivitetIkkeMulig
import no.nav.syfo.sykmelding.model.AnnenFraverGrunn
import no.nav.syfo.sykmelding.model.AnnenFraversArsak
import no.nav.syfo.sykmelding.model.Arbeidsgiver
import no.nav.syfo.sykmelding.model.ArbeidsgiverDbModel
import no.nav.syfo.sykmelding.model.ArbeidsrelatertArsak
import no.nav.syfo.sykmelding.model.ArbeidsrelatertArsakType
import no.nav.syfo.sykmelding.model.Behandler
import no.nav.syfo.sykmelding.model.ErIArbeid
import no.nav.syfo.sykmelding.model.ErIkkeIArbeid
import no.nav.syfo.sykmelding.model.Gradert
import no.nav.syfo.sykmelding.model.KontaktMedPasient
import no.nav.syfo.sykmelding.model.MedisinskArsak
import no.nav.syfo.sykmelding.model.MedisinskArsakType
import no.nav.syfo.sykmelding.model.MedisinskVurdering
import no.nav.syfo.sykmelding.model.MeldingTilNAV
import no.nav.syfo.sykmelding.model.Periode
import no.nav.syfo.sykmelding.model.Periodetype
import no.nav.syfo.sykmelding.model.Prognose
import no.nav.syfo.sykmelding.model.SporsmalSvar
import no.nav.syfo.sykmelding.model.StatusDbModel
import no.nav.syfo.sykmelding.model.SvarRestriksjon
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun MedisinskVurdering.getHarRedusertArbeidsgiverperiode(sykmeldingsperioder: List<Periode>): Boolean {
    val sykmeldingsperioderInnenforKoronaregler = sykmeldingsperioder.filter { periodeErInnenforKoronaregler(it.fom, it.tom) }
    if (sykmeldingsperioderInnenforKoronaregler.isEmpty()) {
        return false
    }
    val diagnoserSomGirRedusertArbgiverPeriode = listOf("R991", "U071", "U072", "A23", "R992")
    if (hovedDiagnose != null && diagnoserSomGirRedusertArbgiverPeriode.contains(hovedDiagnose.kode)) {
        return true
    } else if (!biDiagnoser.isNullOrEmpty() && biDiagnoser.find { diagnoserSomGirRedusertArbgiverPeriode.contains(it.kode) } != null) {
        return true
    }
    return checkSmittefare()
}

private fun MedisinskVurdering.checkSmittefare() =
    annenFraversArsak?.grunn?.any { annenFraverGrunn -> annenFraverGrunn == AnnenFraverGrunn.SMITTEFARE } == true

fun Sporsmal.toSporsmalDTO(): SporsmalDTO {
    return SporsmalDTO(
        tekst = tekst,
        svar = svar.toDTO(),
        shortName = shortName.toDTO()
    )
}

private fun ShortName.toDTO(): ShortNameDTO {
    return when (this) {
        ShortName.ARBEIDSSITUASJON -> ShortNameDTO.ARBEIDSSITUASJON
        ShortName.FORSIKRING -> ShortNameDTO.FORSIKRING
        ShortName.FRAVAER -> ShortNameDTO.FRAVAER
        ShortName.PERIODE -> ShortNameDTO.PERIODE
        ShortName.NY_NARMESTE_LEDER -> ShortNameDTO.NY_NARMESTE_LEDER
    }
}

private fun Svar.toDTO(): SvarDTO {
    return SvarDTO(
        svar = svar,
        svarType = svartype.toDTO()
    )
}

private fun Svartype.toDTO(): SvartypeDTO {
    return when (this) {
        Svartype.ARBEIDSSITUASJON -> SvartypeDTO.ARBEIDSSITUASJON
        Svartype.JA_NEI -> SvartypeDTO.JA_NEI
        Svartype.PERIODER -> SvartypeDTO.PERIODER
    }
}

fun getUtcTime(tidspunkt: LocalDateTime): OffsetDateTime {
    return tidspunkt.atOffset(ZoneOffset.UTC)
}

fun StatusDbModel.toSykmeldingStatusDTO(sporsmal: List<SporsmalDTO>): SykmeldingStatusDTO {
    return SykmeldingStatusDTO(statusEvent, getUtcTime(statusTimestamp), arbeidsgiver?.toArbeidsgiverStatusDTO(), sporsmal)
}

private fun ArbeidsgiverDbModel.toArbeidsgiverStatusDTO(): ArbeidsgiverStatusDTO {
    return ArbeidsgiverStatusDTO(orgnummer, juridiskOrgnummer, orgNavn)
}

fun toUtdypendeOpplysninger(utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>>): Map<String, Map<String, SporsmalSvarDTO>> {
    return utdypendeOpplysninger.mapValues {
        it.value.mapValues { entry -> entry.value.toSporsmalSvarDTO() }
            .filterValues { sporsmalSvar -> !sporsmalSvar.restriksjoner.contains(SvarRestriksjonDTO.SKJERMET_FOR_NAV) }
    }
}

fun SporsmalSvar.toSporsmalSvarDTO(): SporsmalSvarDTO {
    return SporsmalSvarDTO(
        sporsmal = sporsmal,
        svar = svar,
        restriksjoner = restriksjoner.map { it.toSvarRestriksjonDTO() }

    )
}

private fun SvarRestriksjon.toSvarRestriksjonDTO(): SvarRestriksjonDTO {
    return when (this) {
        SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER -> SvarRestriksjonDTO.SKJERMET_FOR_ARBEIDSGIVER
        SvarRestriksjon.SKJERMET_FOR_NAV -> SvarRestriksjonDTO.SKJERMET_FOR_NAV
        SvarRestriksjon.SKJERMET_FOR_PASIENT -> SvarRestriksjonDTO.SKJERMET_FOR_PASIENT
    }
}

fun Prognose.toPrognoseDTO(): PrognoseDTO {
    return PrognoseDTO(
        arbeidsforEtterPeriode = arbeidsforEtterPeriode,
        erIArbeid = erIArbeid?.toErIArbeidDTO(),
        erIkkeIArbeid = erIkkeIArbeid?.toErIkkeIArbeidDTO(),
        hensynArbeidsplassen = hensynArbeidsplassen
    )
}

private fun ErIkkeIArbeid.toErIkkeIArbeidDTO(): ErIkkeIArbeidDTO {
    return ErIkkeIArbeidDTO(
        arbeidsforPaSikt = arbeidsforPaSikt,
        arbeidsforFOM = arbeidsforFOM,
        vurderingsdato = vurderingsdato
    )
}

private fun ErIArbeid.toErIArbeidDTO(): ErIArbeidDTO {
    return ErIArbeidDTO(
        egetArbeidPaSikt = egetArbeidPaSikt,
        annetArbeidPaSikt = annetArbeidPaSikt,
        arbeidFOM = arbeidFOM,
        vurderingsdato = vurderingsdato
    )
}

private fun MeldingTilNAV.toMeldingTilNavDTO(): MeldingTilNavDTO? {
    return MeldingTilNavDTO(
        bistandUmiddelbart = bistandUmiddelbart,
        beskrivBistand = beskrivBistand
    )
}

fun KontaktMedPasient.toKontaktMedPasientDTO(): KontaktMedPasientDTO {
    return KontaktMedPasientDTO(
        kontaktDato = kontaktDato,
        begrunnelseIkkeKontakt = begrunnelseIkkeKontakt
    )
}

fun Arbeidsgiver.toArbeidsgiverDTO(): ArbeidsgiverDTO {
    return ArbeidsgiverDTO(navn, stillingsprosent)
}

fun Periode.toSykmeldingsperiodeDTO(): SykmeldingsperiodeDTO {
    return SykmeldingsperiodeDTO(
        fom = fom,
        tom = tom,
        behandlingsdager = behandlingsdager,
        gradert = gradert?.toGradertDTO(),
        innspillTilArbeidsgiver = avventendeInnspillTilArbeidsgiver,
        type = finnPeriodetype(this).toDTO(),
        aktivitetIkkeMulig = aktivitetIkkeMulig?.toDto(),
        reisetilskudd = reisetilskudd
    )
}

private fun AktivitetIkkeMulig.toDto(): AktivitetIkkeMuligDTO {
    return AktivitetIkkeMuligDTO(
        medisinskArsak = medisinskArsak?.toMedisinskArsakDto(),
        arbeidsrelatertArsak = arbeidsrelatertArsak?.toArbeidsrelatertArsakDto()
    )
}

private fun ArbeidsrelatertArsak.toArbeidsrelatertArsakDto(): ArbeidsrelatertArsakDTO {
    return ArbeidsrelatertArsakDTO(
        beskrivelse = beskrivelse,
        arsak = arsak.map { toArbeidsrelatertArsakTypeDto(it) }
    )
}

fun toArbeidsrelatertArsakTypeDto(arbeidsrelatertArsakType: ArbeidsrelatertArsakType): ArbeidsrelatertArsakTypeDTO {
    return when (arbeidsrelatertArsakType) {
        ArbeidsrelatertArsakType.MANGLENDE_TILRETTELEGGING -> ArbeidsrelatertArsakTypeDTO.MANGLENDE_TILRETTELEGGING
        ArbeidsrelatertArsakType.ANNET -> ArbeidsrelatertArsakTypeDTO.ANNET
    }
}

private fun MedisinskArsak.toMedisinskArsakDto(): MedisinskArsakDTO {
    return MedisinskArsakDTO(
        beskrivelse = beskrivelse,
        arsak = arsak.map { toMedisinskArsakTypeDto(it) }
    )
}

fun toMedisinskArsakTypeDto(medisinskArsakType: MedisinskArsakType): MedisinskArsakTypeDTO {
    return when (medisinskArsakType) {
        MedisinskArsakType.AKTIVITET_FORHINDRER_BEDRING -> MedisinskArsakTypeDTO.AKTIVITET_FORHINDRER_BEDRING
        MedisinskArsakType.AKTIVITET_FORVERRER_TILSTAND -> MedisinskArsakTypeDTO.AKTIVITET_FORVERRER_TILSTAND
        MedisinskArsakType.TILSTAND_HINDRER_AKTIVITET -> MedisinskArsakTypeDTO.TILSTAND_HINDRER_AKTIVITET
        MedisinskArsakType.ANNET -> MedisinskArsakTypeDTO.ANNET
    }
}

private fun Gradert.toGradertDTO(): GradertDTO {
    return GradertDTO(grad, reisetilskudd)
}

private fun AnnenFraversArsak.toDTO(): AnnenFraversArsakDTO {
    return AnnenFraversArsakDTO(
        beskrivelse = beskrivelse,
        grunn = grunn.map { it.toDTO() }
    )
}

private fun AnnenFraverGrunn.toDTO(): AnnenFraverGrunnDTO {
    return when (this) {
        AnnenFraverGrunn.ABORT -> AnnenFraverGrunnDTO.ABORT
        AnnenFraverGrunn.ARBEIDSRETTET_TILTAK -> AnnenFraverGrunnDTO.ARBEIDSRETTET_TILTAK
        AnnenFraverGrunn.BEHANDLING_FORHINDRER_ARBEID -> AnnenFraverGrunnDTO.BEHANDLING_FORHINDRER_ARBEID
        AnnenFraverGrunn.BEHANDLING_STERILISERING -> AnnenFraverGrunnDTO.BEHANDLING_STERILISERING
        AnnenFraverGrunn.DONOR -> AnnenFraverGrunnDTO.DONOR
        AnnenFraverGrunn.GODKJENT_HELSEINSTITUSJON -> AnnenFraverGrunnDTO.GODKJENT_HELSEINSTITUSJON
        AnnenFraverGrunn.MOTTAR_TILSKUDD_GRUNNET_HELSETILSTAND -> AnnenFraverGrunnDTO.MOTTAR_TILSKUDD_GRUNNET_HELSETILSTAND
        AnnenFraverGrunn.NODVENDIG_KONTROLLUNDENRSOKELSE -> AnnenFraverGrunnDTO.NODVENDIG_KONTROLLUNDENRSOKELSE
        AnnenFraverGrunn.SMITTEFARE -> AnnenFraverGrunnDTO.SMITTEFARE
        AnnenFraverGrunn.UFOR_GRUNNET_BARNLOSHET -> AnnenFraverGrunnDTO.UFOR_GRUNNET_BARNLOSHET
    }
}

fun Behandler.toBehandlerDTO(): BehandlerDTO {
    return BehandlerDTO(
        fornavn = fornavn,
        mellomnavn = mellomnavn,
        etternavn = etternavn,
        aktoerId = aktoerId,
        fnr = fnr,
        her = her,
        hpr = hpr,
        tlf = tlf,
        adresse = adresse.toAdresseDTO()
    )
}

private fun Adresse.toAdresseDTO(): AdresseDTO {
    return AdresseDTO(
        gate = gate,
        kommune = kommune,
        land = land,
        postboks = postboks,
        postnummer = postnummer
    )
}

fun finnPeriodetype(periode: Periode): Periodetype =
    when {
        periode.aktivitetIkkeMulig != null -> Periodetype.AKTIVITET_IKKE_MULIG
        periode.avventendeInnspillTilArbeidsgiver != null -> Periodetype.AVVENTENDE
        periode.behandlingsdager != null -> Periodetype.BEHANDLINGSDAGER
        periode.gradert != null -> Periodetype.GRADERT
        periode.reisetilskudd -> Periodetype.REISETILSKUDD
        else -> throw RuntimeException("Kunne ikke bestemme typen til periode: $periode")
    }

fun Periodetype.toDTO(): PeriodetypeDTO =
    PeriodetypeDTO.valueOf(this.name)
