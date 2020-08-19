package no.nav.syfo.sparenaproxy

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseSparenaproxyPostgres
import no.nav.syfo.log
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class Arena39UkerService(
    private val applicationState: ApplicationState,
    private val databasePostgres: DatabaseSparenaproxyPostgres,
    private val lastOpprettetDato: OffsetDateTime
) {
    fun run() {
        var counter39Ukersmeldinger = 0
        var lastOpprettetDato = lastOpprettetDato
        val loggingJob = GlobalScope.launch {
            while (applicationState.ready) {
                log.info(
                    "Antall 39-ukersmeldinger som er opprettet: {}, lastOpprettetDato {}",
                    counter39Ukersmeldinger,
                    lastOpprettetDato
                )
                delay(10_000)
            }
        }
        while (lastOpprettetDato.isBefore(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1))) {
            val dbmodels = databasePostgres.getPlanlagte8Ukersmeldinger(lastOpprettetDato)

            dbmodels.forEach {
                try {
                    if (!databasePostgres.planlagt39UkersmeldingFinnes(it.fnr, it.startdato)) {
                        databasePostgres.lagrePlanlagtMelding(
                            PlanlagtMeldingDbModel(
                                id = UUID.randomUUID(),
                                fnr = it.fnr,
                                startdato = it.startdato,
                                type = BREV_39_UKER_TYPE,
                                opprettet = OffsetDateTime.now(ZoneOffset.UTC),
                                sendes = it.startdato.plusWeeks(39).atStartOfDay().atZone(ZoneId.systemDefault()).withZoneSameInstant(
                                    ZoneOffset.UTC
                                ).toOffsetDateTime()
                            )
                        )
                    }
                } catch (ex: Exception) {
                    log.error(
                        "Noe gikk galt med planlagt melding {}, på dato {}, {}",
                        it.id,
                        lastOpprettetDato,
                        ex.message
                    )
                    throw ex
                }
                counter39Ukersmeldinger++
            }
            lastOpprettetDato = lastOpprettetDato.plusDays(1)
        }
        log.info(
            "Ferdig med alle 39-ukersmeldingene, totalt {}, siste dato {}",
            counter39Ukersmeldinger,
            lastOpprettetDato
        )
        runBlocking {
            loggingJob.cancelAndJoin()
        }
    }
}
