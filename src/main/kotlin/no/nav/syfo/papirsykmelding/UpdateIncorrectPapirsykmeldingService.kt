package no.nav.syfo.papirsykmelding

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.aksessering.db.oracle.getSykmeldingsDokument
import no.nav.syfo.aksessering.db.oracle.updateDocument
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.persistering.db.postgres.getSykmeldingWithEmptyUtdypendeOpplysninger
import no.nav.syfo.persistering.db.postgres.getSykmeldingWithIArbeidIkkeIArbeid
import no.nav.syfo.persistering.db.postgres.updatePrognose
import no.nav.syfo.persistering.db.postgres.updateUtdypendeOpplysninger

class UpdateIncorrectPapirsykmeldingService(private val databaseOracle: DatabaseOracle, private val databasePostgres: DatabasePostgres) {

    private val sykmeldingID = ""

    // OBS: Denne må kun kjøres på sykmeldinger med EN spørsmålsgruppe (6.2). Hvis ikke vil den slette alle bortsett fra første spørsmålsgruppe!!
    fun updateUtdypendeOpplysningerSpmGruppeText() {
        val sykmeldingSyfoService = getSyfoserviceSykmelding(sykmeldingID)
        val newUtdypendOpplysningSyfoService = HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger()
        val oppdatertSpmGruppe = HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger.SpmGruppe()
        oppdatertSpmGruppe.spmGruppeId = sykmeldingSyfoService.utdypendeOpplysninger.spmGruppe.first().spmGruppeId
        oppdatertSpmGruppe.spmSvar.addAll(sykmeldingSyfoService.utdypendeOpplysninger.spmGruppe.first().spmSvar)
        oppdatertSpmGruppe.spmGruppeTekst = "Utdypende opplysninger ved 7/8,17 og 39 uker"
        newUtdypendOpplysningSyfoService.spmGruppe.add(oppdatertSpmGruppe)

        sykmeldingSyfoService.utdypendeOpplysninger = newUtdypendOpplysningSyfoService
        databaseOracle.updateDocument(sykmeldingSyfoService, sykmeldingID)

        log.info("Updated updypende opplysninger for sykmelding $sykmeldingID")
    }

    fun updateUtdypendeOpplysninger() {
        val sickleavesToUpdate = databasePostgres.connection.getSykmeldingWithEmptyUtdypendeOpplysninger()
        log.info("got ${sickleavesToUpdate.size} to check")
        sickleavesToUpdate.forEach {
            val sykmeldingSyfoService = getSyfoserviceSykmelding(it.id)
            val newUtdypendOpplysningSyfoService = HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger()
            newUtdypendOpplysningSyfoService.spmGruppe.addAll(
                sykmeldingSyfoService.utdypendeOpplysninger.spmGruppe.filter { it.spmSvar.size != 0 }
            )
            sykmeldingSyfoService.utdypendeOpplysninger = newUtdypendOpplysningSyfoService
            databaseOracle.updateDocument(sykmeldingSyfoService, it.id)

            val newUtdypendeOpplysninger = it.utdypendeOpplysninger.filter { !it.value.isEmpty() }
            databasePostgres.updateUtdypendeOpplysninger(it.id, newUtdypendeOpplysninger)
            log.info("Updated updypende opplysninger for sykmelding ${it.id}")
        }
    }

    fun updateIArbeidIkkeIAarbeid() {
        val sickleavesToUpdate = databasePostgres.connection.getSykmeldingWithIArbeidIkkeIArbeid()
        log.info("got ${sickleavesToUpdate.size} to check")
        sickleavesToUpdate.forEach {
            if (it.prognose?.erIArbeid != null && it.prognose!!.erIkkeIArbeid != null) {
                log.info("updating sykmelding ${it.id}")
                val arbeidFOM = it.prognose!!.erIArbeid?.arbeidFOM != null
                val ikkeArbeidFom = it.prognose!!.erIkkeIArbeid?.arbeidsforFOM != null
                val sykmeldingSyfoService = getSyfoserviceSykmelding(it.id)
                if (arbeidFOM && ikkeArbeidFom) {
                    log.info("Sykmelding har både erIArbeid.arbeidFom og erIkkeIArbeid.arbeidsforFom ${it.id}")
                } else {
                    var prognose = it.prognose
                    when {
                        arbeidFOM -> {
                            log.info("Sykmelding har arbeidFom satt, sletter erIkkeIArbeid ${it.id}")
                            sykmeldingSyfoService.prognose.erIkkeIArbeid = null
                            prognose = prognose!!.copy(erIkkeIArbeid = null)
                        }
                        ikkeArbeidFom -> {
                            log.info("Sykmelding har ikkeArbeidFom satt, sletter erIArbeid ${it.id}")
                            sykmeldingSyfoService.prognose.erIArbeid = null
                            prognose = prognose!!.copy(erIArbeid = null)
                        }
                        else -> {
                            log.info("Sletter erIArbeid og erIkkeIArbeid ${it.id}")
                            sykmeldingSyfoService.prognose.erIArbeid = null
                            sykmeldingSyfoService.prognose.erIkkeIArbeid = null
                            prognose = prognose!!.copy(erIArbeid = null, erIkkeIArbeid = null)
                        }
                    }
                    databaseOracle.updateDocument(sykmeldingSyfoService, it.id)
                    databasePostgres.updatePrognose(it.id, prognose)
                }
            }
        }
    }

    private fun getSyfoserviceSykmelding(id: String): HelseOpplysningerArbeidsuforhet {
        return databaseOracle.getSykmeldingsDokument(id).rows.first()!!
    }
}
