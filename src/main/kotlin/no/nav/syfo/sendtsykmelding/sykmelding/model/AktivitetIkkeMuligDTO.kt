package no.nav.syfo.sendtsykmelding.sykmelding.model

data class AktivitetIkkeMuligDTO(
    val medisinskArsak: MedisinskArsakDTO?,
    val arbeidsrelatertArsak: ArbeidsrelatertArsakDTO?
)
