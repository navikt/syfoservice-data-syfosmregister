import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import org.amshove.kluent.shouldBeEqualTo

object BehandlingsutfallMapperTest : FunSpec({
    val behandlingsutfallJson = "{\"status\": \"INVALID\", \"ruleHits\": [{\"ruleName\": \"TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING\", \"messageForUser\": \"Sykmeldingen er tilbakedatert uten at det er begrunnet.\", \"messageForSender\": \"Første sykmelding er tilbakedatert mer enn det som er tillatt.\"}]}"

    xtest("Skal ikke feile hvis ruleStatus mangler") {

        val validationResult: ValidationResult = objectMapper.readValue(behandlingsutfallJson)

        validationResult.status shouldBeEqualTo Status.INVALID
        validationResult.ruleHits.size shouldBeEqualTo 1
        validationResult.ruleHits[0].ruleName shouldBeEqualTo "TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING"
        validationResult.ruleHits[0].messageForUser shouldBeEqualTo "Sykmeldingen er tilbakedatert uten at det er begrunnet."
        validationResult.ruleHits[0].messageForSender shouldBeEqualTo "Første sykmelding er tilbakedatert mer enn det som er tillatt."
    }
})
