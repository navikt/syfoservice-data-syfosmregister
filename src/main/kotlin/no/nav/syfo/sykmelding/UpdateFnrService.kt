package no.nav.syfo.sykmelding

import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.db.updateFnr
import org.slf4j.LoggerFactory

class UpdateFnrService(
    private val pdlPersonService: PdlPersonService,
    private val syfoSmRegisterDb: DatabaseInterfacePostgres
) {

    private val log = LoggerFactory.getLogger(UpdateFnrService::class.java)

    suspend fun updateFnr(accessToken: String, fnr: String, nyttFnr: String): Boolean {

        val pdlPerson = pdlPersonService.getPdlPerson(fnr, accessToken)

        log.info("Debugging PDL lookup on Q, f = {}, nf = {}, pdlPerson = {}", fnr, nyttFnr, pdlPerson)
        if (pdlPerson.fnr != nyttFnr) {
            // Vi sjekker bare nyttFnr, det gamle finnes ofte ikke i PDL
            log.error("Oppdatering av fnr feilet, nytt fnr ikke funnet i PDL")
            throw RuntimeException("Oppdatering av fnr feilet, nytt fnr ikke funnet i PDL")
        }

        log.info("Oppdaterer fnr for person ")
        val updateFnr = syfoSmRegisterDb.updateFnr(nyttFnr = nyttFnr, fnr = fnr)
        return updateFnr > 0
    }
}
