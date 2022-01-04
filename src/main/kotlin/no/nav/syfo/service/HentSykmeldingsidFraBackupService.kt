package no.nav.syfo.service

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabasePostgresUtenVault
import no.nav.syfo.kafka.SykmeldingIdKafkaProducer
import no.nav.syfo.log
import no.nav.syfo.persistering.db.postgres.hentAntallSykmeldinger
import no.nav.syfo.persistering.db.postgres.hentSykmeldingsIderUtenBehandlingsutfall
import java.time.LocalDate
import java.time.Month

class HentSykmeldingsidFraBackupService(
    private val sykmeldingIdKafkaProducer: SykmeldingIdKafkaProducer,
    private val databasePostgresUtenVault: DatabasePostgresUtenVault,
    private val lastIndexBackup: String,
    private val applicationState: ApplicationState
) {

    fun run() {
        val hentantallSykmeldinger = databasePostgresUtenVault.hentAntallSykmeldinger()
        log.info("Antall sykmeldinger som finnes i backup-databasen:  {}", hentantallSykmeldinger.first().antall)

        var counter = 0
        var counterSykmeldingerUtenBehandlingsutfall = 0
        var lastMottattDato = LocalDate.parse(lastIndexBackup)

        GlobalScope.launch {
            while (applicationState.ready) {
                log.info(
                    "Antall sykmeldinger som er hentet: {}, lastMottattDato {}, antall relevante uten behandlingsutfall: {}",
                    counter,
                    lastMottattDato,
                    counterSykmeldingerUtenBehandlingsutfall
                )
                delay(30_000)
            }
        }

        while (lastMottattDato.isBefore(LocalDate.of(2019, Month.DECEMBER, 20))) {
            val result = databasePostgresUtenVault.hentSykmeldingsIderUtenBehandlingsutfall(lastMottattDato)
            for (id in result) {
                sykmeldingIdKafkaProducer.publishToKafka(id)
                counterSykmeldingerUtenBehandlingsutfall++
            }
            lastMottattDato = lastMottattDato.plusDays(1)
            counter += result.size
        }
        log.info("Ferdig med alle sykmeldingene, totalt {},  antall relevante uten behandlingsutfall: {}", counter, counterSykmeldingerUtenBehandlingsutfall)
    }
}
