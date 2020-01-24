package no.nav.syfo.service

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.kafka.BehandlingsutfallKafkaProducer
import no.nav.syfo.kafka.ReceivedSykmeldingKafkaProducer
import no.nav.syfo.log
import no.nav.syfo.model.toReceivedSykmelding
import no.nav.syfo.persistering.db.postgres.SykmeldingDbModel
import no.nav.syfo.persistering.db.postgres.hentAntallSykmeldinger
import no.nav.syfo.persistering.db.postgres.hentSykmeldingerDokumentOgBehandlingsutfall
import java.time.LocalDate

class HentSykmeldingerFraSyfosmregisterService(
    private val receivedSykmeldingKafkaProducer: ReceivedSykmeldingKafkaProducer,
    private val behandlingsutfallKafkaProducer: BehandlingsutfallKafkaProducer,
    private val databasePostgres: DatabaseInterfacePostgres,
    private val lastIndexSyfosmregister: String,
    private val applicationState: ApplicationState
) {

    fun run() {
        val hentantallSykmeldinger = databasePostgres.hentAntallSykmeldinger()
        log.info("Antall sykmeldinger som finnes i databasen:  {}", hentantallSykmeldinger.first().antall)

        var counter = 0
        var lastMottattDato = LocalDate.parse(lastIndexSyfosmregister)

        GlobalScope.launch {
            while (applicationState.ready) {
                log.info(
                    "Antall sykmeldinger som er hentet: {}, lastMottattDato {}",
                    counter,
                    lastMottattDato
                )
                delay(120_000)
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
                    behandlingsutfallKafkaProducer.publishToKafka(sykmelding.behandlingsutfall)
                }
            }
            lastMottattDato = lastMottattDato.plusDays(1)
            counter += result.size
        }
    }
}
