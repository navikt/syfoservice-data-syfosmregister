package no.nav.syfo.service

import no.nav.syfo.aksessering.db.hentAntallSykmeldinger
import no.nav.syfo.aksessering.db.hentSykmeldinger
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log

class SykmeldingService(
    private val database: DatabaseInterface,
    private val batchSize: Int
) {

    fun run(): Int {
        val hentantallSykmeldinger = database.hentAntallSykmeldinger()
        log.info("Antall sykmeldinger som finnes i databasen:  {}", hentantallSykmeldinger.first().antall)
        var counter = 0

        val antallSykmeldinger = hentantallSykmeldinger.first().antall.toInt()

        while (antallSykmeldinger > counter) {
            val hentetSykmeldinger = database.hentSykmeldinger(counter + 1, counter + batchSize)
            counter += hentetSykmeldinger.size
            log.info("Antall sykmeldinger som er mappet over:  {}", hentetSykmeldinger.size)
            log.info("Counter på sykmeldinger som er mappet over:  {}", counter)
        }
        return counter
    }
}
