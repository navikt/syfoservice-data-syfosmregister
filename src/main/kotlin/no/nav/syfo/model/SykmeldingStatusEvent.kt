package no.nav.syfo.model

import java.time.LocalDateTime
import java.time.OffsetDateTime

data class SykmeldingStatus(
    val timestamp: LocalDateTime,
    val statusEvent: StatusEvent,
    val arbeidsgiver: ArbeidsgiverStatus?,
    val sporsmalListe: List<Sporsmal>?
)

data class ArbeidsgiverStatus(
    val sykmeldingId: String,
    val orgnummer: String,
    val juridiskOrgnummer: String?,
    val orgnavn: String
)

data class SykmeldingStatusEvent(
    val sykmeldingId: String,
    val eventTimestamp: LocalDateTime,
    val event: StatusEvent,
    var timestamp: OffsetDateTime? = null
)

enum class StatusEvent {
    APEN, AVBRUTT, UTGATT, SENDT, BEKREFTET, SLETTET
}
