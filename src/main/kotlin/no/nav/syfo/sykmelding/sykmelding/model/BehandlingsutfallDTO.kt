package no.nav.syfo.sykmelding.sykmelding.model

data class BehandlingsutfallDTO(
    val status: RegelStatusDTO,
    val ruleHits: List<RegelinfoDTO>
)
