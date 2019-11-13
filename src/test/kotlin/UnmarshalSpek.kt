import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.utils.fellesformatUnmarshaller
import no.nav.syfo.utils.getFileAsString
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader
import java.time.LocalDate

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
    }
})
