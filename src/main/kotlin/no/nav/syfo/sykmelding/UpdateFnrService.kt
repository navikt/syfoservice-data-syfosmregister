package no.nav.syfo.sykmelding

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.db.updateFnr
import org.slf4j.LoggerFactory

@KtorExperimentalAPI
class UpdateFnrService(
    private val pdlPersonService: PdlPersonService,
    private val syfoSmRegisterDb: DatabaseInterfacePostgres
) {

    private val log = LoggerFactory.getLogger(UpdateFnrService::class.java)

    suspend fun updateFnr(fnr: String, nyttFnr: String): Boolean {

        val pdlPerson = pdlPersonService.getPdlPerson(fnr)

        when {
            pdlPerson.fnr != nyttFnr -> {
                val msg = "Oppdatering av fnr feilet, nyttFnr står ikke som aktivt fnr for aktøren i PDL"
                log.error(msg)
                throw UpdateIdentException(msg)
            }
            !pdlPerson.harHistoriskFnr(fnr) -> {
                val msg = "Oppdatering av fnr feilet, fnr er ikke historisk for aktør"
                log.error(msg)
                throw UpdateIdentException(msg)
            }
            else -> {
                log.info("Oppdaterer fnr for person ")
                val updateFnr = syfoSmRegisterDb.updateFnr(nyttFnr = nyttFnr, fnr = fnr)
                return updateFnr > 0
            }
        }
    }
}

class UpdateIdentException(override val message: String) : Exception(message)
