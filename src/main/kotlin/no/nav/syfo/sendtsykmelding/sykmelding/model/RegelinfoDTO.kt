package no.nav.syfo.sendtsykmelding.sykmelding.model

data class RegelinfoDTO(
    val messageForSender: String,
    val messageForUser: String,
    val ruleName: String,
    val ruleStatus: RegelStatusDTO?
)
