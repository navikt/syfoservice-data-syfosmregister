package no.nav.syfo.sykmelding.sykmelding.model

import java.time.LocalDate

data class ErIkkeIArbeidDTO(
    val arbeidsforPaSikt: Boolean,
    val arbeidsforFOM: LocalDate?,
    val vurderingsdato: LocalDate?
)
