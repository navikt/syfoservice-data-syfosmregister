package no.nav.syfo.sendtsykmelding.sykmelding.model

data class DiagnoseDTO(
    val kode: String,
    val system: String,
    val tekst: String?
)
