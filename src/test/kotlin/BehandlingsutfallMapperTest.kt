import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object BehandlingsutfallMapperTest : Spek({
    val behandlingsutfallJson = "{\"status\": \"INVALID\", \"ruleHits\": [{\"ruleName\": \"TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING\", \"messageForUser\": \"Sykmeldingen er tilbakedatert uten at det er begrunnet.\", \"messageForSender\": \"Første sykmelding er tilbakedatert mer enn det som er tillatt.\"}]}"

    describe("Tester mapping av rulestatus i behandlingsutfall") {
        it("Skal ikke feile hvis ruleStatus mangler") {

            val validationResult: ValidationResult = objectMapper.readValue(behandlingsutfallJson)

            validationResult.status shouldEqual Status.INVALID
            validationResult.ruleHits.size shouldEqual 1
            validationResult.ruleHits[0].ruleName shouldEqual "TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING"
            validationResult.ruleHits[0].messageForUser shouldEqual "Sykmeldingen er tilbakedatert uten at det er begrunnet."
            validationResult.ruleHits[0].messageForSender shouldEqual "Første sykmelding er tilbakedatert mer enn det som er tillatt."
        }
    }
})
