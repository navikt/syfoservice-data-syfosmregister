package no.nav.syfo.papirsykmelding

import no.nav.syfo.aksessering.db.oracle.getSykmeldingsDokument
import no.nav.syfo.aksessering.db.oracle.updateDocument
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.model.toMap
import no.nav.syfo.persistering.db.postgres.updateUtdypendeOpplysninger

class SlettInformasjonService(private val databaseoracle: DatabaseOracle, private val databasePostgres: DatabasePostgres) {

    val sykmeldingId = ""
    val nySvartekst = "Tekst slettet pga feil tekst fra lege. Se sykmelding som erstatter denne for korrekt informasjon"

    fun start() {
        val result = databaseoracle.getSykmeldingsDokument(sykmeldingId)

        if (result.rows.isNotEmpty()) {
            log.info("updating sykmelding dokument with sykmelding id {}", sykmeldingId)
            val document = result.rows.first()
            if (document != null) {
                val utdypendeOpplysning64 = document.utdypendeOpplysninger.spmGruppe.find { it.spmGruppeId == "6.4" }
                if (utdypendeOpplysning64 == null) {
                    log.error("Fant ikke utdypende opplysning 6.4!")
                    throw IllegalStateException("Fant ikke utdypende opplysning 6.4!")
                } else {
                    val utdypendeOpplysning641 = utdypendeOpplysning64.spmSvar.find { it.spmId == "6.4.1" }
                    if (utdypendeOpplysning641 == null) {
                        log.error("Fant ikke utdypende opplysning 6.4.1!")
                        throw IllegalStateException("Fant ikke utdypende opplysning 6.4.1!")
                    } else {
                        utdypendeOpplysning641.svarTekst = nySvartekst

                        databaseoracle.updateDocument(document, sykmeldingId)
                        databasePostgres.updateUtdypendeOpplysninger(sykmeldingId, document.utdypendeOpplysninger.toMap())
                    }
                }
            }
        } else {
            log.info("could not find sykmelding with id {}", sykmeldingId)
        }
    }
}
