package no.nav.syfo.papirsykmelding.api

import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CS
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
    fun updatePeriode(sykmeldingId: String, periodeliste: List<Periode>) {
        val result = databaseoracle.getSykmeldingsDokument(sykmeldingId)

        if (result.rows.isNotEmpty()) {
            log.info("Oppdaterer sykmeldingsperiode for id {}", sykmeldingId)
            val document = result.rows.first()
            if (document != null) {
                log.info("Endrer perioder fra ${objectMapper.writeValueAsString(document.aktivitet.periode)}" +
                        " til ${objectMapper.writeValueAsString(periodeliste)} for id $sykmeldingId")

                document.aktivitet.periode.clear()
                document.aktivitet.periode.addAll(periodeliste.map { tilSyfoservicePeriode(it) })

                databaseoracle.updateDocument(document, sykmeldingId)
                databasePostgres.updatePeriode(periodeliste, sykmeldingId)
            }
        } else {
            log.info("Fant ikke sykmelding med id {}", sykmeldingId)
            throw RuntimeException("Fant ikke sykmelding med id $sykmeldingId")
        }
    }

    private fun tilSyfoservicePeriode(periode: Periode): HelseOpplysningerArbeidsuforhet.Aktivitet.Periode {
        if (periode.aktivitetIkkeMulig != null) {
            return HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = periode.fom
                periodeTOMDato = periode.tom
                aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig().apply {
                    medisinskeArsaker = if (periode.aktivitetIkkeMulig.medisinskArsak != null) {
                        ArsakType().apply {
                            beskriv = periode.aktivitetIkkeMulig.medisinskArsak.beskrivelse
                            arsakskode.add(CS())
                        }
                    } else {
                        null
                    }
                    arbeidsplassen = if (periode.aktivitetIkkeMulig.arbeidsrelatertArsak != null) {
                        ArsakType().apply {
                            beskriv = periode.aktivitetIkkeMulig.arbeidsrelatertArsak.beskrivelse
                            arsakskode.add(CS())
                        }
                    } else {
                        null
                    }
                }
                avventendeSykmelding = null
                gradertSykmelding = null
                behandlingsdager = null
                isReisetilskudd = false
            }
        }

        if (periode.gradert != null) {
            return HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = periode.fom
                periodeTOMDato = periode.tom
                aktivitetIkkeMulig = null
                avventendeSykmelding = null
                gradertSykmelding = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.GradertSykmelding().apply {
                    isReisetilskudd = periode.gradert.reisetilskudd
                    sykmeldingsgrad = Integer.valueOf(periode.gradert.grad)
                }
                behandlingsdager = null
                isReisetilskudd = false
            }
        }
        if (!periode.avventendeInnspillTilArbeidsgiver.isNullOrEmpty()) {
            return HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = periode.fom
                periodeTOMDato = periode.tom
                aktivitetIkkeMulig = null
                avventendeSykmelding = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AvventendeSykmelding().apply {
                    innspillTilArbeidsgiver = periode.avventendeInnspillTilArbeidsgiver
                }
                gradertSykmelding = null
                behandlingsdager = null
                isReisetilskudd = false
            }
        }
        if (periode.behandlingsdager != null) {
            return HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = periode.fom
                periodeTOMDato = periode.tom
                aktivitetIkkeMulig = null
                avventendeSykmelding = null
                gradertSykmelding = null
                behandlingsdager = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.Behandlingsdager().apply {
                    antallBehandlingsdagerUke = periode.behandlingsdager
                }
                isReisetilskudd = false
            }
        }
        if (periode.reisetilskudd) {
            return HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                periodeFOMDato = periode.fom
                periodeTOMDato = periode.tom
                aktivitetIkkeMulig = null
                avventendeSykmelding = null
                gradertSykmelding = null
                behandlingsdager = null
                isReisetilskudd = true
            }
        }
        throw IllegalStateException("Har mottatt periode som ikke er av kjent type")
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
