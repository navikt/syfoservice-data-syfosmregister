package no.nav.syfo.sykmelding.db

import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.log

fun DatabaseInterfacePostgres.updateFnr(sykmeldingsId: String, pasientFnr: String) {
        connection.use { connection ->
            connection.prepareStatement("""
            UPDATE sykmeldingsopplysninger set pasient_fnr = ? where id = ?;
        """).use {
                it.setString(1, pasientFnr)
                it.setString(2, sykmeldingsId)
                val updated = it.executeUpdate()
                log.info("Updated {} sykmeldingsdokument", updated)
            }
            connection.commit()
        }
}
