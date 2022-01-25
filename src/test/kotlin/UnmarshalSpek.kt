
import io.kotest.core.spec.style.FunSpec
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.utils.fellesformatUnmarshaller
import no.nav.syfo.utils.getFileAsString
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import java.io.StringReader
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

object UnmarshalSpek : FunSpec({
    context("Testing unmarshaller") {

        test("Test unmarshal dates testsett 1") {
            val healthInformation =
                fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/helseopplysninger-ISO-8859-1.xml"))) as HelseOpplysningerArbeidsuforhet
            val expectedFomDate = LocalDate.of(2017, 9, 1)
            val expectedTomDate = LocalDate.of(2017, 10, 27)

            expectedFomDate shouldBeEqualTo healthInformation.aktivitet.periode.first().periodeFOMDato
            expectedTomDate shouldBeEqualTo healthInformation.aktivitet.periode.first().periodeTOMDato
        }
        test("test ") {
            val string = "2016-11-25"
            if (string.length > 10) {
                LocalDateTime.parse(string.substring(0, 19))
            } else {
                LocalDate.parse(string).atTime(12, 0)
            }
        }

        test("test timestamp") {
            val string = Timestamp.valueOf(LocalDateTime.now()).toString()
            var r = Timestamp.valueOf(string).toLocalDateTime()
            r shouldNotBe null
        }
    }
})
