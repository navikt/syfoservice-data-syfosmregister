package no.nav.syfo.papirsykmelding.tilsyfoservice

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLCS
import no.nav.helse.msgHead.XMLCV
import no.nav.helse.msgHead.XMLDocument
import no.nav.helse.msgHead.XMLHealthcareProfessional
import no.nav.helse.msgHead.XMLIdent
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLMsgInfo
import no.nav.helse.msgHead.XMLOrganisation
import no.nav.helse.msgHead.XMLReceiver
import no.nav.helse.msgHead.XMLRefDoc
import no.nav.helse.msgHead.XMLSender
import no.nav.helse.sm2013.Address
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.DynaSvarType
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.Ident
import no.nav.helse.sm2013.NavnType
import no.nav.helse.sm2013.TeleCom
import no.nav.helse.sm2013.URL
import no.nav.syfo.log
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.SporsmalSvar
import no.nav.syfo.persistering.db.postgres.SykmeldingDbModel

fun mapSykmeldingDbModelTilFellesformat(
    sykmeldingDbModel: SykmeldingDbModel
): XMLEIFellesformat {
    if (sykmeldingDbModel.sykmeldingsdokument?.sykmelding == null) {
        throw IllegalStateException("Kan ikke behandle sykmelding uten dokument!")
    } else {
        return XMLEIFellesformat().apply {
            any.add(
                XMLMsgHead().apply {
                    msgInfo = XMLMsgInfo().apply {
                        type = XMLCS().apply {
                            dn = "Medisinsk vurdering av arbeidsmulighet ved sykdom, sykmelding"
                            v = "SYKMELD"
                        }
                        miGversion = "v1.2 2006-05-24"
                        genDate = sykmeldingDbModel.sykmeldingsdokument.sykmelding.signaturDato
                        msgId = sykmeldingDbModel.sykmeldingsdokument.sykmelding.msgId
                        ack = XMLCS().apply {
                            dn = "Ja"
                            v = "J"
                        }
                        sender = XMLSender().apply {
                            comMethod = XMLCS().apply {
                                dn = "EDI"
                                v = "EDI"
                            }
                            organisation = XMLOrganisation().apply {
                                healthcareProfessional = XMLHealthcareProfessional().apply {
                                    givenName = sykmeldingDbModel.sykmeldingsdokument.sykmelding.behandler.fornavn
                                    middleName = sykmeldingDbModel.sykmeldingsdokument.sykmelding.behandler.mellomnavn
                                    familyName = sykmeldingDbModel.sykmeldingsdokument.sykmelding.behandler.etternavn
                                    ident.addAll(
                                        listOf(
                                            XMLIdent().apply {
                                                id = sykmeldingDbModel.sykmeldingsdokument.sykmelding.behandler.hpr
                                                typeId = XMLCV().apply {
                                                    dn = "HPR-nummer"
                                                    s = "6.87.654.3.21.9.8.7.6543.2198"
                                                    v = "HPR"
                                                }
                                            },
                                            XMLIdent().apply {
                                                id = sykmeldingDbModel.sykmeldingsopplysninger.legeFnr
                                                typeId = XMLCV().apply {
                                                    dn = "Fødselsnummer"
                                                    s = "2.16.578.1.12.4.1.1.8327"
                                                    v = "FNR"
                                                }
                                            }
                                        )
                                    )
                                }
                            }
                        }
                        receiver = XMLReceiver().apply {
                            comMethod = XMLCS().apply {
                                dn = "EDI"
                                v = "EDI"
                            }
                            organisation = XMLOrganisation().apply {
                                organisationName = "NAV"
                                ident.addAll(
                                    listOf(
                                        XMLIdent().apply {
                                            id = "79768"
                                            typeId = XMLCV().apply {
                                                dn = "Identifikator fra Helsetjenesteenhetsregisteret (HER-id)"
                                                s = "2.16.578.1.12.4.1.1.9051"
                                                v = "HER"
                                            }
                                        },
                                        XMLIdent().apply {
                                            id = "889640782"
                                            typeId = XMLCV().apply {
                                                dn = "Organisasjonsnummeret i Enhetsregister (Brønøysund)"
                                                s = "2.16.578.1.12.4.1.1.9051"
                                                v = "ENH"
                                            }
                                        }
                                    )
                                )
                            }
                        }
                    }
                    document.add(
                        XMLDocument().apply {
                            refDoc = XMLRefDoc().apply {
                                msgType = XMLCS().apply {
                                    dn = "XML-instans"
                                    v = "XML"
                                }
                                content = XMLRefDoc.Content().apply {
                                    any.add(
                                        HelseOpplysningerArbeidsuforhet().apply {
                                            syketilfelleStartDato =
                                                sykmeldingDbModel.sykmeldingsdokument.sykmelding.syketilfelleStartDato
                                            pasient = HelseOpplysningerArbeidsuforhet.Pasient().apply {
                                                navn = NavnType().apply {
                                                    fornavn = ""
                                                    mellomnavn = ""
                                                    etternavn = ""
                                                }
                                                fodselsnummer = Ident().apply {
                                                    id = sykmeldingDbModel.sykmeldingsopplysninger.pasientFnr
                                                    typeId = CV().apply {
                                                        dn = "Fødselsnummer"
                                                        s = "2.16.578.1.12.4.1.1.8116"
                                                        v = "FNR"
                                                    }
                                                }
                                            }
                                            arbeidsgiver =
                                                tilArbeidsgiver(sykmeldingDbModel.sykmeldingsdokument.sykmelding.arbeidsgiver)
                                            medisinskVurdering = tilMedisinskVurdering(
                                                sykmeldingDbModel.sykmeldingsdokument.sykmelding.medisinskVurdering,
                                                sykmeldingDbModel.sykmeldingsdokument.sykmelding.skjermesForPasient
                                            )
                                            aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                                                periode.addAll(tilPeriodeListe(sykmeldingDbModel.sykmeldingsdokument.sykmelding.perioder))
                                            }
                                            prognose = sykmeldingDbModel.sykmeldingsdokument.sykmelding.prognose?.let {
                                                tilPrognose(it)
                                            }
                                            utdypendeOpplysninger = tilUtdypendeOpplysninger(sykmeldingDbModel.sykmeldingsdokument.sykmelding.utdypendeOpplysninger)
                                            tiltak = HelseOpplysningerArbeidsuforhet.Tiltak().apply {
                                                tiltakArbeidsplassen = sykmeldingDbModel.sykmeldingsdokument.sykmelding.tiltakArbeidsplassen
                                                tiltakNAV = sykmeldingDbModel.sykmeldingsdokument.sykmelding.tiltakNAV
                                                andreTiltak = sykmeldingDbModel.sykmeldingsdokument.sykmelding.andreTiltak
                                            }
                                            meldingTilNav = sykmeldingDbModel.sykmeldingsdokument.sykmelding.meldingTilNAV?.let {
                                                HelseOpplysningerArbeidsuforhet.MeldingTilNav().apply {
                                                    beskrivBistandNAV =
                                                        sykmeldingDbModel.sykmeldingsdokument.sykmelding.meldingTilNAV?.beskrivBistand
                                                    isBistandNAVUmiddelbart =
                                                        sykmeldingDbModel.sykmeldingsdokument.sykmelding.meldingTilNAV?.bistandUmiddelbart
                                                            ?: false
                                                }
                                            }
                                            meldingTilArbeidsgiver =
                                                sykmeldingDbModel.sykmeldingsdokument.sykmelding.meldingTilArbeidsgiver
                                            kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                                                kontaktDato =
                                                    sykmeldingDbModel.sykmeldingsdokument.sykmelding.behandletTidspunkt.toLocalDate()
                                                begrunnIkkeKontakt = null
                                                behandletDato = sykmeldingDbModel.sykmeldingsdokument.sykmelding.behandletTidspunkt
                                            }
                                            behandler = tilBehandler(sykmeldingDbModel.sykmeldingsdokument.sykmelding.behandler)
                                            avsenderSystem = HelseOpplysningerArbeidsuforhet.AvsenderSystem().apply {
                                                systemNavn = "Papirsykmelding"
                                                systemVersjon = "1"
                                            }
                                            strekkode = "123456789qwerty"
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            )
        }
    }
}

fun tilBehandler(behandler: Behandler): HelseOpplysningerArbeidsuforhet.Behandler =
    HelseOpplysningerArbeidsuforhet.Behandler().apply {
        navn = NavnType().apply {
            fornavn = behandler.fornavn
            mellomnavn = behandler.mellomnavn
            etternavn = behandler.etternavn
        }
        id.addAll(
            listOf(
                Ident().apply {
                    id = behandler.hpr
                    typeId = CV().apply {
                        dn = "HPR-nummer"
                        s = "6.87.654.3.21.9.8.7.6543.2198"
                        v = "HPR"
                    }
                },
                Ident().apply {
                    id = behandler.fnr
                    typeId = CV().apply {
                        dn = "Fødselsnummer"
                        s = "2.16.578.1.12.4.1.1.8327"
                        v = "FNR"
                    }
                }
            )
        )
        adresse = Address()
        kontaktInfo.add(
            TeleCom().apply {
                typeTelecom = CS().apply {
                    v = "HP"
                    dn = "Hovedtelefon"
                }
                teleAddress = URL().apply {
                    v = if (behandler.tlf != null) "tel:${behandler.tlf}" else null
                }
            }
        )
    }

fun tilUtdypendeOpplysninger(utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>>?): HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger =
    HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger().apply {
        spmGruppe.addAll(tilSpmGruppe(utdypendeOpplysninger))
    }

fun tilSpmGruppe(utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>>?): List<HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger.SpmGruppe> {
    if (utdypendeOpplysninger.isNullOrEmpty() || !utdypendeOpplysninger.contains("6.2")) {
        return ArrayList()
    }

    val listeDynaSvarType = ArrayList<DynaSvarType>()
    val utdypendeOpplysninger62 = utdypendeOpplysninger["6.2"]

    utdypendeOpplysninger62!!["6.2.1"]?.let {
        listeDynaSvarType.add(
            DynaSvarType().apply {
                spmId = "6.2.1"
                spmTekst = "Beskriv kort sykehistorie, symptomer og funn i dagens situasjon."
                restriksjon = DynaSvarType.Restriksjon().apply {
                    restriksjonskode.add(
                        CS().apply {
                            v = "A"
                            dn = "Informasjonen skal ikke vises arbeidsgiver"
                        }
                    )
                }
                svarTekst = it.svar
            }
        )
    }
    utdypendeOpplysninger62["6.2.2"]?.let {
        listeDynaSvarType.add(
            DynaSvarType().apply {
                spmId = "6.2.2"
                spmTekst = "Hvordan påvirker sykdommen arbeidsevnen?"
                restriksjon = DynaSvarType.Restriksjon().apply {
                    restriksjonskode.add(
                        CS().apply {
                            v = "A"
                            dn = "Informasjonen skal ikke vises arbeidsgiver"
                        }
                    )
                }
                svarTekst = it.svar
            }
        )
    }
    utdypendeOpplysninger62["6.2.3"]?.let {
        listeDynaSvarType.add(
            DynaSvarType().apply {
                spmId = "6.2.3"
                spmTekst = "Har behandlingen frem til nå bedret arbeidsevnen?"
                restriksjon = DynaSvarType.Restriksjon().apply {
                    restriksjonskode.add(
                        CS().apply {
                            v = "A"
                            dn = "Informasjonen skal ikke vises arbeidsgiver"
                        }
                    )
                }
                svarTekst = it.svar
            }
        )
    }
    utdypendeOpplysninger62["6.2.4"]?.let {
        listeDynaSvarType.add(
            DynaSvarType().apply {
                spmId = "6.2.4"
                spmTekst = "Beskriv pågående og planlagt henvisning,utredning og/eller behandling."
                restriksjon = DynaSvarType.Restriksjon().apply {
                    restriksjonskode.add(
                        CS().apply {
                            v = "A"
                            dn = "Informasjonen skal ikke vises arbeidsgiver"
                        }
                    )
                }
                svarTekst = it.svar
            }
        )
    }

    // Spørsmålene kommer herfra: https://stash.adeo.no/projects/EIA/repos/nav-eia-external/browse/SM2013/xml/SM2013DynaSpm_1_5.xml
    val spmGruppe = listOf(
        HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger.SpmGruppe().apply {
            spmGruppeId = "6.2"
            spmGruppeTekst = "Utdypende opplysninger ved 7/8,17 og 39 uker"
            spmSvar.addAll(listeDynaSvarType)
        }
    )

    if (spmGruppe.first().spmSvar.isNotEmpty()) {
        return spmGruppe
    }
    return ArrayList()
}

fun tilPrognose(prognose: Prognose): HelseOpplysningerArbeidsuforhet.Prognose =
    HelseOpplysningerArbeidsuforhet.Prognose().apply {
        isArbeidsforEtterEndtPeriode = prognose.arbeidsforEtterPeriode
        beskrivHensynArbeidsplassen = prognose.hensynArbeidsplassen
        erIArbeid = prognose.erIArbeid?.let {
            HelseOpplysningerArbeidsuforhet.Prognose.ErIArbeid().apply {
                isEgetArbeidPaSikt = it.egetArbeidPaSikt
                isAnnetArbeidPaSikt = it.annetArbeidPaSikt
                arbeidFraDato = it.arbeidFOM
                vurderingDato = it.vurderingsdato
            }
        }
        erIkkeIArbeid = prognose.erIkkeIArbeid?.let {
            HelseOpplysningerArbeidsuforhet.Prognose.ErIkkeIArbeid().apply {
                isArbeidsforPaSikt = it.arbeidsforPaSikt
                arbeidsforFraDato = it.arbeidsforFOM
                vurderingDato = it.vurderingsdato
            }
        }
    }

fun tilPeriodeListe(perioder: List<Periode>): List<HelseOpplysningerArbeidsuforhet.Aktivitet.Periode> {
    val periodeListe = ArrayList<HelseOpplysningerArbeidsuforhet.Aktivitet.Periode>()

    perioder.forEach {
        if (it.aktivitetIkkeMulig != null) {
            periodeListe.add(
                HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                    periodeFOMDato = it.fom
                    periodeTOMDato = it.tom
                    aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig().apply {
                        medisinskeArsaker = if (it.aktivitetIkkeMulig?.medisinskArsak != null) {
                            ArsakType().apply {
                                beskriv = it.aktivitetIkkeMulig!!.medisinskArsak?.beskrivelse
                                arsakskode.add(CS())
                            }
                        } else {
                            null
                        }
                        arbeidsplassen = if (it.aktivitetIkkeMulig?.arbeidsrelatertArsak != null) {
                            ArsakType().apply {
                                beskriv = it.aktivitetIkkeMulig!!.arbeidsrelatertArsak?.beskrivelse
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
            )
        }
        if (it.gradert != null) {
            periodeListe.add(
                HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                    periodeFOMDato = it.fom
                    periodeTOMDato = it.tom
                    aktivitetIkkeMulig = null
                    avventendeSykmelding = null
                    gradertSykmelding = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.GradertSykmelding().apply {
                        isReisetilskudd = it.gradert?.reisetilskudd
                            ?: false
                        sykmeldingsgrad = it.gradert!!.grad
                    }
                    behandlingsdager = null
                    isReisetilskudd = false
                }
            )
        }
        if (!it.avventendeInnspillTilArbeidsgiver.isNullOrEmpty()) {
            periodeListe.add(
                HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                    periodeFOMDato = it.fom
                    periodeTOMDato = it.tom
                    aktivitetIkkeMulig = null
                    avventendeSykmelding = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AvventendeSykmelding().apply {
                        innspillTilArbeidsgiver = it.avventendeInnspillTilArbeidsgiver
                    }
                    gradertSykmelding = null
                    behandlingsdager = null
                    isReisetilskudd = false
                }
            )
        }
        if (it.behandlingsdager != null) {
            periodeListe.add(
                HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                    periodeFOMDato = it.fom
                    periodeTOMDato = it.tom
                    aktivitetIkkeMulig = null
                    avventendeSykmelding = null
                    gradertSykmelding = null
                    behandlingsdager = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.Behandlingsdager().apply {
                        antallBehandlingsdagerUke = it.behandlingsdager!!
                    }
                    isReisetilskudd = false
                }
            )
        }
        if (it.reisetilskudd) {
            periodeListe.add(
                HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                    periodeFOMDato = it.fom
                    periodeTOMDato = it.tom
                    aktivitetIkkeMulig = null
                    avventendeSykmelding = null
                    gradertSykmelding = null
                    behandlingsdager = null
                    isReisetilskudd = true
                }
            )
        }
    }
    if (periodeListe.isEmpty()) {
        log.error("Could not find aktivitetstype, {}")
        throw IllegalStateException("Cound not find aktivitetstype")
    }
    return periodeListe
}

fun tilArbeidsgiver(arbeidsgiver: Arbeidsgiver): HelseOpplysningerArbeidsuforhet.Arbeidsgiver? =
    HelseOpplysningerArbeidsuforhet.Arbeidsgiver().apply {
        harArbeidsgiver = with(arbeidsgiver.harArbeidsgiver) {
            when {
                this == HarArbeidsgiver.INGEN_ARBEIDSGIVER -> CS().apply {
                    dn = "Ingen arbeidsgiver"
                    v = "3"
                }
                this == HarArbeidsgiver.FLERE_ARBEIDSGIVERE -> CS().apply {
                    dn = "Flere arbeidsgivere"
                    v = "2"
                }
                this == HarArbeidsgiver.EN_ARBEIDSGIVER -> CS().apply {
                    dn = "Én arbeidsgiver"
                    v = "1"
                }
                else -> {
                    log.error("Klarte ikke å mappe til riktig harArbeidsgiver-verdi")
                    throw IllegalStateException("Klarte ikke å mappe til riktig harArbeidsgiver-verdi")
                }
            }
        }
        navnArbeidsgiver = arbeidsgiver.navn
        yrkesbetegnelse = arbeidsgiver.yrkesbetegnelse
        stillingsprosent = arbeidsgiver.stillingsprosent
    }

fun tilMedisinskVurdering(
    medisinskVurdering: MedisinskVurdering,
    skjermesForPasient: Boolean
): HelseOpplysningerArbeidsuforhet.MedisinskVurdering {
    if (medisinskVurdering.hovedDiagnose == null && medisinskVurdering.annenFraversArsak == null) {
        log.warn("Sykmelding mangler hoveddiagnose og annenFraversArsak, avbryter..")
        throw IllegalStateException("Sykmelding mangler hoveddiagnose")
    }

    val biDiagnoseListe: List<CV>? = medisinskVurdering.biDiagnoser.map {
        toMedisinskVurderingDiagnose(it.kode, it.system, it.tekst)
    }

    return HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
        if (medisinskVurdering.hovedDiagnose != null) {
            hovedDiagnose = HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose().apply {
                diagnosekode = toMedisinskVurderingDiagnose(
                    medisinskVurdering.hovedDiagnose!!.kode,
                    medisinskVurdering.hovedDiagnose!!.system,
                    medisinskVurdering.hovedDiagnose!!.tekst
                )
            }
        }
        if (biDiagnoseListe != null && biDiagnoseListe.isNotEmpty()) {
            biDiagnoser = HelseOpplysningerArbeidsuforhet.MedisinskVurdering.BiDiagnoser().apply {
                diagnosekode.addAll(biDiagnoseListe)
            }
        }
        isSkjermesForPasient = skjermesForPasient
        annenFraversArsak = medisinskVurdering.annenFraversArsak?.let {
            ArsakType().apply {
                arsakskode.add(CS())
                beskriv = medisinskVurdering.annenFraversArsak!!.beskrivelse
            }
        }
        isSvangerskap = medisinskVurdering.svangerskap
        isYrkesskade = medisinskVurdering.yrkesskade
        yrkesskadeDato = medisinskVurdering.yrkesskadeDato
    }
}

fun toMedisinskVurderingDiagnose(kode: String, system: String?, tekst: String?): CV =
    CV().apply {
        s = system
        v = kode
        dn = tekst
    }
