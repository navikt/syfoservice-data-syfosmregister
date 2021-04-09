package no.nav.syfo.pdl.service

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.log
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.error.AktoerNotFoundException
import no.nav.syfo.pdl.model.PdlPerson

@KtorExperimentalAPI
class PdlPersonService(private val pdlClient: PdlClient, private val stsOidcClient: StsOidcClient) {
    suspend fun getPdlPerson(fnr: String, userToken: String): PdlPerson {
        val stsToken = stsOidcClient.oidcToken().access_token
        val pdlResponse = pdlClient.getPerson(fnr, userToken, stsToken)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.error("PDL kastet error: {} ", it)
            }
        }
        if (pdlResponse.data.hentIdenter == null || pdlResponse.data.hentIdenter.identer.isNullOrEmpty()) {
            log.error("Fant ikke aktørid i PDL {}")
            throw AktoerNotFoundException("Fant ikke aktørId i PDL")
        }
        return PdlPerson(pdlResponse.data.hentIdenter.identer)
    }

    suspend fun getFnrs(identer: List<String>, narmesteLederId: String): Map<String, String?> {
        val stsToken = stsOidcClient.oidcToken().access_token
        val pdlResponse = pdlClient.getFnrs(aktorids = identer, stsToken = stsToken)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.error("PDL returnerte error {}, {}", it, narmesteLederId)
            }
        }
        if (pdlResponse.data.hentIdenterBolk == null || pdlResponse.data.hentIdenterBolk.isNullOrEmpty()) {
            log.error("Fant ikke identer i PDL {}", narmesteLederId)
            throw IllegalStateException("Fant ingen identer i PDL, skal ikke kunne skje!")
        }
        pdlResponse.data.hentIdenterBolk.forEach {
            if (it.code != "ok") {
                log.warn("Mottok feilkode ${it.code} fra PDL for en eller flere identer, {}", narmesteLederId)
            }
        }
        return pdlResponse.data.hentIdenterBolk.map {
            it.ident to it.identer?.firstOrNull { ident -> ident.gruppe == "FOLKEREGISTERIDENT" }?.ident
        }.toMap()
    }
}
