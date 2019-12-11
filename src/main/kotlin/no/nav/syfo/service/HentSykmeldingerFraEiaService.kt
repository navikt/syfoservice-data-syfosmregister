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

        val result = databaseOracle.hentSykmeldingerEia()

        for (eiaSykmelding in result.rows) {
            eiaKafkaProducer.publishToKafka(eiaSykmelding)
        }

        counter += result.rows.size
        log.info(
            "Antall sykmeldinger som er hentet i dette forsoket:  {} totalt {}",
            result.rows.size,
            counter
        )
        return counter
    }
}
