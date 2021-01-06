package no.nav.syfo.papirsykmelding.api

import java.time.LocalDate
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.aksessering.db.oracle.getSykmeldingsDokument
import no.nav.syfo.aksessering.db.oracle.updateDocument
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.updatePeriode
import no.nav.syfo.sykmelding.model.AktivitetIkkeMulig
import no.nav.syfo.sykmelding.model.Periode

class UpdatePeriodeService(
    private val databaseoracle: DatabaseOracle,
    private val databasePostgres: DatabasePostgres
) {
    fun updatePeriode(sykmeldingId: String, fom: LocalDate, tom: LocalDate) {
        val result = databaseoracle.getSykmeldingsDokument(sykmeldingId)

        if (result.rows.isNotEmpty()) {
            log.info("Oppdaterer sykmeldingsperiode for id {}", sykmeldingId)
            val document = result.rows.first()
            if (document != null) {
                if (document.aktivitet.periode.size != 1) {
                    log.error("Sykmeldingen har mer enn en periode!")
                    throw IllegalStateException("Sykmeldingen har mer enn en periode!")
                }
                log.info("Endrer periode fra fom: ${objectMapper.writeValueAsString(document.aktivitet.periode.first().periodeFOMDato)}, tom: ${objectMapper.writeValueAsString(document.aktivitet.periode.first().periodeTOMDato)} for id $sykmeldingId")
                document.aktivitet.periode.first().periodeFOMDato = fom
                document.aktivitet.periode.first().periodeTOMDato = tom

                val periode = document.aktivitet.periode.first().tilSmregPeriode()

                databaseoracle.updateDocument(document, sykmeldingId)
                databasePostgres.updatePeriode(listOf(periode), sykmeldingId)
            }
        } else {
            log.info("Fant ikke sykmelding med id {}", sykmeldingId)
            throw RuntimeException("Fant ikke sykmelding med id $sykmeldingId")
        }
    }

    private fun HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.tilSmregPeriode(): Periode {
        return Periode(
            fom = periodeFOMDato,
            tom = periodeTOMDato,
            aktivitetIkkeMulig = AktivitetIkkeMulig(
                medisinskArsak = null,
                arbeidsrelatertArsak = null
            ),
            avventendeInnspillTilArbeidsgiver = null,
            behandlingsdager = null,
            gradert = null,
            reisetilskudd = false
        )
    }
}
