package no.nav.syfo.sendtsykmelding.sykmelding.model

data class SporsmalSvarDTO(
    val sporsmal: String?,
    val svar: String,
    val restriksjoner: List<SvarRestriksjonDTO>
)
