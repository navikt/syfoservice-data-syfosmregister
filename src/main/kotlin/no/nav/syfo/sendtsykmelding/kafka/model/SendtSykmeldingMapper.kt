package no.nav.syfo.sendtsykmelding.kafka.model

import no.nav.syfo.sendtsykmelding.model.SendtSykmeldingDbModel
import no.nav.syfo.sendtsykmelding.model.SporsmalSvar
import no.nav.syfo.sendtsykmelding.sykmelding.model.SporsmalSvarDTO
import no.nav.syfo.sendtsykmelding.sykmelding.model.SvarRestriksjonDTO
import no.nav.syfo.sendtsykmelding.sykmelding.model.getHarRedusertArbeidsgiverperiode
import no.nav.syfo.sendtsykmelding.sykmelding.model.getUtcTime
import no.nav.syfo.sendtsykmelding.sykmelding.model.toArbeidsgiverDTO
import no.nav.syfo.sendtsykmelding.sykmelding.model.toBehandlerDTO
import no.nav.syfo.sendtsykmelding.sykmelding.model.toKontaktMedPasientDTO
import no.nav.syfo.sendtsykmelding.sykmelding.model.toPrognoseDTO
import no.nav.syfo.sendtsykmelding.sykmelding.model.toSporsmalSvarDTO
import no.nav.syfo.sendtsykmelding.sykmelding.model.toSykmeldingsperiodeDTO

fun SendtSykmeldingDbModel.toSendtSykmelding(): SendtSykmelding {
    return SendtSykmelding(
            id = id,
            andreTiltak = sykmeldingsDokument.andreTiltak,
            mottattTidspunkt = getUtcTime(mottattTidspunkt),
            legekontorOrgnr = legekontorOrgNr,
            behandletTidspunkt = getUtcTime(sykmeldingsDokument.behandletTidspunkt),
            meldingTilArbeidsgiver = sykmeldingsDokument.meldingTilArbeidsgiver,
            navnFastlege = sykmeldingsDokument.navnFastlege,
            tiltakArbeidsplassen = sykmeldingsDokument.tiltakArbeidsplassen,
            syketilfelleStartDato = sykmeldingsDokument.syketilfelleStartDato,
            behandler = sykmeldingsDokument.behandler.toBehandlerDTO(),
            sykmeldingsperioder = sykmeldingsDokument.perioder.map { it.toSykmeldingsperiodeDTO() },
            arbeidsgiver = sykmeldingsDokument.arbeidsgiver.toArbeidsgiverDTO(),
            kontaktMedPasient = sykmeldingsDokument.kontaktMedPasient.toKontaktMedPasientDTO(),
            prognose = sykmeldingsDokument.prognose?.toPrognoseDTO(),
            utdypendeOpplysninger = toUtdypendeOpplysninger(sykmeldingsDokument.utdypendeOpplysninger),
            egenmeldt = sykmeldingsDokument.avsenderSystem.navn == "Egenmeldt",
            papirsykmelding = sykmeldingsDokument.avsenderSystem.navn == "Papirsykmelding",
            harRedusertArbeidsgiverperiode = sykmeldingsDokument.medisinskVurdering.getHarRedusertArbeidsgiverperiode()
    )
}

private fun toUtdypendeOpplysninger(utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>>): Map<String, Map<String, SporsmalSvarDTO>> {
    return utdypendeOpplysninger.mapValues {
        it.value.mapValues { entry -> entry.value.toSporsmalSvarDTO() }
                .filterValues { sporsmalSvar -> !sporsmalSvar.restriksjoner.contains(SvarRestriksjonDTO.SKJERMET_FOR_ARBEIDSGIVER) }
    }
}
