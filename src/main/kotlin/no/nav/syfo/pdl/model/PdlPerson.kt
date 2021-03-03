package no.nav.syfo.pdl.model

import no.nav.syfo.pdl.client.model.IdentInformasjon

data class PdlPerson(
    val identer: List<IdentInformasjon>
) {
    val fnr: String? = identer.firstOrNull { it.gruppe == "FOLKEREGISTERIDENT" }?.ident
    val aktorId: String? = identer.firstOrNull { it.gruppe == "AKTORID" }?.ident
    val npId: String? = identer.firstOrNull { it.gruppe == "NPID" }?.ident
}
