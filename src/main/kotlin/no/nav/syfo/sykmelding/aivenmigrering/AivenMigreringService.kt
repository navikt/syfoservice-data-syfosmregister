package no.nav.syfo.sykmelding.aivenmigrering

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.BehandlerAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.KontaktMedPasientAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.PrognoseAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.SykmeldingsperiodeDTO
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class AivenMigreringService(
    private val sykmeldingKafkaConsumer: KafkaConsumer<String, SykmeldingV1KafkaMessage>,
    private val sykmeldingV2KafkaProducer: SykmeldingV2KafkaProducer,
    private val topics: Map<String, String>,
    private val applicationState: ApplicationState,
    private val environment: Environment
) {

    fun start() {
        var counterMottatt = 0
        var counterSendt = 0
        var counterBekreftet = 0
        GlobalScope.launch {
            while (applicationState.ready) {
                log.info(
                    "Antall meldinger som er lest. Sendt: $counterSendt, bekreftet: $counterBekreftet, mottatt: $counterMottatt"
                )
                delay(60_000)
            }
        }
        sykmeldingKafkaConsumer.subscribe(topics.keys)
        log.info("Started consuming topics")
        while (applicationState.ready) {
            sykmeldingKafkaConsumer.poll(Duration.ofSeconds(1)).forEach {
                sykmeldingV2KafkaProducer.sendSykmelding(it.value()?.tilNyttFormat(), it.key(), topics[it.topic()]!!)
                when (it.topic()) {
                    environment.mottattSykmeldingTopic -> counterMottatt++
                    environment.sendSykmeldingTopic -> counterSendt++
                    environment.bekreftSykmeldingKafkaTopic -> counterBekreftet++
                }
            }
        }
    }

    private fun SykmeldingV1KafkaMessage.tilNyttFormat(): SykmeldingV2KafkaMessage {
        return SykmeldingV2KafkaMessage(
            sykmelding = ArbeidsgiverSykmelding(
                id = sykmelding.id,
                mottattTidspunkt = sykmelding.mottattTidspunkt,
                syketilfelleStartDato = sykmelding.syketilfelleStartDato,
                behandletTidspunkt = sykmelding.behandletTidspunkt,
                arbeidsgiver = ArbeidsgiverAGDTO(
                    navn = sykmelding.arbeidsgiver.navn,
                    yrkesbetegnelse = null
                ),
                sykmeldingsperioder = sykmelding.sykmeldingsperioder.map { it.toSykmeldingsperiodeAG() },
                prognose = sykmelding.prognose?.let {
                    PrognoseAGDTO(
                        arbeidsforEtterPeriode = it.arbeidsforEtterPeriode,
                        hensynArbeidsplassen = it.hensynArbeidsplassen
                    )
                },
                tiltakArbeidsplassen = sykmelding.tiltakArbeidsplassen,
                meldingTilArbeidsgiver = sykmelding.meldingTilArbeidsgiver,
                kontaktMedPasient = KontaktMedPasientAGDTO(
                    kontaktDato = sykmelding.kontaktMedPasient.kontaktDato
                ),
                behandler = BehandlerAGDTO(
                    fornavn = sykmelding.behandler.fornavn,
                    mellomnavn = sykmelding.behandler.mellomnavn,
                    etternavn = sykmelding.behandler.etternavn,
                    hpr = sykmelding.behandler.hpr,
                    adresse = sykmelding.behandler.adresse,
                    tlf = sykmelding.behandler.tlf
                ),
                egenmeldt = sykmelding.egenmeldt,
                papirsykmelding = sykmelding.papirsykmelding,
                harRedusertArbeidsgiverperiode = sykmelding.harRedusertArbeidsgiverperiode,
                merknader = sykmelding.merknader
            ),
            kafkaMetadata = kafkaMetadata.copy(source = "macgyver-${kafkaMetadata.source}"),
            event = event
        )
    }

    private fun SykmeldingsperiodeDTO.toSykmeldingsperiodeAG(): SykmeldingsperiodeAGDTO {
        return SykmeldingsperiodeAGDTO(
            fom = fom,
            tom = tom,
            gradert = gradert,
            behandlingsdager = behandlingsdager,
            innspillTilArbeidsgiver = innspillTilArbeidsgiver,
            type = type,
            aktivitetIkkeMulig = aktivitetIkkeMulig?.let { AktivitetIkkeMuligAGDTO(arbeidsrelatertArsak = it.arbeidsrelatertArsak) },
            reisetilskudd = reisetilskudd
        )
    }
}
