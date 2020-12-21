package no.nav.syfo.papirsykmelding

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.aksessering.db.oracle.getSykmeldingsDokument
import no.nav.syfo.aksessering.db.oracle.updateDocument
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.persistering.db.postgres.updatePeriode
import no.nav.syfo.sykmelding.model.Gradert
import no.nav.syfo.sykmelding.model.Periode

class GradService(private val databaseoracle: DatabaseOracle, private val databasePostgres: DatabasePostgres) {

    val sykmeldingId = "0f2c5260-082d-48f9-8fac-660846981bb4"

    fun start() {
        val result = databaseoracle.getSykmeldingsDokument(sykmeldingId)

        if (result.rows.isNotEmpty()) {
            log.info("updating sykmelding dokument with sykmelding id {}", sykmeldingId)
            val document = result.rows.first()
            if (document != null) {
                if (document.aktivitet.periode.size != 1) {
                    log.error("Sykmeldingen har mer enn en periode!")
                    throw IllegalStateException("Sykmeldingen har mer enn en periode!")
                }
                val gradertSykmelding = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.GradertSykmelding()
                gradertSykmelding.isReisetilskudd = false
                gradertSykmelding.sykmeldingsgrad = 50
                document.aktivitet.periode.first().gradertSykmelding = gradertSykmelding
                document.aktivitet.periode.first().aktivitetIkkeMulig = null

                val periode = document.aktivitet.periode.first().tilSmregPeriodeGradert()

                databaseoracle.updateDocument(document, sykmeldingId)
                databasePostgres.updatePeriode(listOf(periode), sykmeldingId)
            }
        } else {
            log.info("could not find sykmelding with id {}", sykmeldingId)
        }
    }

    private fun HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.tilSmregPeriodeGradert(): Periode {
        return Periode(
            fom = periodeFOMDato,
            tom = periodeTOMDato,
            aktivitetIkkeMulig = null,
            avventendeInnspillTilArbeidsgiver = null,
            behandlingsdager = null,
            gradert = Gradert(
                reisetilskudd = false,
                grad = 50
            ),
            reisetilskudd = false
        )
    }
}
