package no.nav.syfo.identendring

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.identendring.client.NarmesteLeder
import no.nav.syfo.identendring.client.NarmestelederClient
import no.nav.syfo.identendring.db.Adresse
import no.nav.syfo.identendring.db.AktivitetIkkeMulig
import no.nav.syfo.identendring.db.Arbeidsgiver
import no.nav.syfo.identendring.db.ArbeidsgiverDbModel
import no.nav.syfo.identendring.db.AvsenderSystem
import no.nav.syfo.identendring.db.Behandler
import no.nav.syfo.identendring.db.Diagnose
import no.nav.syfo.identendring.db.HarArbeidsgiver
import no.nav.syfo.identendring.db.KontaktMedPasient
import no.nav.syfo.identendring.db.MedisinskArsak
import no.nav.syfo.identendring.db.MedisinskVurdering
import no.nav.syfo.identendring.db.MeldingTilNAV
import no.nav.syfo.identendring.db.Periode
import no.nav.syfo.identendring.db.StatusDbModel
import no.nav.syfo.identendring.db.Sykmelding
import no.nav.syfo.identendring.db.SykmeldingDbModelUtenBehandlingsutfall
import no.nav.syfo.identendring.db.getSykmeldingerMedFnrUtenBehandlingsutfall
import no.nav.syfo.identendring.db.updateFnr
import no.nav.syfo.narmesteleder.NarmesteLederResponseKafkaProducer
import no.nav.syfo.narmesteleder.kafkamodel.Leder
import no.nav.syfo.narmesteleder.kafkamodel.NlResponse
import no.nav.syfo.narmesteleder.kafkamodel.NlResponseKafkaMessage
import no.nav.syfo.narmesteleder.kafkamodel.Sykmeldt
import no.nav.syfo.pdl.client.model.IdentInformasjon
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sm.Diagnosekoder
import no.nav.syfo.sykmelding.aivenmigrering.SykmeldingV2KafkaProducer
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertFailsWith

