package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.utils.getFileAsString
import org.amshove.kluent.shouldBeEqualTo

class OppgaveTilReglerTest : FunSpec({
    xcontext("Mapper oppgavebeskrivelse riktig") {
        test("Mapper oppgavebeskrivelse riktig") {
            val ruleMap =
                objectMapper.readValue<Map<String, RuleInfo>>(getFileAsString("src/main/resources/ruleMap.json"))

            val results = ValidationResult(
                Status.MANUAL_PROCESSING,
                listOf(
                    RuleInfo(
                        "regelnavn",
                        "Hvis perioden er avsluttet (AA",
                        "melding til bruker",
                        Status.MANUAL_PROCESSING
                    ),
                    RuleInfo(
                        "regelnavn2",
                        "vi kan ikke automatisk oppdatere Infotrygd",
                        "melding til bruker2",
                        Status.MANUAL_PROCESSING
                    )
                )
            )
            val regelliste = mapOppgaveTilRegler(
                "Manuell behandling av sykmelding grunnet følgende regler: ${
                results.ruleHits.joinToString(
                    ", ",
                    "(",
                    ")"
                ) { it.messageForSender }
                }",
                ruleMap
            )

            regelliste.size shouldBeEqualTo 1
            regelliste[0] shouldBeEqualTo RuleInfo(
                "PERIOD_FOR_AA_ENDED",
                "Hvis perioden er avsluttet (AA)",
                "Hvis perioden er avsluttet (AA)",
                Status.MANUAL_PROCESSING
            )
        }
    }
})
