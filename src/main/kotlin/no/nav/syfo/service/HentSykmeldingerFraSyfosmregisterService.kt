package no.nav.syfo.service

import java.time.LocalDate
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.kafka.BehandlingsutfallKafkaProducer
import no.nav.syfo.kafka.ReceivedSykmeldingKafkaProducer
import no.nav.syfo.log
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.toReceivedSykmelding
import no.nav.syfo.persistering.db.postgres.SykmeldingDbModel
import no.nav.syfo.persistering.db.postgres.getBehandlingsutfall
import no.nav.syfo.persistering.db.postgres.getSykmeldingIds
import no.nav.syfo.persistering.db.postgres.hentAntallSykmeldinger
import no.nav.syfo.persistering.db.postgres.hentSykmeldingerDokumentOgBehandlingsutfall
import no.nav.syfo.persistering.db.postgres.oppdaterBehandlingsutfall

class HentSykmeldingerFraSyfosmregisterService(
    private val receivedSykmeldingKafkaProducer: ReceivedSykmeldingKafkaProducer,
    private val behandlingsutfallKafkaProducer: BehandlingsutfallKafkaProducer,
    private val databasePostgres: DatabaseInterfacePostgres,
    private val lastIndexSyfosmregister: String,
    private val applicationState: ApplicationState
) {

    suspend fun skrivBehandlingsutfallTilTopic() {
        var counter = 0
        var lastMottattDato = LocalDate.of(2019, 9, 26)
        var toDate = LocalDate.of(2019, 12, 9)

        val job = GlobalScope.launch {
            while (applicationState.ready) {
                log.info(
                    "Antall behandlingsutfall skrevet til kafka: {}, lastMottattDato {}",
                    counter,
                    lastMottattDato
                )
                delay(10_000)
            }
        }
        while (lastMottattDato.isBefore(toDate.plusDays(1))) {
            val ids = databasePostgres.connection.getSykmeldingIds(lastMottattDato)
            ids.forEach {
                val behandlingsutfall = databasePostgres.connection.getBehandlingsutfall(it)
                if (behandlingsutfall != null) {
                    behandlingsutfallKafkaProducer.publishToKafka(behandlingsutfall)
                    counter++
                }
            }
            lastMottattDato = lastMottattDato.plusDays(1)
        }
        log.info(
            "Antall behandlingsutfall skrevet til kafka: {}, lastMottattDato {}",
            counter,
            lastMottattDato
        )
        job.cancelAndJoin()
    }

    fun run() {
        val hentantallSykmeldinger = databasePostgres.hentAntallSykmeldinger()
        log.info("Antall sykmeldinger som finnes i databasen:  {}", hentantallSykmeldinger.first().antall)

        var counter = 0
        var counterOppdatertBehandlingsutfall = 0
        var lastMottattDato = LocalDate.parse(lastIndexSyfosmregister)

        GlobalScope.launch {
            while (applicationState.ready) {
                log.info(
                    "Antall sykmeldinger som er hentet: {}, lastMottattDato {}, antall oppdaterte behandlingsutfall: {}",
                    counter,
                    lastMottattDato,
                    counterOppdatertBehandlingsutfall
                )
                delay(30_000)
            }
        }

        while (lastMottattDato.isBefore(LocalDate.now().plusDays(1))) {
            val result = databasePostgres.hentSykmeldingerDokumentOgBehandlingsutfall(lastMottattDato)
            for (sykmelding in result) {
                val receivedSykmelding = toReceivedSykmelding(
                    SykmeldingDbModel(
                        sykmelding.sykmeldingsopplysninger,
                        sykmelding.sykmeldingsdokument
                    )
                )
                receivedSykmeldingKafkaProducer.publishToKafka(receivedSykmelding)
                if (sykmelding.behandlingsutfall != null) {
                    if (sykmelding.behandlingsutfall.behandlingsutfall.status == Status.INVALID && sykmelding.behandlingsutfall.behandlingsutfall.ruleHits.isNotEmpty()) {
                        val ruleHitsMedStatus = ArrayList<RuleInfo>()
                        for (ruleInfo in sykmelding.behandlingsutfall.behandlingsutfall.ruleHits) {
                            if (ruleInfo.ruleStatus == null) {
                                ruleHitsMedStatus.add(ruleInfo.copy(ruleStatus = Status.INVALID))
                            }
                        }
                        if (ruleHitsMedStatus.isNotEmpty()) {
                            val oppdatertValidationResult = sykmelding.behandlingsutfall.behandlingsutfall.copy(ruleHits = ruleHitsMedStatus)
                            val oppdatertBehandlingsutfall = sykmelding.behandlingsutfall.copy(behandlingsutfall = oppdatertValidationResult)
                            counterOppdatertBehandlingsutfall++
                            databasePostgres.oppdaterBehandlingsutfall(oppdatertBehandlingsutfall)
                            behandlingsutfallKafkaProducer.publishToKafka(oppdatertBehandlingsutfall)
                        }
                    } else {
                        behandlingsutfallKafkaProducer.publishToKafka(sykmelding.behandlingsutfall)
                    }
                }
            }
            lastMottattDato = lastMottattDato.plusDays(1)
            counter += result.size
        }
        log.info("Ferdig med alle sykmeldingene, totalt {},  antall oppdaterte behandlingsutfall: {}", counter, counterOppdatertBehandlingsutfall)
    }
}
