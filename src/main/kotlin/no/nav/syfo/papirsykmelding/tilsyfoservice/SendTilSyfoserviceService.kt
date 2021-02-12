package no.nav.syfo.papirsykmelding.tilsyfoservice

import java.time.LocalDateTime
import java.time.LocalTime
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.SykmeldingSyfoserviceKafkaProducer
import no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.model.KafkaMessageMetadata
import no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.model.SykmeldingSyfoserviceKafkaMessage
import no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.model.Tilleggsdata
import no.nav.syfo.persistering.db.postgres.hentSykmeldingMedId
import no.nav.syfo.utils.extractHelseOpplysningerArbeidsuforhet

class SendTilSyfoserviceService(
    private val sykmeldingSyfoserviceKafkaProducer: SykmeldingSyfoserviceKafkaProducer,
    private val databasePostgres: DatabasePostgres
) {

    fun sendTilSyfoservice(sykmeldingId: String) {
        val sykmelding = databasePostgres.connection.hentSykmeldingMedId(sykmeldingId)
        if (sykmelding != null) {
            log.info("sender sykmelding med sykmeldingId {} til syfoservice", sykmeldingId)

            val sykmeldingId = sykmelding.sykmeldingsopplysninger.id
            val fellesformat = mapSykmeldingDbModelTilFellesformat(sykmelding)
            val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)

            val syfoserviceKafkaMessage = SykmeldingSyfoserviceKafkaMessage(
                    metadata = KafkaMessageMetadata(sykmeldingId = sykmeldingId, source = "smregistrering-backend"),
                    tilleggsdata = Tilleggsdata(
                            ediLoggId = sykmelding.sykmeldingsopplysninger.mottakId,
                            msgId = sykmelding.sykmeldingsdokument!!.sykmelding.msgId,
                            syketilfelleStartDato = extractSyketilfelleStartDato(healthInformation),
                            sykmeldingId = sykmeldingId
                    ),
                    helseopplysninger = healthInformation
            )

            sykmeldingSyfoserviceKafkaProducer.publishSykmeldingToKafka(sykmeldingId, syfoserviceKafkaMessage)

        } else {
            log.info("could not find sykmelding with sykmeldingId {}", sykmeldingId)
        }
    }

    private fun extractSyketilfelleStartDato(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): LocalDateTime =
        LocalDateTime.of(helseOpplysningerArbeidsuforhet.syketilfelleStartDato, LocalTime.NOON)
}
