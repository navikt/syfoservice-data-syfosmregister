package no.nav.syfo.service

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
        var counter = 0
        while (true) {
            val currentMillies = System.currentTimeMillis()
            val result = databaseOracle.hentSykmeldingerEia(lastIndex, batchSize)

            for (eiaSykmelding in result.rows) {
                eiaKafkaProducer.publishToKafka(eiaSykmelding)
            }
            val time = (System.currentTimeMillis() - currentMillies) / 1000.0
            lastIndex = result.lastIndex
            counter += result.rows.size
            log.info(
                "Antall sykmeldinger som er hentet i dette forsoket:  {} totalt {}, lastIndex {}, time {}",
                result.rows.size,
                counter,
                lastIndex,
                time
            )
            if (result.rows.isEmpty()) {
                log.info("no more sykmelinger in database")
                break
            }
        }

        return counter
    }
}
