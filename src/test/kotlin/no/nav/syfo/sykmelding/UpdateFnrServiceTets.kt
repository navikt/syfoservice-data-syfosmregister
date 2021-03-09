package no.nav.syfo.sykmelding

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.pdl.client.model.IdentInformasjon
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.db.updateFnr
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.lang.RuntimeException
import kotlin.test.assertFailsWith
import kotlin.test.expect

class UpdateFnrServiceTets : Spek({


    describe("Test at UpdateFnrService fungerer som forventet") {

        val pdlPersonService = mockk<PdlPersonService>(relaxed = true)

        mockkStatic("no.nav.syfo.sykmelding.db.SyfoSmRegisterKt")
        val db = mockk<DatabaseInterfacePostgres>(relaxed = true)

        val updateFnrService = UpdateFnrService(pdlPersonService, db)
        val accessToken = "accessToken"

        it("Skal oppdatere OK hvis nytt og gammelt fnr er knyttet til samme person") {
            coEvery { pdlPersonService.getPdlPerson(any(), any()) } returns PdlPerson(
                    listOf(
                            IdentInformasjon("12345678913", false, "FOLKEREGISTERIDENT"),
                            IdentInformasjon("12345678912", true, "FOLKEREGISTERIDENT"),
                            IdentInformasjon("12345", false, "AKTORID")
                    ))

            every { db.updateFnr(any(), any()) } returns 1

            runBlocking {
                updateFnrService.updateFnr(
                        accessToken = accessToken,
                        fnr = "12345678912",
                        nyttFnr = "12345678913") shouldEqual true

            }
        }

        it("Skal kaste feil hvis nytt og gammelt fnr ikke er knyttet til samme person") {
            coEvery { pdlPersonService.getPdlPerson(any(), any()) } returns PdlPerson(
                    listOf(
                            IdentInformasjon("12345678913", false, "FOLKEREGISTERIDENT"),
                            IdentInformasjon("12345678912", true, "FOLKEREGISTERIDENT"),
                            IdentInformasjon("12345", false, "AKTORID")
                    ))

            every { db.updateFnr(any(), any()) } returns 1

            runBlocking {
                val assertFailsWith = assertFailsWith<UpdateIdentException> {
                    updateFnrService.updateFnr(
                            accessToken = accessToken,
                            fnr = "12345678912",
                            nyttFnr = "12345678914")
                }
                assertFailsWith.message shouldEqual "Oppdatering av fnr feilet, nyttFnr står ikke som aktivt fnr for aktøren i PDL"
            }
        }

        it("Skal kaste feil hvis fnr ikke er registrert som historisk for person") {
            coEvery { pdlPersonService.getPdlPerson(any(), any()) } returns PdlPerson(
                    listOf(
                            IdentInformasjon("12345678913", false, "FOLKEREGISTERIDENT"),
                            IdentInformasjon("123", true, "FOLKEREGISTERIDENT"),
                            IdentInformasjon("12345", false, "AKTORID")
                    ))

            every { db.updateFnr(any(), any()) } returns 1

            runBlocking {
                val assertFailsWith = assertFailsWith<UpdateIdentException> {
                    updateFnrService.updateFnr(
                            accessToken = accessToken,
                            fnr = "12345678912",
                            nyttFnr = "12345678913")
                }
                assertFailsWith.message shouldEqual "Oppdatering av fnr feilet, fnr er ikke historisk for aktør"
            }
        }



    }

})