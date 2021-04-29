package no.nav.syfo.narmesteleder

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.log

class NarmesteLederFraSyfoServiceService(
    private val databaseOracle: DatabaseInterfaceOracle,
    private val lastIndexSyfoservice: Int,
    private val syfoServiceNarmesteLederKafkaProducer: SyfoServiceNarmesteLederKafkaProducer,
    private val applicationState: ApplicationState
) {
    fun run() {
        val antallNarmesteLederKoblinger =
            Integer.valueOf(databaseOracle.hentAntallNarmesteLederKoblinger().first().antall)
        log.info("Antall narmesteleder-koblinger som finnes i syfoservice: {}", antallNarmesteLederKoblinger)
        val sisteNarmesteLederId = Integer.valueOf(databaseOracle.finnSisteNarmesteLeder())
        log.info("Siste narmesteleder-kobling som finnes i syfoservice: {}", sisteNarmesteLederId)
        var counter = 0
        var lastIndex = lastIndexSyfoservice

        GlobalScope.launch {
            while (applicationState.ready) {
                log.info(
                    "Antall narmesteleder-koblinger som er hentet: {}, lastIndex {}",
                    counter,
                    lastIndex
                )
                delay(10_000)
            }
        }

        while (lastIndex <= sisteNarmesteLederId) {
            val result = databaseOracle.hentNarmesteLederSyfoService(lastIndex = lastIndex, limit = 10_000)
            for (syfoServiceNarmesteLeder in result.rows) {
                syfoServiceNarmesteLederKafkaProducer.publishToKafka(syfoServiceNarmesteLeder)
            }
            lastIndex = result.lastIndex
            counter += result.rows.size
        }
        log.info("Ferdig med alle narmeste ledere, totalt {}", counter)
    }
}
