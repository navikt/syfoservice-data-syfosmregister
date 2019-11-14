package no.nav.syfo.service

import no.nav.syfo.aksessering.db.oracle.hentAntallSykmeldingerEia
import no.nav.syfo.aksessering.db.oracle.hentSykmeldingerEia
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.kafka.EiaSykmeldingKafkaProducer
import no.nav.syfo.log

class HentSykmeldingerFraEiaService(
    private val eiaKafkaProducer: EiaSykmeldingKafkaProducer,
    private val databaseOracle: DatabaseInterfaceOracle,
    private val batchSize: Int,
    private var lastIndex: Int
) {

    fun run(): Int {
        val hentantallSykmeldinger = databaseOracle.hentAntallSykmeldingerEia()
        log.info("Antall sykmeldinger som finnes i databasen:  {}", hentantallSykmeldinger.first().antall)

        var counter = 0

        while (true) {

            val result = databaseOracle.hentSykmeldingerEia(lastIndex, batchSize)

            val currentMillies = System.currentTimeMillis()

            for (eiaSykmelding in result.rows) {
                eiaKafkaProducer.publishToKafka(eiaSykmelding)
            }
            val kafkaTime = (System.currentTimeMillis() - currentMillies) / 1000.0
            lastIndex = result.lastIndex
            counter += result.rows.size
            log.info(
                "Antall sykmeldinger som er hentet i dette forsoket:  {} totalt {}, DB time used {}, processing time {}, lastIndex {}, kafkatime {}",
                result.rows.size,
                counter,
                result.databaseTime,
                result.processingTime,
                lastIndex,
                kafkaTime
            )
            if (result.rows.isEmpty()) {
                log.info("no more sykmelinger in database")
                break
            }
        }

        return counter
    }
}
