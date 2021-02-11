package no.nav.syfo.sykmelding

import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.Ident
import no.nav.syfo.aksessering.db.oracle.getSykmeldingsDokument
import no.nav.syfo.aksessering.db.oracle.updateDocument
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.log
import no.nav.syfo.sykmelding.db.updateFnr

class UpdateFnrService(
    val syfoSmRegisterDb: DatabaseInterfacePostgres,
    val syfoServiceDb: DatabaseInterfaceOracle
) {

    fun updateFnr(sykmeldingId: String, fnr: String) {

        val result = syfoServiceDb.getSykmeldingsDokument(sykmeldingId)

        if (result.rows.isNotEmpty()) {
            log.info("updating sykmelding dokument with sykmelding id {}", sykmeldingId)
            val document = result.rows.first()
            if (document != null) {
                document.pasient.fodselsnummer = Ident().apply {
                    id = fnr
                    typeId = CV().apply {
                        v = "FNR"
                        s = "2.16.578.1.12.4.1.1.8116"
                        dn = "Fødselsnummer"
                    }
                }

                syfoServiceDb.updateDocument(document, sykmeldingId)
                syfoSmRegisterDb.updateFnr(sykmeldingId, fnr)
            }
        } else {
            log.info("could not find sykmelding with id {}", sykmeldingId)
        }
    }
}
