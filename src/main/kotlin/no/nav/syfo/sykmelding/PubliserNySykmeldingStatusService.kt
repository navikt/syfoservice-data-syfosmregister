package no.nav.syfo.sykmelding

import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_APEN
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.persistering.db.postgres.getEnkelSykmelding
import no.nav.syfo.sykmelding.kafka.model.MottattSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.toEnkelSykmelding
import no.nav.syfo.sykmelding.model.EnkelSykmeldingDbModel
import java.time.OffsetDateTime
import java.time.ZoneOffset

class PubliserNySykmeldingStatusService(
    private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer,
    private val mottattSykmeldingProudcer: MottattSykmeldingKafkaProducer,
    private val databasePostgres: DatabasePostgres
) {
    val sykmeldingId = ""

    fun start() {
        val sykmelding = databasePostgres.connection.getEnkelSykmelding(sykmeldingId)
        if (sykmelding != null) {
            log.info("oppdaterer status for sykmeldingid {}", sykmeldingId)
            val sykmeldingStatusKafkaEventDTO = SykmeldingStatusKafkaEventDTO(
                sykmeldingId = sykmeldingId,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                statusEvent = STATUS_APEN,
                arbeidsgiver = null,
                sporsmals = null
            )
            sykmeldingStatusKafkaProducer.send(
                sykmeldingStatusKafkaEventDTO,
                "macgyver",
                sykmelding.fnr
            )
            log.info("Sendt statusendring")
            mottattSykmeldingProudcer.sendSykmelding(mapTilMottattSykmelding(sykmelding))
            log.info("Sendt til mottatt-topic")
        } else {
            log.info("fant ikke sykmelding med id {}", sykmeldingId)
        }
    }

    private fun mapTilMottattSykmelding(enkelSykmeldingDbModel: EnkelSykmeldingDbModel): MottattSykmeldingKafkaMessage {
        return MottattSykmeldingKafkaMessage(
            sykmelding = enkelSykmeldingDbModel.toEnkelSykmelding(),
            kafkaMetadata = KafkaMetadataDTO(
                sykmeldingId = enkelSykmeldingDbModel.id,
                timestamp = enkelSykmeldingDbModel.mottattTidspunkt.atOffset(ZoneOffset.UTC),
                fnr = enkelSykmeldingDbModel.fnr,
                source = "macgyver"

            )
        )
    }
}