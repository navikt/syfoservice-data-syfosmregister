package no.nav.syfo.service

import no.nav.syfo.aksessering.db.oracle.hentArbeidsgiverSyfoService
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.kafka.ArbeidsgiverSykmeldingKafkaProducer
import no.nav.syfo.log

class HentArbeidsGiverOgSporsmalFraSyfoServiceService(
    private val arbeidsgiverSykmeldingKafkaProducer: ArbeidsgiverSykmeldingKafkaProducer,
    private val databaseOracle: DatabaseInterfaceOracle,
    private val batchSize: Int
) {

    fun run(): Int {

        var lastIndex = 0
        var counter = 0

        while (true) {
            val currentMillies = System.currentTimeMillis()
            val result = databaseOracle.hentArbeidsgiverSyfoService(lastIndex, batchSize)

            for (sykmelding in result.rows) {
//                arbeidsgiverSykmeldingKafkaProducer.publishToKafka(sykmelding)
            }
            val time = (System.currentTimeMillis() - currentMillies) / 1000.0
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
