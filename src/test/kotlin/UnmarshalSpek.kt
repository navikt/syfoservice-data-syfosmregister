import java.io.StringReader
import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.utils.fellesformatUnmarshaller
import no.nav.syfo.utils.getFileAsString
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object UnmarshalSpek : Spek({
    describe("Testing unmarshaller") {

        it("Test unmarshal dates testsett 1") {
            val healthInformation =
                fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/helseopplysninger-ISO-8859-1.xml"))) as HelseOpplysningerArbeidsuforhet
            val expectedFomDate = LocalDate.of(2017, 9, 1)
            val expectedTomDate = LocalDate.of(2017, 10, 27)

            expectedFomDate shouldEqual healthInformation.aktivitet.periode.first().periodeFOMDato
            expectedTomDate shouldEqual healthInformation.aktivitet.periode.first().periodeTOMDato
        }
        it("test ") {
            val string = "2016-11-25"
            if (string.length > 10) {
                LocalDateTime.parse(string.substring(0, 19))
            } else {
                LocalDate.parse(string).atTime(12, 0)
            }
        }
    }
})
