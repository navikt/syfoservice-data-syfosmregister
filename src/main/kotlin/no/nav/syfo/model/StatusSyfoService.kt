package no.nav.syfo.model

import java.time.LocalDateTime

data class StatusSyfoService(
    val status: String,
    val mottakid: String,
    val createdTimestmap: LocalDateTime,
    val sendTilArbeidsgiverDate: LocalDateTime?
)
