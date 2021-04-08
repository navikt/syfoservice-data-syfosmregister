package no.nav.syfo.narmesteleder

import java.time.LocalDate

data class SyfoServiceNarmesteLeder(
    val id: Long,
    val aktorId: String,
    val orgnummer: String,
    val nlAktorId: String,
    val nlTelefonnummer: String,
    val nlEpost: String,
    val aktivFom: LocalDate,
    val aktivTom: LocalDate?,
    val agForskutterer: Boolean?
)
