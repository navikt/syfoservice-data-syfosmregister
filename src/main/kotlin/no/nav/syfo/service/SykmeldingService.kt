package no.nav.syfo.service

import no.nav.syfo.aksessering.db.hentAntallSykmeldinger
import no.nav.syfo.aksessering.db.hentSykmeldinger
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.kafka.SykmeldingKafkaProducer
import no.nav.syfo.log

class SykmeldingService(
    private val sykmeldingKafkaProducer: SykmeldingKafkaProducer,
    private val database: DatabaseInterface,
    private val batchSize: Int
) {

    fun run(): Int {
        val hentantallSykmeldinger = database.hentAntallSykmeldinger()
        log.info("Antall sykmeldinger som finnes i databasen:  {}", hentantallSykmeldinger.first().antall)
        val antallSykmeldinger = hentantallSykmeldinger.first().antall.toInt()

        var lastIndex = 0
        var counter = 0

        while (true) {

            val result = database.hentSykmeldinger(lastIndex, batchSize)
            lastIndex = result.lastIndex
            counter += result.rows.size
            log.info("Antall sykmeldinger som er hentet i dette forsoket:  {} totalt {}, DB time used {}, processing time {}, lastIndex {}", result.rows.size, counter, result.databaseTime, result.processingTime, lastIndex)
            if (result.rows.isEmpty()) {
                log.info("no more sykmelinger in database")
                break
            }
        }
        return counter
    }
}
