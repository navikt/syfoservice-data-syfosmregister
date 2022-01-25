package no.nav.syfo.legeerklaring.kafka.model

import com.fasterxml.jackson.databind.JsonNode

data class ReceivedLegeerklaring(
    val msgId: String,
    val legeerklaering: Legeerklaering
)

data class Legeerklaering(
    val id: String
)

data class OnPremLegeerklaringKafkaMessage(
    val receivedLegeerklaering: JsonNode,
    val validationResult: ValidationResult,
    val vedlegg: List<String>?
)

data class LegeerklaringKafkaMessage(
    val legeerklaeringObjectId: String,
    val validationResult: ValidationResult,
    val vedlegg: List<String>?
)

data class ValidationResult(
    val status: Status,
    val ruleHits: List<RuleInfo>
)

data class RuleInfo(
    val ruleName: String,
    val messageForSender: String,
    val messageForUser: String,
    val ruleStatus: Status
)

enum class Status {
    OK,
    INVALID
}
