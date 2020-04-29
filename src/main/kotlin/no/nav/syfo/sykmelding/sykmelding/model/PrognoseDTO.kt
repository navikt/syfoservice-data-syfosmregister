package no.nav.syfo.sykmelding.sykmelding.model

data class PrognoseDTO(
    val arbeidsforEtterPeriode: Boolean,
    val hensynArbeidsplassen: String?,
    val erIArbeid: ErIArbeidDTO?,
    val erIkkeIArbeid: ErIkkeIArbeidDTO?

)
