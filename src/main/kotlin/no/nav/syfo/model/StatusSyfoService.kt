package no.nav.syfo.model

import java.time.LocalDateTime

data class StatusSyfoService(
    val status: String,
    val sykmeldingId: String,
    val createdTimestmap: LocalDateTime,
    val sendTilArbeidsgiverDate: LocalDateTime?
)
