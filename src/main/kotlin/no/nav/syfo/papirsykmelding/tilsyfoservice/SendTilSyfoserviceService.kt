package no.nav.syfo.papirsykmelding.tilsyfoservice

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.SykmeldingSyfoserviceKafkaProducer
import no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.model.KafkaMessageMetadata
import no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.model.SykmeldingSyfoserviceKafkaMessage
import no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.model.Tilleggsdata
import no.nav.syfo.persistering.db.postgres.hentSykmelding
import no.nav.syfo.utils.extractHelseOpplysningerArbeidsuforhet
import java.time.LocalDateTime
import java.time.LocalTime

class SendTilSyfoserviceService(
    private val sykmeldingSyfoserviceKafkaProducer: SykmeldingSyfoserviceKafkaProducer,
    private val databasePostgres: DatabasePostgres
) {

    val mottakId = "ba0aa2b1-bd9f-4637-a314-7dedca3a0eb9"

    fun start() {
        val sykmelding = databasePostgres.connection.hentSykmelding(mottakId)
        if (sykmelding != null) {
            log.info("sender sykmelding med mottaksid {} til syfoservice", mottakId)

            val sykmeldingId = sykmelding.sykmeldingsopplysninger.id
            val fellesformat = mapSykmeldingDbModelTilFellesformat(sykmelding)
            val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)

            val syfoserviceKafkaMessage = SykmeldingSyfoserviceKafkaMessage(
                metadata = KafkaMessageMetadata(sykmeldingId = sykmeldingId, source = "smregistrering-backend"),
                tilleggsdata = Tilleggsdata(
                    ediLoggId = mottakId,
                    msgId = sykmelding.sykmeldingsdokument!!.sykmelding.msgId,
                    syketilfelleStartDato = extractSyketilfelleStartDato(healthInformation),
                    sykmeldingId = sykmeldingId
                ),
                helseopplysninger = healthInformation
            )

            sykmeldingSyfoserviceKafkaProducer.publishSykmeldingToKafka(sykmeldingId, syfoserviceKafkaMessage)
        } else {
            log.info("could not find sykmelding with mottakid {}", mottakId)
        }
    }

    private fun extractSyketilfelleStartDato(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): LocalDateTime =
        LocalDateTime.of(helseOpplysningerArbeidsuforhet.syketilfelleStartDato, LocalTime.NOON)
}
