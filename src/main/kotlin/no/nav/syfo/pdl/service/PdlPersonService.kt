package no.nav.syfo.pdl.service

import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.error.AktoerNotFoundException
import no.nav.syfo.pdl.model.PdlPerson
import org.slf4j.LoggerFactory

class PdlPersonService(private val pdlClient: PdlClient, private val stsOidcClient: StsOidcClient) {
    companion object {
        private val log = LoggerFactory.getLogger(PdlPersonService::class.java)
    }
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
}
