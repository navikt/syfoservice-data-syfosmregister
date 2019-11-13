package no.nav.syfo.service

import no.nav.syfo.aksessering.db.oracle.hentAntallSykmeldingerEia
import no.nav.syfo.aksessering.db.oracle.hentSykmeldingerEia
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.kafka.EiaSykmeldingKafkaProducer
import no.nav.syfo.log

class HentSykmeldingerFraEiaService(
    private val eiaKafkaProducer: EiaSykmeldingKafkaProducer,
    private val databaseOracle: DatabaseInterfaceOracle,
    private val batchSize: Int
) {

    fun run(): Int {
        val hentantallSykmeldinger = databaseOracle.hentAntallSykmeldingerEia()
        log.info("Antall sykmeldinger som finnes i databasen:  {}", hentantallSykmeldinger.first().antall)

        val hentSykmeldingerEia = databaseOracle.hentSykmeldingerEia()
        log.info("Mapper over sykmeldinger som finnes i databasen:  {}", hentSykmeldingerEia.size)

        var counter = 0

        return counter
    }
}
