package no.nav.syfo.service

import no.nav.syfo.aksessering.db.oracle.hentAntallSykmeldingerEia
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.kafka.RecivedSykmeldingKafkaProducer
import no.nav.syfo.log

class HentSykmeldingerFraEiaService(
    private val recivedSykmeldingKafkaProducer: RecivedSykmeldingKafkaProducer,
    private val databaseOracle: DatabaseInterfaceOracle,
    private val batchSize: Int
) {

    fun run(): Int {
        val hentantallSykmeldinger = databaseOracle.hentAntallSykmeldingerEia()
        log.info("Antall sykmeldinger som finnes i databasen:  {}", hentantallSykmeldinger.first().antall)

        var lastIndex = 0
        var counter = 0

        return counter
    }
}
