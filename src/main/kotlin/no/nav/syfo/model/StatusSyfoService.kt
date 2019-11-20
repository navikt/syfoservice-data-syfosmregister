package no.nav.syfo.model

import java.time.LocalDate
import java.time.LocalDateTime

data class StatusSyfoService(
    val status: String,
    val mottakid: String,
    val createdTimestmap: LocalDateTime,
    val sendTilArbeidsgiverDate: LocalDate?
)
