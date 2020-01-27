package no.nav.syfo.model

import no.nav.syfo.objectMapper
import org.postgresql.util.PGobject

data class Behandlingsutfall(
    val id: String,
    val behandlingsutfall: ValidationResult
)

data class ValidationResult(
    val status: Status,
    val ruleHits: List<RuleInfo>
)

data class RuleInfo(
    val ruleName: String,
    val messageForSender: String,
    val messageForUser: String,
    val ruleStatus: Status?
)

fun ValidationResult.toPGObject() = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(this)
}
