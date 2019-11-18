package no.nav.syfo.service

import no.nav.syfo.aksessering.db.oracle.hentAntallSykmeldingerSyfoService
import no.nav.syfo.aksessering.db.oracle.hentSykmeldingStatusSyfoService
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.log

class HentStatusFraSyfoServiceService(
    // private val sykmeldingKafkaProducer: SykmeldingKafkaProducer,
    private val databaseOracle: DatabaseInterfaceOracle,
    private val batchSize: Int
) {

    fun run(): Int {
        val hentantallSykmeldinger = databaseOracle.hentAntallSykmeldingerSyfoService()
        log.info("Antall statuser som finnes i databasen:  {}", hentantallSykmeldinger.first().antall)

        var lastIndex = 0
        var counter = 0

        while (true) {
            val result = databaseOracle.hentSykmeldingStatusSyfoService(lastIndex, batchSize)
            val currentMillies = System.currentTimeMillis()
            /*for (sykmelding in result.rows) {
                sykmeldingKafkaProducer.publishToKafka(sykmelding)
            }*/
            val kafkaTime = (System.currentTimeMillis() - currentMillies) / 1000.0
            lastIndex = result.lastIndex
            counter += result.rows.size
            log.info("Antall statuser som er hentet i dette forsoket:  {} totalt {}, DB time used {}, " +
                    "processing time {}, lastIndex {}, kafkatime {}",
                result.rows.size, counter, result.databaseTime, result.processingTime, lastIndex, kafkaTime)
            if (result.rows.isEmpty()) {
                log.info("no more statuser in database")
                break
            }
        }
        return counter
    }
}
