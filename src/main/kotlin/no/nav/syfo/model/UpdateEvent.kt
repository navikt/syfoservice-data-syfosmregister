package no.nav.syfo.model

import java.time.LocalDateTime

data class UpdateEvent(
    val sykmeldingId: String,
    val created: LocalDateTime,
    val mottakId: String
)
