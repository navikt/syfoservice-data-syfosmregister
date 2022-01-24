package no.nav.syfo.legeerklaring.kafka.model

data class ReceivedLegeerklaring(
    val fellesformat: String
)

data class LegeerklaringKafkaMessage(
    val receivedLegeerklaering: ReceivedLegeerklaring
)
