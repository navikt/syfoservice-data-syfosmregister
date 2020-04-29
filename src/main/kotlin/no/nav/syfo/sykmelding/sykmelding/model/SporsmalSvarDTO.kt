package no.nav.syfo.sykmelding.sykmelding.model

data class SporsmalSvarDTO(
    val sporsmal: String?,
    val svar: String,
    val restriksjoner: List<SvarRestriksjonDTO>
)
