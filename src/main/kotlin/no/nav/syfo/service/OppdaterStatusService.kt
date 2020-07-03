package no.nav.syfo.service

import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.aksessering.db.oracle.settTilNy
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.log
import no.nav.syfo.model.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.SykmeldingStatusKafkaProducer

class OppdaterStatusService(private val databaseoracle: DatabaseOracle, private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer) {

    val sykmeldingId = "4dbe0766-3d59-45e9-a6bf-63c97888abcc"

    fun start(fnr: String) {
        val sykmeldingStatusKafkaEventDTO = SykmeldingStatusKafkaEventDTO(
            sykmeldingId = sykmeldingId,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            statusEvent = StatusEventDTO.APEN,
            arbeidsgiver = null,
            sporsmals = null
        )
        sykmeldingStatusKafkaProducer.send(sykmeldingStatusKafkaEventDTO, "migrering", fnr)
        log.info("Sendt statusendring")
        databaseoracle.settTilNy(sykmeldingId)
    }
}