class UpdateFnrServiceTest : FunSpec({
    val pdlPersonService = mockk<PdlPersonService>(relaxed = true)
    mockkStatic("no.nav.syfo.identendring.db.SyfoSmRegisterKt")
    val db = mockk<DatabaseInterfacePostgres>(relaxed = true)
    val sendtSykmeldingKafkaProducer = mockk<SykmeldingV2KafkaProducer>(relaxed = true)
    val narmesteLederResponseKafkaProducer = mockk<NarmesteLederResponseKafkaProducer>(relaxed = true)
    val narmestelederClient = mockk<NarmestelederClient>()

    val updateFnrService = UpdateFnrService(pdlPersonService, db, sendtSykmeldingKafkaProducer, narmesteLederResponseKafkaProducer, narmestelederClient, "topic")

    beforeAny {
        clearMocks(sendtSykmeldingKafkaProducer, narmesteLederResponseKafkaProducer)
    }

    context("Test at UpdateFnrService fungerer som forventet") {
        test("Skal oppdatere OK hvis nytt og gammelt fnr er knyttet til samme person") {
            coEvery { pdlPersonService.getPdlPerson(any()) } returns PdlPerson(
                listOf(
                    IdentInformasjon("12345678913", false, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("12345678912", true, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("12345", false, "AKTORID")
                ),
                "navn navn"
            )
            coEvery { narmestelederClient.getNarmesteledere(any()) } returns emptyList()

            every { db.updateFnr(any(), any()) } returns 1

            runBlocking {
                updateFnrService.updateFnr(
                    fnr = "12345678912",
                    nyttFnr = "12345678913"
                ) shouldBeEqualTo true
            }
        }

        test("Skal kaste feil hvis nytt og gammelt fnr ikke er knyttet til samme person") {
            coEvery { pdlPersonService.getPdlPerson(any()) } returns PdlPerson(
                listOf(
                    IdentInformasjon("12345678913", false, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("12345678912", true, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("12345", false, "AKTORID")
                ),
                "navn navn"
            )

            every { db.updateFnr(any(), any()) } returns 1

            runBlocking {
                val assertFailsWith = assertFailsWith<UpdateIdentException> {
                    updateFnrService.updateFnr(
                        fnr = "12345678912",
                        nyttFnr = "12345678914"
                    )
                }
                assertFailsWith.message shouldBeEqualTo "Oppdatering av fnr feilet, nyttFnr står ikke som aktivt fnr for aktøren i PDL"
            }
        }

        test("Skal kaste feil hvis fnr ikke er registrert som historisk for person") {
            coEvery { pdlPersonService.getPdlPerson(any()) } returns PdlPerson(
                listOf(
                    IdentInformasjon("12345678913", false, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("123", true, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("12345", false, "AKTORID")
                ),
                "navn navn"
            )

            every { db.updateFnr(any(), any()) } returns 1

            runBlocking {
                val assertFailsWith = assertFailsWith<UpdateIdentException> {
                    updateFnrService.updateFnr(
                        fnr = "12345678912",
                        nyttFnr = "12345678913"
                    )
                }
                assertFailsWith.message shouldBeEqualTo "Oppdatering av fnr feilet, fnr er ikke historisk for aktør"
            }
        }

        test("Oppdaterer sendte sykmeldinger og aktiv NL-relasjon") {
            coEvery { pdlPersonService.getPdlPerson(any()) } returns PdlPerson(
                listOf(
                    IdentInformasjon("12345678913", false, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("12345678912", true, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("12345", false, "AKTORID")
                ),
                "navn navn"
            )
            every { db.getSykmeldingerMedFnrUtenBehandlingsutfall("12345678912") } returns listOf(getSendtSykmelding())
            coEvery { narmestelederClient.getNarmesteledere(any()) } returns listOf(getNarmesteLeder())

            every { db.updateFnr(any(), any()) } returns 1

            runBlocking {
                updateFnrService.updateFnr(
                    fnr = "12345678912",
                    nyttFnr = "12345678913"
                ) shouldBeEqualTo true

                coVerify {
                    sendtSykmeldingKafkaProducer.sendSykmelding(
                        match { it.kafkaMetadata.fnr == "12345678913" },
                        any(),
                        any()
                    )
                }
                coVerify(exactly = 1) {
                    narmesteLederResponseKafkaProducer.publishToKafka(
                        match<NlResponseKafkaMessage> { it.nlAvbrutt?.sykmeldtFnr == "12345678912" },
                        "9898"
                    )
                }
                coVerify(exactly = 1) {
                    narmesteLederResponseKafkaProducer.publishToKafka(
                        match<NlResponseKafkaMessage> { it.nlResponse == getExpectedNarmestelederResponse() },
                        "9898"
                    )
                }
            }
        }

        test("Oppdaterer kun sendte sykmeldinger fra de siste fire måneder og kun aktiv NL-relasjon") {
            coEvery { pdlPersonService.getPdlPerson(any()) } returns PdlPerson(
                listOf(
                    IdentInformasjon("12345678913", false, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("12345678912", true, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("12345", false, "AKTORID")
                ),
                "navn navn"
            )
            every { db.getSykmeldingerMedFnrUtenBehandlingsutfall("12345678912") } returns listOf(
                getSendtSykmelding(),
                getSendtSykmelding().copy(status = StatusDbModel("APEN", OffsetDateTime.now(ZoneOffset.UTC), null)),
                getSendtSykmelding(
                    listOf(
                        Periode(
                            fom = LocalDate.now().minusMonths(6),
                            tom = LocalDate.now().minusMonths(5),
                            aktivitetIkkeMulig = AktivitetIkkeMulig(MedisinskArsak(null, emptyList()), null),
                            avventendeInnspillTilArbeidsgiver = null,
                            behandlingsdager = 0,
                            gradert = null,
                            reisetilskudd = false
                        )
                    )
                )
            )
            coEvery { narmestelederClient.getNarmesteledere(any()) } returns listOf(
                getNarmesteLeder(),
                getNarmesteLeder().copy(narmesteLederFnr = "987", orgnummer = "9999", aktivTom = LocalDate.now())
            )

            every { db.updateFnr(any(), any()) } returns 1

            runBlocking {
                updateFnrService.updateFnr(
                    fnr = "12345678912",
                    nyttFnr = "12345678913"
                ) shouldBeEqualTo true

                coVerify(exactly = 1) {
                    sendtSykmeldingKafkaProducer.sendSykmelding(
                        match { it.kafkaMetadata.fnr == "12345678913" },
                        any(),
                        any()
                    )
                }
                coVerify(exactly = 1) {
                    narmesteLederResponseKafkaProducer.publishToKafka(
                        match<NlResponseKafkaMessage> { it.nlAvbrutt?.sykmeldtFnr == "12345678912" },
                        "9898"
                    )
                }
                coVerify(exactly = 1) {
                    narmesteLederResponseKafkaProducer.publishToKafka(
                        match<NlResponseKafkaMessage> { it.nlResponse == getExpectedNarmestelederResponse() },
                        "9898"
                    )
                }
            }
        }
    }

    context("Test at UpdateFnrService fungerer som forventet for leder") {
        test("Skal kaste feil hvis nytt og gammelt fnr ikke er knyttet til samme person") {
            coEvery { pdlPersonService.getPdlPerson(any()) } returns PdlPerson(
                listOf(
                    IdentInformasjon("12345678913", false, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("12345678912", true, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("12345", false, "AKTORID")
                ),
                "navn navn"
            )

            runBlocking {
                val assertFailsWith = assertFailsWith<UpdateIdentException> {
                    updateFnrService.updateNlFnr(
                        fnr = "12345678912",
                        nyttFnr = "12345678914"
                    )
                }
                assertFailsWith.message shouldBeEqualTo "Oppdatering av leders fnr feilet, nyttFnr står ikke som aktivt fnr for aktøren i PDL"
            }
        }
        test("Skal kaste feil hvis fnr ikke er registrert som historisk for person") {
            coEvery { pdlPersonService.getPdlPerson(any()) } returns PdlPerson(
                listOf(
                    IdentInformasjon("12345678913", false, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("123", true, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("12345", false, "AKTORID")
                ),
                "navn navn"
            )

            runBlocking {
                val assertFailsWith = assertFailsWith<UpdateIdentException> {
                    updateFnrService.updateNlFnr(
                        fnr = "12345678912",
                        nyttFnr = "12345678913"
                    )
                }
                assertFailsWith.message shouldBeEqualTo "Oppdatering av leders fnr feilet, fnr er ikke historisk for aktør"
            }
        }
        test("Oppdaterer aktiv NL-relasjon") {
            coEvery { pdlPersonService.getPdlPerson(any()) } returns PdlPerson(
                listOf(
                    IdentInformasjon("12345678913", false, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("12345678912", true, "FOLKEREGISTERIDENT"),
                    IdentInformasjon("12345", false, "AKTORID")
                ),
                "navn navn"
            )
            coEvery { narmestelederClient.getNarmestelederKoblingerForLeder("12345678912") } returns listOf(getNarmesteLeder().copy(fnr = "10987654321", narmesteLederFnr = "12345678912"))

            runBlocking {
                updateFnrService.updateNlFnr(
                    fnr = "12345678912",
                    nyttFnr = "12345678913"
                ) shouldBeEqualTo true

                coVerify(exactly = 1) {
                    narmesteLederResponseKafkaProducer.publishToKafka(
                        match<NlResponseKafkaMessage> { it.nlAvbrutt?.sykmeldtFnr == "10987654321" },
                        "9898"
                    )
                }
                coVerify(exactly = 1) {
                    narmesteLederResponseKafkaProducer.publishToKafka(
                        match<NlResponseKafkaMessage> {
                            it.nlResponse == NlResponse(
                                orgnummer = "9898",
                                utbetalesLonn = true,
                                leder = Leder(
                                    fnr = "12345678913",
                                    mobil = "90909090",
                                    epost = "mail@nav.no",
                                    fornavn = null,
                                    etternavn = null
                                ),
                                sykmeldt = Sykmeldt(fnr = "10987654321", navn = null),
                                aktivFom = LocalDate.of(2019, 2, 2).atStartOfDay().atOffset(ZoneOffset.UTC),
                                aktivTom = null
                            )
                        },
                        "9898"
                    )
                }
            }
        }
    }
})

fun getSendtSykmelding(periodeListe: List<Periode>? = null): SykmeldingDbModelUtenBehandlingsutfall {
    val id = UUID.randomUUID().toString()
    return SykmeldingDbModelUtenBehandlingsutfall(
        id = id,
        mottattTidspunkt = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(1),
        legekontorOrgNr = "8888",
        sykmeldingsDokument = Sykmelding(
            id = id,
            arbeidsgiver = Arbeidsgiver(
                harArbeidsgiver = HarArbeidsgiver.EN_ARBEIDSGIVER,
                navn = "navn",
                stillingsprosent = null,
                yrkesbetegnelse = null
            ),
            medisinskVurdering = MedisinskVurdering(
                hovedDiagnose = Diagnose(Diagnosekoder.ICPC2_CODE, "L87", null),
                biDiagnoser = emptyList(),
                yrkesskade = false,
                svangerskap = false,
                annenFraversArsak = null,
                yrkesskadeDato = null
            ),
            andreTiltak = "Andre tiltak",
            meldingTilArbeidsgiver = null,
            navnFastlege = null,
            tiltakArbeidsplassen = null,
            syketilfelleStartDato = null,
            tiltakNAV = "Tiltak NAV",
            prognose = null,
            meldingTilNAV = MeldingTilNAV(true, "Masse bistand"),
            skjermesForPasient = false,
            behandletTidspunkt = LocalDateTime.now(),
            behandler = Behandler(
                "fornavn",
                null,
                "etternavn",
                "aktorId",
                "01234567891",
                null,
                null,
                Adresse(null, null, null, null, null),
                null
            ),
            kontaktMedPasient = KontaktMedPasient(
                LocalDate.now(),
                "Begrunnelse"
            ),
            utdypendeOpplysninger = emptyMap(),
            msgId = "msgid",
            pasientAktoerId = "aktorId",
            avsenderSystem = AvsenderSystem("Navn", "verjosn"),
            perioder = periodeListe ?: listOf(
                Periode(
                    fom = LocalDate.now().minusMonths(1),
                    tom = LocalDate.now().minusWeeks(3),
                    aktivitetIkkeMulig = AktivitetIkkeMulig(MedisinskArsak(null, emptyList()), null),
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = 0,
                    gradert = null,
                    reisetilskudd = false
                )
            ),
            signaturDato = LocalDateTime.now()
        ),
        status = StatusDbModel(
            "SENDT",
            OffsetDateTime.now(ZoneOffset.UTC).minusDays(7),
            ArbeidsgiverDbModel("9898", null, "Bedriften AS")
        ),
        merknader = null
    )
}

fun getNarmesteLeder(): NarmesteLeder {
    return NarmesteLeder(
        fnr = "12345678912",
        narmesteLederFnr = "12345",
        orgnummer = "9898",
        narmesteLederTelefonnummer = "90909090",
        narmesteLederEpost = "mail@nav.no",
        aktivFom = LocalDate.of(2019, 2, 2),
        aktivTom = null,
        arbeidsgiverForskutterer = true
    )
}

fun getExpectedNarmestelederResponse(): NlResponse {
    return NlResponse(
        orgnummer = "9898",
        utbetalesLonn = true,
        leder = Leder(fnr = "12345", mobil = "90909090", epost = "mail@nav.no", fornavn = null, etternavn = null),
        sykmeldt = Sykmeldt(fnr = "12345678913", navn = null),
        aktivFom = LocalDate.of(2019, 2, 2).atStartOfDay().atOffset(ZoneOffset.UTC),
        aktivTom = null
    )
}
