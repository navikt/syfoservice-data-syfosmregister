package no.nav.syfo.identendring.model

import no.nav.syfo.identendring.db.AktivitetIkkeMulig
import no.nav.syfo.identendring.db.Arbeidsgiver
import no.nav.syfo.identendring.db.ArbeidsrelatertArsak
import no.nav.syfo.identendring.db.ArbeidsrelatertArsakType
import no.nav.syfo.identendring.db.Behandler
import no.nav.syfo.identendring.db.Gradert
import no.nav.syfo.identendring.db.KontaktMedPasient
import no.nav.syfo.identendring.db.Periode
import no.nav.syfo.identendring.db.Prognose
import no.nav.syfo.identendring.db.SykmeldingDbModelUtenBehandlingsutfall
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.BehandlerAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.KontaktMedPasientAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.PrognoseAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.AdresseDTO
import no.nav.syfo.model.sykmelding.model.ArbeidsrelatertArsakDTO
import no.nav.syfo.model.sykmelding.model.ArbeidsrelatertArsakTypeDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.sykmelding.model.getUtcTime

fun SykmeldingDbModelUtenBehandlingsutfall.toArbeidsgiverSykmelding(): ArbeidsgiverSykmelding {
    return ArbeidsgiverSykmelding(
        id = id,
        mottattTidspunkt = mottattTidspunkt,
        behandletTidspunkt = getUtcTime(sykmeldingsDokument.behandletTidspunkt),
        meldingTilArbeidsgiver = sykmeldingsDokument.meldingTilArbeidsgiver,
        tiltakArbeidsplassen = sykmeldingsDokument.tiltakArbeidsplassen,
        syketilfelleStartDato = sykmeldingsDokument.syketilfelleStartDato,
        behandler = sykmeldingsDokument.behandler.toBehandlerAGDTO(),
        sykmeldingsperioder = sykmeldingsDokument.perioder.map { it.toSykmeldingsperiodeAGDTO(id) },
        arbeidsgiver = sykmeldingsDokument.arbeidsgiver.toArbeidsgiverAGDTO(),
        kontaktMedPasient = sykmeldingsDokument.kontaktMedPasient.toKontaktMedPasientAGDTO(),
        prognose = sykmeldingsDokument.prognose?.toPrognoseAGDTO(),
        egenmeldt = sykmeldingsDokument.avsenderSystem.navn == "Egenmeldt",
        papirsykmelding = sykmeldingsDokument.avsenderSystem.navn == "Papirsykmelding",
        harRedusertArbeidsgiverperiode = sykmeldingsDokument.medisinskVurdering.getHarRedusertArbeidsgiverperiode(sykmeldingsDokument.perioder),
        merknader = merknader?.map { Merknad(type = it.type, beskrivelse = it.beskrivelse) }
    )
}

private fun Behandler.toBehandlerAGDTO(): BehandlerAGDTO {
    return BehandlerAGDTO(
        fornavn = fornavn,
        mellomnavn = mellomnavn,
        hpr = hpr,
        tlf = tlf,
        etternavn = etternavn,
        adresse = AdresseDTO(adresse.gate, adresse.postnummer, adresse.kommune, adresse.postboks, adresse.land)
    )
}

fun Periode.toSykmeldingsperiodeAGDTO(sykmeldingId: String): SykmeldingsperiodeAGDTO {
    return SykmeldingsperiodeAGDTO(
        fom = fom,
        tom = tom,
        behandlingsdager = behandlingsdager,
        gradert = gradert?.toGradertDTO(),
        innspillTilArbeidsgiver = avventendeInnspillTilArbeidsgiver,
        type = finnPeriodetype(this, sykmeldingId),
        aktivitetIkkeMulig = aktivitetIkkeMulig?.toAktivitetIkkeMuligAGDTO(),
        reisetilskudd = reisetilskudd
    )
}

private fun finnPeriodetype(periode: Periode, sykmeldingId: String): PeriodetypeDTO =
    when {
        periode.aktivitetIkkeMulig != null -> PeriodetypeDTO.AKTIVITET_IKKE_MULIG
        periode.avventendeInnspillTilArbeidsgiver != null -> PeriodetypeDTO.AVVENTENDE
        periode.behandlingsdager != null -> PeriodetypeDTO.BEHANDLINGSDAGER
        periode.gradert != null -> PeriodetypeDTO.GRADERT
        periode.reisetilskudd -> PeriodetypeDTO.REISETILSKUDD
        else -> throw RuntimeException("Kunne ikke bestemme typen til periode: $periode for sykmeldingId $sykmeldingId")
    }

private fun AktivitetIkkeMulig?.toAktivitetIkkeMuligAGDTO(): AktivitetIkkeMuligAGDTO? {
    return when (this) {
        null -> null
        else -> AktivitetIkkeMuligAGDTO(
            arbeidsrelatertArsak = arbeidsrelatertArsak.toArbeidsRelatertArsakDTO()
        )
    }
}

private fun ArbeidsrelatertArsak?.toArbeidsRelatertArsakDTO(): ArbeidsrelatertArsakDTO? {
    return when (this) {
        null -> null
        else -> ArbeidsrelatertArsakDTO(
            beskrivelse = beskrivelse,
            arsak = arsak.map { toArbeidsrelatertArsakTypeDTO(it) }
        )
    }
}

private fun toArbeidsrelatertArsakTypeDTO(arbeidsrelatertArsakType: ArbeidsrelatertArsakType): ArbeidsrelatertArsakTypeDTO {
    return when (arbeidsrelatertArsakType) {
        ArbeidsrelatertArsakType.MANGLENDE_TILRETTELEGGING -> ArbeidsrelatertArsakTypeDTO.MANGLENDE_TILRETTELEGGING
        ArbeidsrelatertArsakType.ANNET -> ArbeidsrelatertArsakTypeDTO.ANNET
    }
}

private fun Gradert?.toGradertDTO(): GradertDTO? {
    return when (this) {
        null -> null
        else -> GradertDTO(
            grad = grad,
            reisetilskudd = reisetilskudd
        )
    }
}

private fun Arbeidsgiver.toArbeidsgiverAGDTO(): ArbeidsgiverAGDTO {
    return ArbeidsgiverAGDTO(
        navn = navn,
        yrkesbetegnelse = yrkesbetegnelse
    )
}

private fun KontaktMedPasient.toKontaktMedPasientAGDTO(): KontaktMedPasientAGDTO {
    return KontaktMedPasientAGDTO(
        kontaktDato = kontaktDato
    )
}

private fun Prognose?.toPrognoseAGDTO(): PrognoseAGDTO? {
    return when (this) {
        null -> null
        else -> {
            PrognoseAGDTO(
                arbeidsforEtterPeriode = arbeidsforEtterPeriode,
                hensynArbeidsplassen = hensynArbeidsplassen
            )
        }
    }
}
