package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import java.time.Duration
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.StatusEvent
import no.nav.syfo.model.SykmeldingStatusEvent
import no.nav.syfo.model.Sykmeldingsdokument
import no.nav.syfo.model.Sykmeldingsopplysninger
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.erSykmeldingsopplysningerLagret
import no.nav.syfo.persistering.db.postgres.opprettSykmeldingsdokument
import no.nav.syfo.persistering.db.postgres.opprettSykmeldingsopplysninger
import no.nav.syfo.persistering.db.postgres.registerStatus
import org.apache.kafka.clients.consumer.KafkaConsumer

class SkrivTilSyfosmRegisterService(
    private val kafkaconsumerReceivedSykmelding: KafkaConsumer<String, String>,
    private val databasePostgres: DatabaseInterfacePostgres,
    private val sm2013SyfoserviceSykmeldingCleanTopic: String,
    private val applicationState: ApplicationState
) {

    fun run() {
        while (applicationState.ready) {
        kafkaconsumerReceivedSykmelding.subscribe(
            listOf(
                sm2013SyfoserviceSykmeldingCleanTopic
            )
        )

        kafkaconsumerReceivedSykmelding.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
            val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(consumerRecord.value())
            // TODO check if already stored in database
            // Then insert into DB
            if (databasePostgres.connection.erSykmeldingsopplysningerLagret(receivedSykmelding.navLogId)) {
                log.info("Sykmelding med id {} allerede lagret i databasen", receivedSykmelding.navLogId
                )
            } else {
                databasePostgres.connection.opprettSykmeldingsopplysninger(
                    Sykmeldingsopplysninger(
                        id = receivedSykmelding.sykmelding.id,
                        pasientFnr = receivedSykmelding.personNrPasient,
                        pasientAktoerId = receivedSykmelding.sykmelding.pasientAktoerId,
                        legeFnr = receivedSykmelding.personNrLege,
                        legeAktoerId = receivedSykmelding.sykmelding.behandler.aktoerId,
                        mottakId = receivedSykmelding.navLogId,
                        legekontorOrgNr = receivedSykmelding.legekontorOrgNr,
                        legekontorHerId = receivedSykmelding.legekontorHerId,
                        legekontorReshId = receivedSykmelding.legekontorReshId,
                        epjSystemNavn = receivedSykmelding.sykmelding.avsenderSystem.navn,
                        epjSystemVersjon = receivedSykmelding.sykmelding.avsenderSystem.versjon,
                        mottattTidspunkt = receivedSykmelding.mottattDato,
                        tssid = receivedSykmelding.tssid
                    )
                )
                databasePostgres.connection.opprettSykmeldingsdokument(
                    Sykmeldingsdokument(
                        id = receivedSykmelding.sykmelding.id,
                        sykmelding = receivedSykmelding.sykmelding
                    )
                )

                databasePostgres.registerStatus(
                    SykmeldingStatusEvent(
                    receivedSykmelding.sykmelding.id,
                    receivedSykmelding.mottattDato, StatusEvent.APEN)
                )

                log.info("Sykmelding SM2013 lagret i databasen")
            }


        }
    }
    }
}
