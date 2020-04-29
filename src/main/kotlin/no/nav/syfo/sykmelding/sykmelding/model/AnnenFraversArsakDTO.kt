package no.nav.syfo.sykmelding.sykmelding.model

data class AnnenFraversArsakDTO(
    val beskrivelse: String?,
    val grunn: List<AnnenFraverGrunnDTO>
)
