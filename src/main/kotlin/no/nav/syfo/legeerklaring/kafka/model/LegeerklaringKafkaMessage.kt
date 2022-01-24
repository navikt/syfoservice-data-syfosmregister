package no.nav.syfo.legeerklaring.kafka.model

data class ReceivedLegeerklaring(
    val fellesformat: String,
    val msgId: String
)

data class LegeerklaringKafkaMessage(
    val receivedLegeerklaering: ReceivedLegeerklaring
)
