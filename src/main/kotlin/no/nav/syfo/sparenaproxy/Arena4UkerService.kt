package no.nav.syfo.sparenaproxy

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseSparenaproxyPostgres
import no.nav.syfo.log
import java.time.LocalDate

class Arena4UkerService(
    private val applicationState: ApplicationState,
    private val databasePostgres: DatabaseSparenaproxyPostgres,
    private val lastOpprettetDato: OffsetDateTime
) {
    fun run() {
        var counter4Ukersmeldinger = 0
        var lastOpprettetDato = lastOpprettetDato
        val loggingJob = GlobalScope.launch {
            while (applicationState.ready) {
                log.info(
                    "Antall 4-ukersmeldinger som er opprettet: {}, lastOpprettetDato {}",
                    counter4Ukersmeldinger,
                    lastOpprettetDato
                )
                delay(10_000)
            }
        }
        while (lastOpprettetDato.isBefore(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1))) {
            val dbmodels = databasePostgres.getPlanlagte8Ukersmeldinger(lastOpprettetDato)

            dbmodels.forEach {
                try {
                    if (!databasePostgres.planlagt4UkersmeldingFinnes(it.fnr, it.startdato)) {
                        databasePostgres.lagrePlanlagtMelding(
                            PlanlagtMeldingDbModel(
                                id = UUID.randomUUID(),
                                fnr = it.fnr,
                                startdato = it.startdato,
                                type = BREV_4_UKER_TYPE,
                                opprettet = OffsetDateTime.now(ZoneOffset.UTC),
                                sendes = it.startdato.plusWeeks(4).atStartOfDay().atZone(ZoneId.systemDefault())
                                    .withZoneSameInstant(
                                        ZoneOffset.UTC
                                    ).toOffsetDateTime(),
                                avbrutt = setAvbruttTimestamp(it)
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
                counter4Ukersmeldinger++
            }
            lastOpprettetDato = lastOpprettetDato.plusDays(1)
        }
        log.info(
            "Ferdig med alle 4-ukersmeldingene, totalt {}, siste dato {}",
            counter4Ukersmeldinger,
            lastOpprettetDato
        )
        runBlocking {
            loggingJob.cancelAndJoin()
        }
    }

    private fun setAvbruttTimestamp(it: PlanlagtMeldingDbModel) =
        if (it.startdato.isAfter(LocalDate.now().minusWeeks(6))) null else OffsetDateTime.now(ZoneOffset.UTC)
}
