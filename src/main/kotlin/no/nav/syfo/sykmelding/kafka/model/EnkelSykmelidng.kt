package no.nav.syfo.sykmelding.kafka.model

import no.nav.syfo.sykmelding.sykmelding.model.ArbeidsgiverDTO
import no.nav.syfo.sykmelding.sykmelding.model.BehandlerDTO
import no.nav.syfo.sykmelding.sykmelding.model.KontaktMedPasientDTO
import no.nav.syfo.sykmelding.sykmelding.model.PrognoseDTO
import no.nav.syfo.sykmelding.sykmelding.model.SykmeldingsperiodeDTO
import java.time.LocalDate
import java.time.OffsetDateTime

data class EnkelSykmelidng(
    val id: String,
    val mottattTidspunkt: OffsetDateTime,
    val legekontorOrgnummer: String?,
    val behandletTidspunkt: OffsetDateTime,
    val meldingTilArbeidsgiver: String?,
    val navnFastlege: String?,
    val tiltakArbeidsplassen: String?,
    val syketilfelleStartDato: LocalDate?,
    val behandler: BehandlerDTO,
    val sykmeldingsperioder: List<SykmeldingsperiodeDTO>,
    val arbeidsgiver: ArbeidsgiverDTO,
    val kontaktMedPasient: KontaktMedPasientDTO,
    val prognose: PrognoseDTO?,
    val egenmeldt: Boolean,
    val papirsykmelding: Boolean,
    val harRedusertArbeidsgiverperiode: Boolean
)
