package no.nav.syfo.narmesteleder.kafkamodel

data class Leder(
    val fnr: String,
    val mobil: String,
    val epost: String,
    val fornavn: String?,
    val etternavn: String?
)
