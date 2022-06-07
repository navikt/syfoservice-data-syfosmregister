package no.nav.syfo.service

import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.model.sykmeldingstatus.STATUS_APEN
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.persistering.db.postgres.hentSykmeldingMedId
import no.nav.syfo.sykmelding.SykmeldingStatusKafkaProducer
import java.time.OffsetDateTime
import java.time.ZoneOffset

class GjenapneSykmeldingService(
    private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer,
    private val databasePostgres: DatabasePostgres
) {
    fun gjenapneSykmelding(sykmeldingId: String) {
        val sykmelding = databasePostgres.connection.hentSykmeldingMedId(sykmeldingId)
        if (sykmelding != null) {
            log.info("Gjenåpner sykmelding med sykmeldingid {}", sykmeldingId)
            val sykmeldingStatusKafkaEventDTO = SykmeldingStatusKafkaEventDTO(
                sykmeldingId = sykmeldingId,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                statusEvent = STATUS_APEN,
                arbeidsgiver = null,
                sporsmals = null
            )
            sykmeldingStatusKafkaProducer.send(sykmeldingStatusKafkaEventDTO, "migrering", sykmelding.sykmeldingsopplysninger.pasientFnr)
            log.info("Sendt statusendring")
        } else {
            log.info("fant ikke sykmelding med id {}", sykmeldingId)
        }
    }
}
