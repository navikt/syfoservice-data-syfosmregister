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

        when {
            pdlPerson.fnr != nyttFnr -> {
                val msg = "Oppdatering av fnr feilet, nyttFnr != pdlPerson.fnr"
                log.error(msg)
                throw RuntimeException(msg)
            }
            !pdlPerson.harHistoriskFnr(fnr) -> {
                val msg = "Oppdatering av fnr feilet, fnr er ikke historisk for aktør"
                log.error(msg)
                throw RuntimeException(msg)
            }
            else -> {
                log.info("Oppdaterer fnr for person ")
                val updateFnr = syfoSmRegisterDb.updateFnr(nyttFnr = nyttFnr, fnr = fnr)
                return updateFnr > 0
            }
        }

    }
}
