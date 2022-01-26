package no.nav.syfo.legeerklaring

import io.kotest.core.spec.style.FunSpec
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.legeerklaring.LegeerklaringMapper.Companion.getAdjustedXml
import no.nav.syfo.legeerklaring.util.extractLegeerklaering
import no.nav.syfo.legeerklaring.util.sha256hashstring
import no.nav.syfo.objectMapper
import no.nav.syfo.utils.fellesformatMarshaller
import no.nav.syfo.utils.fellesformatUnmarshaller
import no.nav.syfo.utils.getFileAsString
import no.nav.syfo.utils.toString
import org.amshove.kluent.shouldBeEqualTo
import java.io.File
import java.io.StringReader

class TestParsingLegeerklaring : FunSpec({

    xcontext("Test parsing of legeerklaring") {
        test("Skriv legeerklaring til jsonb") {
            val inputMessageText = getFileAsString("src/test/resources/legeerklearing.xml")
            val fellesformat =
                fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat

            File("src/test/resources/lekeerklaringjson.txt").writeText(objectMapper.writeValueAsString(fellesformat))
        }
        test("parse json fellesformat") {
            val inputString = getFileAsString("src/test/resources/lekeerklaringjson.txt")
            val xmlFellesformat = LegeerklaringMapper.mapToXml(inputString)

            File("src/test/resources/legeerklaringnyxml.xml").writeText(fellesformatMarshaller.toString(xmlFellesformat))
        }

        test("test dump and original") {
            val dump = LegeerklaringMapper.mapToXml(getFileAsString("src/test/resources/lekeerklaringjson.txt"))
            val original = getFileAsString("src/test/resources/legeerklearing.xml")
            val simpleKafkaMessage = SimpleLegeerklaeringKafkaMessage(SimpleReceivedLegeerklaeering(original))
            val adjustedXml = getAdjustedXml(simpleKafkaMessage)

            sha256hashstring(extractLegeerklaering(dump)) shouldBeEqualTo sha256hashstring(
                extractLegeerklaering(
                    adjustedXml
                )
            )
        }
    }
})
