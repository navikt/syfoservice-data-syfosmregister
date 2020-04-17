package no.nav.syfo.sendtsykmelding.kafka.model

import java.time.LocalDate
import java.time.OffsetDateTime
import no.nav.syfo.sendtsykmelding.sykmelding.model.ArbeidsgiverDTO
import no.nav.syfo.sendtsykmelding.sykmelding.model.BehandlerDTO
import no.nav.syfo.sendtsykmelding.sykmelding.model.KontaktMedPasientDTO
import no.nav.syfo.sendtsykmelding.sykmelding.model.PrognoseDTO
import no.nav.syfo.sendtsykmelding.sykmelding.model.SporsmalSvarDTO
import no.nav.syfo.sendtsykmelding.sykmelding.model.SykmeldingsperiodeDTO

data class SendtSykmelding(
    val id: String,
    val andreTiltak: String?,
    val mottattTidspunkt: OffsetDateTime,
    val legekontorOrgnr: String?,
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
    val utdypendeOpplysninger: Map<String, Map<String, SporsmalSvarDTO>>,
    val egenmeldt: Boolean,
    val papirsykmelding: Boolean,
    val harRedusertArbeidsgiverperiode: Boolean
)
