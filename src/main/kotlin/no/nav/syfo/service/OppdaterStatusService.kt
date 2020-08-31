package no.nav.syfo.service

import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.aksessering.db.oracle.settTilNy
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.log
import no.nav.syfo.model.sykmeldingstatus.STATUS_APEN
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.SykmeldingStatusKafkaProducer

class OppdaterStatusService(private val databaseoracle: DatabaseOracle, private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer) {

    val sykmeldingId = "a9888b61-6da3-4e6e-a89a-21af5dfc5936"

    fun start(fnr: String) {
        val sykmeldingStatusKafkaEventDTO = SykmeldingStatusKafkaEventDTO(
            sykmeldingId = sykmeldingId,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            statusEvent = STATUS_APEN,
            arbeidsgiver = null,
            sporsmals = null
        )
        sykmeldingStatusKafkaProducer.send(sykmeldingStatusKafkaEventDTO, "migrering", fnr)
        log.info("Sendt statusendring")
        databaseoracle.settTilNy(sykmeldingId)
    }
}
