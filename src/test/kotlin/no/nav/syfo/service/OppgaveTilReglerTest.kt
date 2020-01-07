package no.nav.syfo.service

import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class OppgaveTilReglerTest : Spek({
    describe("Mapper oppgavebeskrivelse riktig") {
        it("Mapper oppgavebeskrivelse riktig") {
            val results = ValidationResult(
                Status.MANUAL_PROCESSING,
                listOf(RuleInfo("regelnavn", "melding til legen", "melding til bruker", Status.MANUAL_PROCESSING),
                    RuleInfo("regelnavn2", "melding til legen2", "melding til bruker2", Status.MANUAL_PROCESSING))
            )
            val regelliste = mapOppgaveTilRegler(
                "Manuell behandling av sykmelding grunnet følgende regler: ${results.ruleHits.joinToString(
                    ", ",
                    "(",
                    ")"
                ) { it.messageForSender }}"
            )

            regelliste.size shouldEqual 2
            // regelliste[0] shouldEqual RuleInfo("regelnavn", "melding til legen", "melding til bruker", Status.MANUAL_PROCESSING)
        }
    }
})
