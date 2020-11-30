package no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.model

import java.time.LocalDateTime

private const val SOURCE = "migrering"

data class KafkaMessageMetadata(
    val sykmeldingId: String,
    val source: String = SOURCE
)

data class Tilleggsdata(
    val ediLoggId: String,
    val sykmeldingId: String,
    val msgId: String,
    val syketilfelleStartDato: LocalDateTime
)
