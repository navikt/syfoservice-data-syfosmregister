package no.nav.syfo.model

enum class ShortName {
    ARBEIDSSITUASJON, NY_NARMESTE_LEDER, FRAVAER, PERIODE, FORSIKRING
}
enum class Svartype {
    ARBEIDSSITUASJON,
    PERIODER,
    JA_NEI
}

data class Sporsmal(
    val tekst: String,
    val shortName: ShortName,
    val svar: Svar
)

data class Svar(
    val sykmeldingId: String,
    val sporsmalId: Int?,
    val svartype: Svartype,
    val svar: String
)
