package no.nav.syfo.service

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkStatic
import io.mockk.verify
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.model.ArbeidsgiverStatus
import no.nav.syfo.model.ShortName
import no.nav.syfo.model.Sporsmal
import no.nav.syfo.model.StatusEvent
import no.nav.syfo.model.Svar
import no.nav.syfo.model.Svartype
import no.nav.syfo.model.SykmeldingStatusEvent
import no.nav.syfo.model.SykmeldingStatusTopicEvent
import no.nav.syfo.persistering.db.postgres.deleteSykmeldingStatus
import no.nav.syfo.persistering.db.postgres.getStatusesForSykmelding
import no.nav.syfo.persistering.db.postgres.lagreSporsmalOgSvarOgArbeidsgiver
import no.nav.syfo.persistering.db.postgres.oppdaterSykmeldingStatus
import no.nav.syfo.persistering.db.postgres.svarFinnesFraFor
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.xdescribe
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class UpdateStatusServiceTest : Spek({

    val database = mockkClass(DatabaseInterfacePostgres::class)
    mockkStatic("no.nav.syfo.persistering.db.postgres.SyfoSmRegisterQueriesKt")

    val updateStatusService = UpdateStatusService(database)
    val sykmeldingId = UUID.randomUUID().toString()

    beforeEachTest {
        clearAllMocks()
        every { database.oppdaterSykmeldingStatus(any()) } returns Unit
        every { database.deleteSykmeldingStatus(any(), any()) } returns Unit
        every { database.lagreSporsmalOgSvarOgArbeidsgiver(any(), any()) } returns Unit
    }

    xdescribe("Update SykmeldingStatus") {

        it("Test SLETTET status") {
            every { database.getStatusesForSykmelding(any()) } returns emptyList()
            val createdDate = LocalDateTime.now()
            val sendtTilArbeidsgiverDate = createdDate.plusHours(1)
            val kafkaTimestamp = createdDate.plusHours(1)
            val sykmeldingStatus = SykmeldingStatusTopicEvent(
                sykmeldingId = sykmeldingId,
                arbeidssituasjon = null,
                sendtTilArbeidsgiverDato = sendtTilArbeidsgiverDate,
                arbeidsgiver = null,
                status = StatusEvent.SLETTET,
                created = createdDate,
                harFravaer = null,
                harForsikring = null,
                fravarsPeriode = null,
                kafkaTimestamp = kafkaTimestamp
            )
            updateStatusService.updateSykemdlingStatus(sykmeldingStatus)
            val listOfStatuses = listOf(
                SykmeldingStatusEvent(sykmeldingId, createdDate, StatusEvent.APEN),
                SykmeldingStatusEvent(sykmeldingId, sendtTilArbeidsgiverDate, StatusEvent.SLETTET)
            )
            verify(exactly = 1) { database.oppdaterSykmeldingStatus(listOfStatuses) }
            verify(exactly = 0) { database.lagreSporsmalOgSvarOgArbeidsgiver(any(), any()) }
            verify(exactly = 0) { database.svarFinnesFraFor(any()) }
        }

        it("Test utgatt status ") {

            every { database.getStatusesForSykmelding(any()) } returns emptyList()
            val createdDate = LocalDateTime.now()

            val kafkaTimestamp = createdDate.plusHours(1)
            val sykmeldingStatus = SykmeldingStatusTopicEvent(
                sykmeldingId = sykmeldingId,
                arbeidssituasjon = null,
                sendtTilArbeidsgiverDato = null,
                arbeidsgiver = null,
                status = StatusEvent.UTGATT,
                created = createdDate,
                harFravaer = null,
                harForsikring = null,
                fravarsPeriode = null,
                kafkaTimestamp = kafkaTimestamp
            )
            updateStatusService.updateSykemdlingStatus(sykmeldingStatus)
            val listOfStatuses = listOf(
                SykmeldingStatusEvent(sykmeldingId, createdDate, StatusEvent.APEN),
                SykmeldingStatusEvent(sykmeldingId, createdDate.plusMonths(3), StatusEvent.UTGATT)
            )
            verify(exactly = 1) { database.oppdaterSykmeldingStatus(listOfStatuses) }
        }

        it("Skal slette status før og inserte på nytt UTGATT") {

            every { database.getStatusesForSykmelding(any()) } returns emptyList()
            val createdDate = LocalDateTime.now().minusMonths(4)
            val timestampUtgatt = createdDate.plusMonths(3)
            val kafkaTimestamp =
                LocalDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), ZoneId.systemDefault())

            every { database.getStatusesForSykmelding(any()) } returns emptyList()
            every { database.deleteSykmeldingStatus(sykmeldingId, kafkaTimestamp) } returns Unit

            val sykmeldingStatus = SykmeldingStatusTopicEvent(
                sykmeldingId = sykmeldingId,
                arbeidssituasjon = null,
                sendtTilArbeidsgiverDato = null,
                arbeidsgiver = null,
                status = StatusEvent.UTGATT,
                created = createdDate,
                harFravaer = null,
                harForsikring = null,
                fravarsPeriode = null,
                kafkaTimestamp = kafkaTimestamp
            )
            updateStatusService.updateSykemdlingStatus(sykmeldingStatus)
            val listOfStatuses = listOf(
                SykmeldingStatusEvent(sykmeldingId, createdDate, StatusEvent.APEN),
                SykmeldingStatusEvent(sykmeldingId, timestampUtgatt, StatusEvent.UTGATT)
            )
            verify(exactly = 1) { database.oppdaterSykmeldingStatus(listOfStatuses) }
            verify(exactly = 1) { database.deleteSykmeldingStatus(sykmeldingId, kafkaTimestamp) }
        }

        it("Ikke insert status om nyere finnes i syfosmregister") {
            val createdDate = LocalDateTime.now().minusMonths(3)
            val timestampUtgatt = createdDate.plusMonths(3)
            val kafkaTimestamp = timestampUtgatt.minusHours(1)

            every { database.getStatusesForSykmelding(sykmeldingId) } returns listOf(
                SykmeldingStatusEvent(
                    sykmeldingId,
                    kafkaTimestamp.plusMinutes(
                        10
                    ),
                    StatusEvent.UTGATT
                )
            )

            val sykmeldingStatus = SykmeldingStatusTopicEvent(
                sykmeldingId = sykmeldingId,
                arbeidsgiver = null,
                sendtTilArbeidsgiverDato = null,
                fravarsPeriode = null,
                harForsikring = null,
                harFravaer = null,
                created = createdDate,
                status = StatusEvent.APEN,
                arbeidssituasjon = null,
                kafkaTimestamp = kafkaTimestamp
            )

            updateStatusService.updateSykemdlingStatus(sykmeldingStatus)

            verify(exactly = 1) { database.deleteSykmeldingStatus(sykmeldingId, kafkaTimestamp) }
            verify(exactly = 1) {
                database.oppdaterSykmeldingStatus(
                    listOf(
                        SykmeldingStatusEvent(
                            sykmeldingId = sykmeldingId,
                            event = StatusEvent.APEN,
                            eventTimestamp = createdDate
                        )
                    )
                )
            }
        }

        it("Should insert sendt status when svar finnes fra før") {

            val createdDateTime = LocalDateTime.now().minusDays(10)
            val sendtTilArbeidsgiverDato = createdDateTime.plusDays(5)

            val kafkaTimeStamp = sendtTilArbeidsgiverDato.minusHours(1)

            every { database.svarFinnesFraFor(sykmeldingId) } returns true
            every { database.getStatusesForSykmelding(any()) } returns emptyList()
            val sykmeldingStatus = getSykmeldingTopicEvent(
                sykmeldingId,
                sendtTilArbeidsgiverDato,
                kafkaTimeStamp,
                createdDateTime
            )
            updateStatusService.updateSykemdlingStatus(sykmeldingStatus)
            verify(exactly = 1) { database.deleteSykmeldingStatus(sykmeldingId, kafkaTimeStamp) }
            verify(exactly = 1) {
                database.oppdaterSykmeldingStatus(
                    listOf(
                        SykmeldingStatusEvent(
                            sykmeldingId,
                            createdDateTime,
                            StatusEvent.APEN
                        ),
                        SykmeldingStatusEvent(sykmeldingId, sendtTilArbeidsgiverDato, StatusEvent.SENDT)
                    )
                )
            }
            verify(exactly = 0) {
                database.lagreSporsmalOgSvarOgArbeidsgiver(any(), any())
            }
        }

        it("Skal inserte send, arbeidsgiver og sporsmalOgSvar om SporsmalOgSvar ikke finnes fra før") {
            val createdDate = LocalDateTime.of(2019, 10, 1, 1, 1)
            val sendtDateTime = createdDate.plusDays(5)

            val kafkaTime = sendtDateTime.plusDays(10)

            every { database.getStatusesForSykmelding(sykmeldingId) } returns emptyList()
            every { database.svarFinnesFraFor(sykmeldingId) } returns false

            val sykmeldingStatusTopicEvent = SykmeldingStatusTopicEvent(
                sykmeldingId = sykmeldingId,
                arbeidsgiver = ArbeidsgiverStatus(sykmeldingId, "123456789", "123456789", "navn"),
                created = createdDate,
                status = StatusEvent.SENDT,
                kafkaTimestamp = kafkaTime,
                harForsikring = null,
                harFravaer = null,
                fravarsPeriode = null,
                sendtTilArbeidsgiverDato = sendtDateTime,
                arbeidssituasjon = "ARBEIDSTAKER"
            )

            updateStatusService.updateSykemdlingStatus(sykmeldingStatusTopicEvent)

            verify(exactly = 1) { database.getStatusesForSykmelding(sykmeldingId) }
            verify(exactly = 1) {
                database.lagreSporsmalOgSvarOgArbeidsgiver(
                    listOf(
                        Sporsmal(
                            "Jeg er sykmeldt fra",
                            ShortName.ARBEIDSSITUASJON,
                            Svar(
                                sykmeldingId = sykmeldingStatusTopicEvent.sykmeldingId,
                                svartype = Svartype.ARBEIDSSITUASJON,
                                svar = sykmeldingStatusTopicEvent.arbeidssituasjon!!,
                                sporsmalId = null
                            )
                        )
                    ),
                    sykmeldingStatusTopicEvent.arbeidsgiver
                )
            }
            verify(exactly = 1) { database.svarFinnesFraFor(sykmeldingId) }
        }

        it("Skal insert bekreftet med sporsmalOgSvar om det ikke finnes nyere i syfosmregister") {
            val createdDateTime = LocalDateTime.of(2019, 1, 1, 1, 0)
            val sendtTilArbeidsgiverDato = createdDateTime.plusHours(2)
            val kafkaTime = sendtTilArbeidsgiverDato.plusMinutes(2)

            every { database.getStatusesForSykmelding(sykmeldingId) } returns emptyList()
            every { database.svarFinnesFraFor(sykmeldingId) } returns false

            val sykmeldingStatusTopicEvent = SykmeldingStatusTopicEvent(
                sykmeldingId = sykmeldingId,
                arbeidssituasjon = "SELVSTENDING",
                sendtTilArbeidsgiverDato = sendtTilArbeidsgiverDato,
                fravarsPeriode = null,
                harFravaer = false,
                harForsikring = true,
                kafkaTimestamp = kafkaTime,
                status = StatusEvent.BEKREFTET,
                created = createdDateTime,
                arbeidsgiver = null
            )

            updateStatusService.updateSykemdlingStatus(sykmeldingStatusTopicEvent)
            verify(exactly = 1) { database.deleteSykmeldingStatus(sykmeldingId, kafkaTime) }
            verify(exactly = 1) { database.getStatusesForSykmelding(sykmeldingId) }
            verify(exactly = 1) {
                database.oppdaterSykmeldingStatus(
                    listOf(
                        SykmeldingStatusEvent(
                            sykmeldingId,
                            createdDateTime,
                            StatusEvent.APEN
                        ),
                        SykmeldingStatusEvent(sykmeldingId, sendtTilArbeidsgiverDato, StatusEvent.BEKREFTET)
                    )
                )
            }
            verify(exactly = 1) { database.svarFinnesFraFor(sykmeldingId) }
            verify(exactly = 1) {
                database.lagreSporsmalOgSvarOgArbeidsgiver(
                    listOf(
                        Sporsmal(
                            "Jeg er sykmeldt fra",
                            ShortName.ARBEIDSSITUASJON,
                            Svar(
                                sykmeldingId = sykmeldingStatusTopicEvent.sykmeldingId,
                                svartype = Svartype.ARBEIDSSITUASJON,
                                svar = sykmeldingStatusTopicEvent.arbeidssituasjon!!,
                                sporsmalId = null
                            )
                        ),
                        Sporsmal(
                            "Har du forsikring som gjelder de første 16 dagene av sykefraværet?",
                            ShortName.FORSIKRING,
                            Svar(
                                sykmeldingStatusTopicEvent.sykmeldingId,
                                null,
                                Svartype.JA_NEI,
                                "JA"
                            )
                        ),
                        Sporsmal(
                            "Brukte du egenmelding eller noen annen sykmelding før datoen denne sykmeldingen gjelder fra?",
                            ShortName.FRAVAER,
                            Svar(
                                sykmeldingStatusTopicEvent.sykmeldingId,
                                null,
                                Svartype.JA_NEI,
                                "NEI"
                            )
                        )
                    ),
                    null
                )
            }
        }

        it("Skal insert bekreftet uten svar om status ikke finnes men sporsmalOgSvar finnes") {
            val createdDateTime = LocalDateTime.of(2019, 1, 1, 1, 0)
            val sendtTilArbeidsgiverDato = createdDateTime.plusHours(2)
            val kafkaTime = sendtTilArbeidsgiverDato.plusMinutes(2)

            every { database.getStatusesForSykmelding(sykmeldingId) } returns emptyList()
            every { database.svarFinnesFraFor(sykmeldingId) } returns true

            val sykmeldingStatusTopicEvent = SykmeldingStatusTopicEvent(
                sykmeldingId = sykmeldingId,
                arbeidssituasjon = "SELVSTENDING",
                sendtTilArbeidsgiverDato = sendtTilArbeidsgiverDato,
                fravarsPeriode = null,
                harFravaer = false,
                harForsikring = true,
                kafkaTimestamp = kafkaTime,
                status = StatusEvent.BEKREFTET,
                created = createdDateTime,
                arbeidsgiver = null
            )

            updateStatusService.updateSykemdlingStatus(sykmeldingStatusTopicEvent)
            verify(exactly = 1) { database.deleteSykmeldingStatus(sykmeldingId, kafkaTime) }
            verify(exactly = 1) { database.getStatusesForSykmelding(sykmeldingId) }
            verify(exactly = 1) {
                database.oppdaterSykmeldingStatus(
                    listOf(
                        SykmeldingStatusEvent(
                            sykmeldingId,
                            createdDateTime,
                            StatusEvent.APEN
                        ),
                        SykmeldingStatusEvent(sykmeldingId, sendtTilArbeidsgiverDato, StatusEvent.BEKREFTET)
                    )
                )
            }
            verify(exactly = 1) { database.svarFinnesFraFor(sykmeldingId) }
            verify(exactly = 0) { database.lagreSporsmalOgSvarOgArbeidsgiver(any(), any()) }
        }

        it("Skal bare insert APEN om bekreftet finnes i syfosmregister") {
            val createdDateTime = LocalDateTime.of(2019, 1, 1, 1, 0)
            val sendtTilArbeidsgiverDato = createdDateTime.plusHours(2)
            val kafkaTime = sendtTilArbeidsgiverDato.plusMinutes(2)

            every { database.getStatusesForSykmelding(sykmeldingId) } returns listOf(
                SykmeldingStatusEvent(
                    sykmeldingId,
                    sendtTilArbeidsgiverDato,
                    StatusEvent.BEKREFTET
                )
            )
            every { database.svarFinnesFraFor(sykmeldingId) } returns true

            val sykmeldingStatusTopicEvent = SykmeldingStatusTopicEvent(
                sykmeldingId = sykmeldingId,
                arbeidssituasjon = "SELVSTENDING",
                sendtTilArbeidsgiverDato = sendtTilArbeidsgiverDato,
                fravarsPeriode = null,
                harFravaer = null,
                harForsikring = null,
                kafkaTimestamp = kafkaTime,
                status = StatusEvent.APEN,
                created = createdDateTime,
                arbeidsgiver = null
            )

            updateStatusService.updateSykemdlingStatus(sykmeldingStatusTopicEvent)
            verify(exactly = 1) { database.deleteSykmeldingStatus(sykmeldingId, kafkaTime) }
            verify(exactly = 1) { database.getStatusesForSykmelding(sykmeldingId) }
            verify(exactly = 1) {
                database.oppdaterSykmeldingStatus(
                    listOf(
                        SykmeldingStatusEvent(
                            sykmeldingId,
                            createdDateTime,
                            StatusEvent.APEN
                        )
                    )
                )
            }
            verify(exactly = 0) { database.svarFinnesFraFor(sykmeldingId) }
            verify(exactly = 0) { database.lagreSporsmalOgSvarOgArbeidsgiver(any(), any()) }
        }
    }
})

private fun getSykmeldingTopicEvent(
    sykmeldingId: String,
    sendtTilArbeidsgiverDato: LocalDateTime?,
    kafkaTimeStamp: LocalDateTime,
    createdDateTime: LocalDateTime
): SykmeldingStatusTopicEvent {
    val arbeidsgiverStatus = ArbeidsgiverStatus(sykmeldingId, "1234456789", "123456789", "Bedrift")
    val sykmeldingStatus = SykmeldingStatusTopicEvent(
        sykmeldingId = sykmeldingId,
        arbeidssituasjon = "ARBEIDSTAKER",
        sendtTilArbeidsgiverDato = sendtTilArbeidsgiverDato,
        fravarsPeriode = null,
        harFravaer = null,
        harForsikring = null,
        kafkaTimestamp = kafkaTimeStamp,
        status = StatusEvent.SENDT,
        created = createdDateTime,
        arbeidsgiver = arbeidsgiverStatus
    )
    return sykmeldingStatus
}

// fun getStatus(utgatt: StatusEvent): List<SykmeldingStatus> {
//    return listOf(SykmeldingStatus(LocalDateTime.now(), StatusEvent.APEN, ))
// }
