package no.nav.syfo.sykmelding.db

import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.log

fun DatabaseInterfacePostgres.updateFnr(fnr: String, nyttFnr: String): Int {
        connection.use { connection ->
            var updated = 0
            connection.prepareStatement("""
            UPDATE sykmeldingsopplysninger set pasient_fnr = ? where pasient_fnr = ?;
        """).use {
                it.setString(1, nyttFnr)
                it.setString(2, fnr)
                updated = it.executeUpdate()
                log.info("Updated {} sykmeldingsdokument", updated)
            }
            connection.commit()
            return updated
        }
}
