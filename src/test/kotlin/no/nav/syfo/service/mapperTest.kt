package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Instant
import java.time.LocalDate
import no.nav.syfo.model.FravarsPeriode
import no.nav.syfo.model.Mapper
import no.nav.syfo.objectMapper
import no.nav.syfo.utils.getFileAsString
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class mapperTest : Spek({
    describe("Map from topic") {
        it("map to Map<String, String?>", timeout = 9999999999L) {

            val mapString = getFileAsString("src/test/resources/kafkaSykmelding.json")

            val newMap = objectMapper.readValue<Map<String, Any?>>(mapString)
            val fravarsPerioder = Mapper.getFravaersPeriode(newMap, true)
            val topicEvent = Mapper.mapToSykmeldingStatusTopicEvent(newMap, Instant.now().toEpochMilli())

            fravarsPerioder shouldEqual listOf(FravarsPeriode(fom = LocalDate.of(2019, 10, 1), tom = LocalDate.of(2019, 10, 2)))
        }
    }
})