package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Duration
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.log
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
        var counter = 0
        var counterDuplicates = 0
        while (applicationState.ready) {
            kafkaconsumerReceivedSykmelding.subscribe(
                listOf(
                    sm2013SyfoserviceSykmeldingCleanTopic
                )
            )

            kafkaconsumerReceivedSykmelding.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
                val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(consumerRecord.value())
                if (!validPasientFnr(receivedSykmelding.personNrPasient)) {
                    logNonValidSykmelding(receivedSykmelding)
                } else if (databasePostgres.connection.erSykmeldingsopplysningerLagret(
                        receivedSykmelding.sykmelding.id,
                        convertToMottakid(receivedSykmelding.navLogId)
                    )
                ) {
                    counterDuplicates++
                    if (counterDuplicates % 10000 == 0) {
                        log.info(
                            "10000 duplikater er registrer og vil ikke bli oppdatert", receivedSykmelding.navLogId
                        )
                    }
                } else {
                    databasePostgres.connection.opprettSykmeldingsopplysninger(
                        Sykmeldingsopplysninger(
                            id = receivedSykmelding.sykmelding.id,
                            pasientFnr = receivedSykmelding.personNrPasient,
                            pasientAktoerId = receivedSykmelding.sykmelding.pasientAktoerId,
                            legeFnr = receivedSykmelding.personNrLege,
                            legeAktoerId = receivedSykmelding.sykmelding.behandler.aktoerId,
                            mottakId = convertToMottakid(receivedSykmelding.navLogId),
                            legekontorOrgNr = receivedSykmelding.legekontorOrgNr,
                            legekontorHerId = receivedSykmelding.legekontorHerId,
                            legekontorReshId = receivedSykmelding.legekontorReshId,
                            epjSystemNavn = "SYFOSERVICE",
                            epjSystemVersjon = "1",
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
                            receivedSykmelding.mottattDato, StatusEvent.APEN
                        )
                    )
                    counter++
                    if (counter % 10000 == 0) {
                        log.info("Melding SM2013 lagret i databasen nr: {}", counter)
                    }
                }
            }
        }
    }
}

private fun logNonValidSykmelding(receivedSykmelding: ReceivedSykmelding) {
    log.info(
        "Invalid fnr for sykmelding, id = {}, mottak_id = ",
        receivedSykmelding.sykmelding.id,
        receivedSykmelding.navLogId
    )
}

fun convertToMottakid(mottakid: String): String =
    when (mottakid.length <= 63) {
        true -> mottakid
        else -> {
            log.info("Størrelsen på mottakid er: {}, mottakid: {}", mottakid.length, mottakid)
            mottakid.substring(0, 63)
        }
    }

fun validPasientFnr(pasientFnr: String): Boolean =
    pasientFnr.length == 11
