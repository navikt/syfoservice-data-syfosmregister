package no.nav.syfo.papirsykmelding

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.aksessering.db.oracle.getSykmeldingsDokument
import no.nav.syfo.aksessering.db.oracle.updateDocument
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.persistering.db.postgres.updatePeriode
import no.nav.syfo.sykmelding.model.Periode

class PeriodeService(private val databaseoracle: DatabaseOracle, private val databasePostgres: DatabasePostgres) {

    val sykmeldingId = ""

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
                // document.aktivitet.periode.first().periodeFOMDato = LocalDate.of(2020, 12, 8)
                // document.aktivitet.periode.first().periodeTOMDato = LocalDate.of(2021, 1, 16)
                document.aktivitet.periode.first().aktivitetIkkeMulig = null

                val periode = document.aktivitet.periode.first().tilSmregPeriode()

                databaseoracle.updateDocument(document, sykmeldingId)
                databasePostgres.updatePeriode(listOf(periode), sykmeldingId)
            }
        } else {
            log.info("could not find sykmelding with id {}", sykmeldingId)
        }
    }

    private fun HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.tilSmregPeriode(): Periode {
        return Periode(
            fom = periodeFOMDato,
            tom = periodeTOMDato,
            aktivitetIkkeMulig = null,
            avventendeInnspillTilArbeidsgiver = null,
            behandlingsdager = null,
            gradert = null,
            reisetilskudd = true
        )
    }
}
