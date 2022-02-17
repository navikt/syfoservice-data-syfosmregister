package no.nav.syfo.papirsykmelding

import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.aksessering.db.oracle.getSykmeldingsDokument
import no.nav.syfo.aksessering.db.oracle.updateDocument
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.kafka.SykmeldingEndringsloggKafkaProducer
import no.nav.syfo.log
import no.nav.syfo.persistering.db.postgres.hentSykmeldingsdokument
import no.nav.syfo.persistering.db.postgres.updateBiDiagnose
import no.nav.syfo.persistering.db.postgres.updateDiagnose
import no.nav.syfo.sm.Diagnosekoder
import no.nav.syfo.sykmelding.api.model.EndreDiagnose

class DiagnoseService(private val syfoserviceDb: DatabaseOracle, private val syfosmRegisterDb: DatabasePostgres, private val endringsloggKafkaProducer: SykmeldingEndringsloggKafkaProducer) {

    fun endreDiagnose(sykmeldingId: String, diagnoseKode: String, system: String) {
        val result = syfoserviceDb.getSykmeldingsDokument(sykmeldingId)
        val sykmeldingsdokument = syfosmRegisterDb.connection.hentSykmeldingsdokument(sykmeldingId)
        if (result.rows.isNotEmpty() && sykmeldingsdokument != null) {
            log.info("updating sykmelding dokument with sykmelding id {}", sykmeldingId)
            endringsloggKafkaProducer.publishToKafka(sykmeldingsdokument)
            val document = result.rows.first()
            if (document != null) {

                val sanitisertSystem = formater(system)
                val santitisertDiagnoseKode = formater(diagnoseKode)

                val diagnose = toDiagnoseType(sanitisertSystem, santitisertDiagnoseKode, sykmeldingId, diagnoseKode)

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

    fun endreBiDiagnose(sykmeldingId: String, diagnoser: List<EndreDiagnose>) {
        val result = syfoserviceDb.getSykmeldingsDokument(sykmeldingId)
        val sykmeldingsdokument = syfosmRegisterDb.connection.hentSykmeldingsdokument(sykmeldingId)
        if (result.rows.isNotEmpty() && sykmeldingsdokument != null) {
            log.info("updating sykmelding dokument with sykmelding id {}", sykmeldingId)
            endringsloggKafkaProducer.publishToKafka(sykmeldingsdokument)
            val document = result.rows.first()
            if (document != null) {
                val diagnoseTyper = diagnoser.map {
                    val sanitisertSystem = formater(it.system)
                    val santitisertDiagnoseKode = formater(it.kode)
                    toDiagnoseType(sanitisertSystem, santitisertDiagnoseKode, sykmeldingId, it.kode)
                }

                val bidiagnoser = HelseOpplysningerArbeidsuforhet.MedisinskVurdering.BiDiagnoser()
                bidiagnoser.diagnosekode.addAll(
                    diagnoseTyper.map {
                        val cv = CV()
                        cv.dn = it.text
                        cv.s = it.oid
                        cv.v = it.code
                        cv
                    }
                )
                document.medisinskVurdering.biDiagnoser = bidiagnoser

                syfoserviceDb.updateDocument(document, sykmeldingId)
                syfosmRegisterDb.updateBiDiagnose(diagnoseTyper, sykmeldingId)
            }
        } else {
            log.info("could not find sykmelding with id {}", sykmeldingId)
        }
    }

    private fun formater(string: String): String {
        return string.replace(".", "")
            .replace(" ", "")
            .replace("-", "")
            .uppercase()
    }

    private fun toDiagnoseType(
        sanitisertSystem: String,
        santitisertDiagnoseKode: String,
        sykmeldingId: String,
        diagnoseKode: String
    ) = when (sanitisertSystem) {
        "ICD10" -> {
            Diagnosekoder.icd10[santitisertDiagnoseKode]
                ?: error("Fant ikke diagnose $santitisertDiagnoseKode i ICD10-kodeverk")
        }
        "ICPC2" -> {
            Diagnosekoder.icpc2[santitisertDiagnoseKode]
                ?: error("Fant ikke diagnose $santitisertDiagnoseKode i ICPC2-kodeverk")
        }
        else -> throw RuntimeException("Could not find correct diagnose when updating sykmeldingId $sykmeldingId, diagnosekode $diagnoseKode, system $sanitisertSystem")
    }
}
