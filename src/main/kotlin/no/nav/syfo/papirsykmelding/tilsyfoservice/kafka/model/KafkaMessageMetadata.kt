package no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.model

private const val SOURCE = "migrering"

data class KafkaMessageMetadata(
    val sykmeldingId: String,
    val source: String = SOURCE
)
