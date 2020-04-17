package no.nav.syfo.sendtsykmelding.sykmelding.model

data class BehandlingsutfallDTO(
    val status: RegelStatusDTO,
    val ruleHits: List<RegelinfoDTO>
)
