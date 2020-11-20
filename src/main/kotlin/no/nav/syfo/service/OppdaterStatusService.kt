package no.nav.syfo.service

import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.aksessering.db.oracle.settTilNy
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.model.sykmeldingstatus.STATUS_APEN
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.persistering.db.postgres.hentSykmeldingMedId
import no.nav.syfo.sykmelding.SykmeldingStatusKafkaProducer

class OppdaterStatusService(
    private val databaseoracle: DatabaseOracle,
    private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer,
    private val databasePostgres: DatabasePostgres
) {

    val sykmeldingId = "121f00ee-f30f-4f19-9a67-38c6b1a97bd2"

    fun start() {
        val sykmelding = databasePostgres.connection.hentSykmeldingMedId(sykmeldingId)
        if (sykmelding != null) {
            log.info("oppdaterer status for sykmeldingid {}", sykmeldingId)
            val sykmeldingStatusKafkaEventDTO = SykmeldingStatusKafkaEventDTO(
                sykmeldingId = sykmeldingId,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                statusEvent = STATUS_APEN,
                arbeidsgiver = null,
                sporsmals = null
            )
            sykmeldingStatusKafkaProducer.send(sykmeldingStatusKafkaEventDTO, "migrering", sykmelding.sykmeldingsopplysninger.pasientFnr)
            log.info("Sendt statusendring")
            databaseoracle.settTilNy(sykmeldingId)
        } else {
            log.info("fant ikke sykmelding med id {}", sykmeldingId)
        }
    }
}
