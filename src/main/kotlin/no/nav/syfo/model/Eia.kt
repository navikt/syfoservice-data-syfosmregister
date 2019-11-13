package no.nav.syfo.model

data class Eia(
    val pasientfnr: String,
    val legefnr: String,
    val mottakid: String,
    val legekontorOrgnr: String?,
    val legekontorOrgnavn: String?,
    val legekontorHer: String?,
    val legekontorResh: String?
)
