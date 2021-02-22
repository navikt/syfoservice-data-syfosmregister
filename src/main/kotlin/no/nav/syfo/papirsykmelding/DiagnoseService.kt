package no.nav.syfo.papirsykmelding

import no.nav.syfo.aksessering.db.oracle.getSykmeldingsDokument
import no.nav.syfo.aksessering.db.oracle.updateDocument
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.persistering.db.postgres.updateDiagnose
import no.nav.syfo.sm.Diagnosekoder

class DiagnoseService(private val syfoserviceDb: DatabaseOracle, private val syfosmRegisterDb: DatabasePostgres) {

    fun endreDiagnose(sykmeldingId: String, diagnoseKode: String, system: String) {
        val result = syfoserviceDb.getSykmeldingsDokument(sykmeldingId)

        if (result.rows.isNotEmpty()) {
            log.info("updating sykmelding dokument with sykmelding id {}", sykmeldingId)
            val document = result.rows.first()
            if (document != null) {

                val sanitisertSystem = formater(system)
                val santitisertDiagnoseKode = formater(diagnoseKode)

                val diagnose = when (sanitisertSystem) {
                    "ICD10" -> {
                        Diagnosekoder.icd10[santitisertDiagnoseKode] ?: error("Fant ikke diagnose $santitisertDiagnoseKode i ICD10-kodeverk")
                    }
                    "ICPC2" -> {
                        Diagnosekoder.icpc2[santitisertDiagnoseKode] ?: error("Fant ikke diagnose $santitisertDiagnoseKode i ICPC2-kodeverk")
                    }
                    else -> throw RuntimeException("Could not find correct diagnose when updating sykmeldingId $sykmeldingId, diagnosekode $diagnoseKode, system $sanitisertSystem")
                }

                document.medisinskVurdering.hovedDiagnose.diagnosekode.s = diagnose.oid
                document.medisinskVurdering.hovedDiagnose.diagnosekode.v = diagnose.code
                document.medisinskVurdering.hovedDiagnose.diagnosekode.dn = diagnose.text

                syfoserviceDb.updateDocument(document, sykmeldingId)
                syfosmRegisterDb.updateDiagnose(diagnose, sykmeldingId)
            }
        } else {
            log.info("could not find sykmelding with id {}", sykmeldingId)
        }
    }

    private fun formater(string: String): String {
        return string.replace(".", "")
                .replace(" ", "")
                .replace("-", "")
                .toUpperCase()
    }
}
