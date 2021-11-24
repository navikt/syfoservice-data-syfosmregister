package no.nav.syfo.narmesteleder.kafkamodel

import java.time.OffsetDateTime

data class NlAvbrutt(
    val orgnummer: String,
    val sykmeldtFnr: String,
    val aktivTom: OffsetDateTime
)
