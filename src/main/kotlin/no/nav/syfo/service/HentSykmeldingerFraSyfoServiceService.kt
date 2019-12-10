package no.nav.syfo.service

import no.nav.syfo.aksessering.db.oracle.hentAntallSykmeldingerSyfoService
import no.nav.syfo.aksessering.db.oracle.hentSykmeldingerSyfoService
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.kafka.SykmeldingKafkaProducer
import no.nav.syfo.log

class HentSykmeldingerFraSyfoServiceService(
    private val sykmeldingKafkaProducer: SykmeldingKafkaProducer,
    private val databaseOracle: DatabaseInterfaceOracle,
    private val batchSize: Int,
    private val lastIndexSyfoservice: Int
) {

    fun run(): Int {
        val hentantallSykmeldinger = databaseOracle.hentAntallSykmeldingerSyfoService()
        log.info("Antall sykmeldinger som finnes i databasen:  {}", hentantallSykmeldinger.first().antall)

        var counter = 0
        var lastIndex = lastIndexSyfoservice
        while (true) {
            val startTime = System.currentTimeMillis()
            val result = databaseOracle.hentSykmeldingerSyfoService(lastIndex, batchSize)
            for (sykmelding in result.rows) {
                sykmeldingKafkaProducer.publishToKafka(sykmelding)
            }
            val time = (System.currentTimeMillis() - startTime) / 1000.0
            lastIndex = result.lastIndex
            counter += result.rows.size
            log.info("Antall sykmeldinger som er hentet i dette forsoket:  {} totalt {}, time used {}, lastIndex {}", result.rows.size, counter, time, lastIndex)
            if (result.rows.isEmpty()) {
                log.info("no more sykmelinger in database")
                break
            }
        }
        return counter
    }
}
