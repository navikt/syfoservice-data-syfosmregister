package no.nav.syfo.narmesteleder.api

data class NlRequestDTO(
    val fnr: String,
    val orgnummer: String,
    val sykmeldingId: String
)
