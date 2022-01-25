package no.nav.syfo.legeerklaring

import no.nav.syfo.legeerklaring.kafka.model.ValidationResult

data class SimpleReceivedLegeerklaeering(val fellesformat: String, val msgId: String = "msgId", val test: String = "test")
data class SimpleLegeerklaeringKafkaMessage(
    val receivedLegeerklaering: SimpleReceivedLegeerklaeering,
    val validationResult: ValidationResult? = null,
    val vedlegg: List<String>? = null
)
