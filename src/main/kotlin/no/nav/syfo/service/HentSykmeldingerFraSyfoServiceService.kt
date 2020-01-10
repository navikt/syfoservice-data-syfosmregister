package no.nav.syfo.service

import no.nav.syfo.aksessering.db.oracle.hentAntallSykmeldingerSyfoService
import no.nav.syfo.aksessering.db.oracle.hentFravaerForSykmelding
import no.nav.syfo.aksessering.db.oracle.hentSykmeldingFraSyfoService
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
        var periodeCounter = 0
        while (true) {
            val startTime = System.currentTimeMillis()
            var result = databaseOracle.hentSykmeldingFraSyfoService(listOf("448ae5c7-0ed4-421c-955e-4c175cf280c5", "bedd0998-7e07-4e29-98c0-2a69b5eb6db4"))
            for (sykmelding in result.rows) {
                if (sykmelding.containsKey("HAR_FRAVAER") && sykmelding["HAR_FRAVAER"] == "1") {
                    periodeCounter++
                    val fravaerResult = databaseOracle.hentFravaerForSykmelding(sykmelding["sm_sporsmal_id".toUpperCase()] as Int)
                    sykmelding["FRAVAER"] = fravaerResult
                }
                sykmeldingKafkaProducer.publishToKafka(sykmelding)
            }
            val timeUsed = (System.currentTimeMillis() - startTime) / 1000.0
            lastIndex = result.lastIndex
            counter += result.rows.size
            log.info("Antall sykmeldinger som er hentet i dette forsoket:  {} totalt {}, time used {}, lastIndex {}, antall perioder hentet {}", result.rows.size, counter, timeUsed, lastIndex, periodeCounter)
            if (result.rows.isEmpty()) {
                log.info("no more sykmelinger in database")
                break
            }
        }
        return counter
    }
}
