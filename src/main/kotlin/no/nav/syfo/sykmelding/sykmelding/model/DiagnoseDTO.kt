package no.nav.syfo.sykmelding.sykmelding.model

data class DiagnoseDTO(
    val kode: String,
    val system: String,
    val tekst: String?
)
