package no.nav.syfo.sykmelding.kafka.model

import no.nav.syfo.sykmelding.model.EnkelSykmeldingDbModel
import no.nav.syfo.sykmelding.model.MottattSykmeldingDbModel
import no.nav.syfo.sykmelding.sykmelding.model.getHarRedusertArbeidsgiverperiode
import no.nav.syfo.sykmelding.sykmelding.model.getUtcTime
import no.nav.syfo.sykmelding.sykmelding.model.toArbeidsgiverDTO
import no.nav.syfo.sykmelding.sykmelding.model.toBehandlerDTO
import no.nav.syfo.sykmelding.sykmelding.model.toKontaktMedPasientDTO
import no.nav.syfo.sykmelding.sykmelding.model.toPrognoseDTO
import no.nav.syfo.sykmelding.sykmelding.model.toSykmeldingsperiodeDTO

fun EnkelSykmeldingDbModel.toEnkelSykmelding(): EnkelSykmelidng {
    return EnkelSykmelidng(
            id = id,
            mottattTidspunkt = getUtcTime(mottattTidspunkt),
            legekontorOrgnummer = legekontorOrgNr,
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
            egenmeldt = sykmeldingsDokument.avsenderSystem.navn == "Egenmeldt",
            papirsykmelding = sykmeldingsDokument.avsenderSystem.navn == "Papirsykmelding",
            harRedusertArbeidsgiverperiode = sykmeldingsDokument.medisinskVurdering.getHarRedusertArbeidsgiverperiode(sykmeldingsDokument.perioder)
    )
}

fun MottattSykmeldingDbModel.toEnkelSykmelding(): EnkelSykmelidng {
        return EnkelSykmelidng(
                id = id,
                mottattTidspunkt = getUtcTime(mottattTidspunkt),
                legekontorOrgnummer = legekontorOrgNr,
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
                egenmeldt = sykmeldingsDokument.avsenderSystem.navn == "Egenmeldt",
                papirsykmelding = sykmeldingsDokument.avsenderSystem.navn == "Papirsykmelding",
                harRedusertArbeidsgiverperiode = sykmeldingsDokument.medisinskVurdering.getHarRedusertArbeidsgiverperiode(sykmeldingsDokument.perioder)
        )
}
