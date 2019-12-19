package no.nav.syfo.model

import java.time.LocalDateTime

data class SykmeldingStatusTopicEvent(
    val sykmeldingId: String,
    val arbeidssituasjon: String?,
    val sendtTilArbeidsgiverDato: LocalDateTime?,
    val arbeidsgiver: ArbeidsgiverStatus?,
    val status: StatusEvent,
    val created: LocalDateTime,
    val harFravaer: Boolean?,
    val harForsikring: Boolean?,
    val fravarsPeriode: List<FravarsPeriode>?,
    val kafkaTimestamp: LocalDateTime
)
