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
            val result = databaseOracle.hentArbeidsgiverSyfoService(lastIndex, batchSize)
            val currentMillies = System.currentTimeMillis()
            for (sykmelding in result.rows) {
                arbeidsgiverSykmeldingKafkaProducer.publishToKafka(sykmelding)
            }
            val kafkaTime = (System.currentTimeMillis() - currentMillies) / 1000.0
            lastIndex = result.lastIndex
            counter += result.rows.size
            log.info("Antall sykmeldinger som er hentet i dette forsoket:  {} totalt {}, DB time used {}, processing time {}, lastIndex {}, kafkatime {}", result.rows.size, counter, result.databaseTime, result.processingTime, lastIndex, kafkaTime)
            if (result.rows.isEmpty()) {
                log.info("no more sykmelinger in database")
                break
            }
        }
        return counter
    }
}
