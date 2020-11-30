package no.nav.syfo.papirsykmelding.tilsyfoservice

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.SykmeldingSyfoserviceKafkaProducer
import no.nav.syfo.persistering.db.postgres.hentSykmelding
import no.nav.syfo.sykmelding.model.AktivitetIkkeMulig
import no.nav.syfo.sykmelding.model.Periode
import no.nav.syfo.utils.extractHelseOpplysningerArbeidsuforhet

class SendTilSyfoserviceService(
    private val sykmeldingSyfoserviceKafkaProducer: SykmeldingSyfoserviceKafkaProducer,
    private val databasePostgres: DatabasePostgres
) {

    val mottakId = ""

    fun start() {
        val sykmelding = databasePostgres.connection.hentSykmelding(mottakId)
        if (sykmelding != null) {
            log.info("sender sykmelding med mottaksid {} til syfoservice", mottakId)

            val fellesformat = mapSykmeldingDbModelTilFellesformat(sykmelding)
            val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)

            sykmeldingSyfoserviceKafkaProducer.publishSykmeldingToKafka(sykmelding.sykmeldingsopplysninger.id, healthInformation)
        } else {
            log.info("could not find sykmelding with mottakid {}", mottakId)
        }
    }

    fun HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.tilSmregPeriode(): Periode {
        return Periode(
            fom = periodeFOMDato,
            tom = periodeTOMDato,
            aktivitetIkkeMulig = AktivitetIkkeMulig(
                medisinskArsak = null,
                arbeidsrelatertArsak = null),
            avventendeInnspillTilArbeidsgiver = null,
            behandlingsdager = null,
            gradert = null,
            reisetilskudd = false
        )
    }
}
