package no.nav.syfo.identendring.model

import no.nav.syfo.identendring.db.Adresse
import no.nav.syfo.identendring.db.AktivitetIkkeMulig
import no.nav.syfo.identendring.db.Arbeidsgiver
import no.nav.syfo.identendring.db.ArbeidsrelatertArsak
import no.nav.syfo.identendring.db.ArbeidsrelatertArsakType
import no.nav.syfo.identendring.db.Behandler
import no.nav.syfo.identendring.db.ErIArbeid
import no.nav.syfo.identendring.db.ErIkkeIArbeid
import no.nav.syfo.identendring.db.Gradert
import no.nav.syfo.identendring.db.KontaktMedPasient
import no.nav.syfo.identendring.db.MedisinskArsak
import no.nav.syfo.identendring.db.MedisinskArsakType
import no.nav.syfo.identendring.db.Periode
import no.nav.syfo.identendring.db.Periodetype
import no.nav.syfo.identendring.db.Prognose
import no.nav.syfo.identendring.db.SykmeldingDbModelUtenBehandlingsutfall
import no.nav.syfo.sykmelding.sykmelding.model.getUtcTime

fun SykmeldingDbModelUtenBehandlingsutfall.toEnkelSykmelding(): EnkelSykmelding {
    return EnkelSykmelding(
        id = id,
        mottattTidspunkt = mottattTidspunkt,
        legekontorOrgnummer = legekontorOrgNr,
        behandletTidspunkt = getUtcTime(sykmeldingsDokument.behandletTidspunkt),
        meldingTilArbeidsgiver = sykmeldingsDokument.meldingTilArbeidsgiver,
        navnFastlege = sykmeldingsDokument.navnFastlege,
        tiltakArbeidsplassen = sykmeldingsDokument.tiltakArbeidsplassen,
        syketilfelleStartDato = sykmeldingsDokument.syketilfelleStartDato,
        behandler = sykmeldingsDokument.behandler.toBehandlerDTO(),
        sykmeldingsperioder = sykmeldingsDokument.perioder.map { it.toSykmeldingsperiodeDTO(id) },
        arbeidsgiver = sykmeldingsDokument.arbeidsgiver.toArbeidsgiverDTO(),
        kontaktMedPasient = sykmeldingsDokument.kontaktMedPasient.toKontaktMedPasientDTO(),
        prognose = sykmeldingsDokument.prognose?.toPrognoseDTO(),
        egenmeldt = sykmeldingsDokument.avsenderSystem.navn == "Egenmeldt",
        papirsykmelding = sykmeldingsDokument.avsenderSystem.navn == "Papirsykmelding",
        harRedusertArbeidsgiverperiode = sykmeldingsDokument.medisinskVurdering.getHarRedusertArbeidsgiverperiode(sykmeldingsDokument.perioder),
        merknader = merknader?.map { MerknadDTO(type = it.type, beskrivelse = it.beskrivelse) }
    )
}

fun Behandler.toBehandlerDTO(fullBehandler: Boolean = true): BehandlerDTO {
    return BehandlerDTO(
        fornavn = fornavn,
        mellomnavn = mellomnavn,
        etternavn = etternavn,
        aktoerId = if (fullBehandler) { aktoerId } else null,
        fnr = if (fullBehandler) { fnr } else null,
        her = if (fullBehandler) { her } else null,
        hpr = if (fullBehandler) { hpr } else null,
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

fun Periode.toSykmeldingsperiodeDTO(sykmeldingId: String): SykmeldingsperiodeDTO {
    return SykmeldingsperiodeDTO(
        fom = fom,
        tom = tom,
        behandlingsdager = behandlingsdager,
        gradert = gradert?.toGradertDTO(),
        innspillTilArbeidsgiver = avventendeInnspillTilArbeidsgiver,
        type = finnPeriodetype(this, sykmeldingId).toDTO(),
        aktivitetIkkeMulig = aktivitetIkkeMulig?.toDto(),
        reisetilskudd = reisetilskudd
    )
}

fun finnPeriodetype(periode: Periode, sykmeldingId: String): Periodetype =
    when {
        periode.aktivitetIkkeMulig != null -> Periodetype.AKTIVITET_IKKE_MULIG
        periode.avventendeInnspillTilArbeidsgiver != null -> Periodetype.AVVENTENDE
        periode.behandlingsdager != null -> Periodetype.BEHANDLINGSDAGER
        periode.gradert != null -> Periodetype.GRADERT
        periode.reisetilskudd -> Periodetype.REISETILSKUDD
        else -> throw RuntimeException("Kunne ikke bestemme typen til periode: $periode for sykmelding med id $sykmeldingId")
    }

fun Periodetype.toDTO(): PeriodetypeDTO =
    PeriodetypeDTO.valueOf(this.name)

private fun AktivitetIkkeMulig.toDto(): AktivitetIkkeMuligDTO {
    return AktivitetIkkeMuligDTO(medisinskArsak = medisinskArsak?.toMedisinskArsakDto(),
        arbeidsrelatertArsak = arbeidsrelatertArsak?.toArbeidsrelatertArsakDto())
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

fun Arbeidsgiver.toArbeidsgiverDTO(): ArbeidsgiverDTO {
    return ArbeidsgiverDTO(navn, stillingsprosent)
}

fun KontaktMedPasient.toKontaktMedPasientDTO(): KontaktMedPasientDTO {
    return KontaktMedPasientDTO(
        kontaktDato = kontaktDato,
        begrunnelseIkkeKontakt = begrunnelseIkkeKontakt
    )
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
