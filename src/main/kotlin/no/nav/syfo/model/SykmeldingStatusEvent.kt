package no.nav.syfo.model

import java.time.LocalDateTime

data class SykmeldingStatusEvent(
    val mottakId: String,
    val timestamp: LocalDateTime,
    val event: StatusEvent
)

enum class StatusEvent {
    APEN, AVBRUTT, UTGATT, SENDT, BEKREFTET, SLETTET
}
