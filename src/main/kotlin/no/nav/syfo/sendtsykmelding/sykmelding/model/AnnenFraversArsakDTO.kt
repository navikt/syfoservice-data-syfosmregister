package no.nav.syfo.sendtsykmelding.sykmelding.model

data class AnnenFraversArsakDTO(
    val beskrivelse: String?,
    val grunn: List<AnnenFraverGrunnDTO>
)
