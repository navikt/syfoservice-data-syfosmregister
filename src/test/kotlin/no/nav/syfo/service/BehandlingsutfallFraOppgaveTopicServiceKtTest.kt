package no.nav.syfo.service

import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class BehandlingsutfallFraOppgaveTopicServiceKtTest : Spek({
    describe("map toRuleInfo") {
        it("Should map beskrivelse to RuleInfo") {
            val ruleMessage = "Sykmeldingens fom-dato er inntil 3 år tilbake i tid og årsak for tilbakedatering er angitt."
            val ruleInfo = mapOppgaveTilRegler("Manuell behandling av sykmelding grunnet følgende regler: (Sykmeldingens fom-dato er inntil 3 år tilbake i tid og årsak for tilbakedatering er angitt.)")
            ruleInfo.first() shouldEqual RuleInfo(ruleMessage, ruleMessage, ruleMessage, Status.MANUAL_PROCESSING)
        }
    }
})
