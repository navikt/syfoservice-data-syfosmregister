package no.nav.syfo.sendtsykmelding.sykmelding.model

import java.time.LocalDate

data class ErIkkeIArbeidDTO(
    val arbeidsforPaSikt: Boolean,
    val arbeidsforFOM: LocalDate?,
    val vurderingsdato: LocalDate?
)
