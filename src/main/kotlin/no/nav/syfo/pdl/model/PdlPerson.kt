package no.nav.syfo.pdl.model

import no.nav.syfo.pdl.client.model.IdentInformasjon

data class PdlPerson(
    val identer: List<IdentInformasjon>,
    val navn: String,
) {
    val fnr: String? = identer.firstOrNull { it.gruppe == "FOLKEREGISTERIDENT" && !it.historisk }?.ident
    val aktorId: String? = identer.firstOrNull { it.gruppe == "AKTORID" && !it.historisk }?.ident

    fun harHistoriskFnr(fnr: String): Boolean {
        return finnIdent(ident = fnr, gruppe = "FOLKEREGISTERIDENT").any { it.historisk }
    }

    fun finnIdent(ident: String, gruppe: String): List<IdentInformasjon> {
        return identer.filter { it.ident == ident && it.gruppe == gruppe }
    }
}
