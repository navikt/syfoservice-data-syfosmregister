package no.nav.syfo.papirsykmelding

import no.nav.syfo.aksessering.db.oracle.getSykmeldingsDokument
import no.nav.syfo.aksessering.db.oracle.updateDiagnose
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.persistering.db.postgres.updateDiagnose
import no.nav.syfo.sm.Diagnosekoder

class DiagnoseService(private val databaseoracle: DatabaseOracle, private val databasePostgres: DatabasePostgres) {

    val sykmeldingId = "5770aba8-8289-4dd7-8e6b-6fe5394814a2"
    val correctSystem = Diagnosekoder.ICPC2_CODE

    fun start() {
        val result = databaseoracle.getSykmeldingsDokument(sykmeldingId)

        if (result.rows.isNotEmpty()) {
            log.info("updating sykmelding dokument with sykmelding id {}", sykmeldingId)
            val document = result.rows.first()
            if (document != null) {
                val diagnoseKode = document.medisinskVurdering.hovedDiagnose.diagnosekode.v
                val diagnose = when (correctSystem) {
                    Diagnosekoder.ICD10_CODE -> Diagnosekoder.icd10[diagnoseKode] ?: error("Could not find diagnose")
                    Diagnosekoder.ICPC2_CODE -> Diagnosekoder.icpc2[diagnoseKode] ?: error("Could not find diagnose")
                    else -> throw RuntimeException("Could not find correct diagnose")
                }
                document.medisinskVurdering.hovedDiagnose.diagnosekode.s = diagnose.oid
                document.medisinskVurdering.hovedDiagnose.diagnosekode.v = diagnose.code
                document.medisinskVurdering.hovedDiagnose.diagnosekode.dn = diagnose.text

                databaseoracle.updateDiagnose(document, sykmeldingId)
                databasePostgres.updateDiagnose(diagnose, sykmeldingId)
            }
        } else {
            log.info("could not find sykmelding with id {}", sykmeldingId)
        }
    }
}