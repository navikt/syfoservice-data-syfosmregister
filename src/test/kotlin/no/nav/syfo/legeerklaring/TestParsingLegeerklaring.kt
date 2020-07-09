package no.nav.syfo.legeerklaring

import java.io.File
import java.io.StringReader
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.objectMapper
import no.nav.syfo.utils.fellesformatMarshaller
import no.nav.syfo.utils.fellesformatUnmarshaller
import no.nav.syfo.utils.getFileAsString
import no.nav.syfo.utils.toString
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class TestParsingLegeerklaring : Spek({

    describe("Test parsing of legeerklaring") {
        it("Skriv legeerklaring til jsonb") {
            val inputMessageText = getFileAsString("src/test/resources/legeerklearing.xml")
            val fellesformat =
                fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat

            File("src/test/resources/lekeerklaringjson.txt").writeText(objectMapper.writeValueAsString(fellesformat))
        }
        it("parse json fellesformat") {
            val inputString = getFileAsString("src/test/resources/lekeerklaringjson.txt")
            val xmlFellesformat = LegeerklaringMapper.mapToXml(inputString)

            File("src/test/resources/legeerklaringnyxml.xml").writeText(fellesformatMarshaller.toString(xmlFellesformat))
        }
    }
})
