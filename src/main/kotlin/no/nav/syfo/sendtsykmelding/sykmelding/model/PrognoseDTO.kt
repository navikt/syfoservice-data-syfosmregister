package no.nav.syfo.sendtsykmelding.sykmelding.model

data class PrognoseDTO(
    val arbeidsforEtterPeriode: Boolean,
    val hensynArbeidsplassen: String?,
    val erIArbeid: ErIArbeidDTO?,
    val erIkkeIArbeid: ErIkkeIArbeidDTO?

)
