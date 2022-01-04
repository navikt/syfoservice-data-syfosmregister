package no.nav.syfo.sykmelding

import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_APEN
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.persistering.db.postgres.getEnkelSykmeldingUtenStatus
import no.nav.syfo.sykmelding.aivenmigrering.SykmeldingV2KafkaMessage
import no.nav.syfo.sykmelding.aivenmigrering.SykmeldingV2KafkaProducer
import no.nav.syfo.sykmelding.kafka.model.toArbeidsgiverSykmelding
import no.nav.syfo.sykmelding.model.EnkelSykmeldingDbModel
import java.time.OffsetDateTime
import java.time.ZoneOffset

class PubliserNySykmeldingStatusService(
    private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer,
    private val mottattSykmeldingProudcer: SykmeldingV2KafkaProducer,
    private val databasePostgres: DatabasePostgres,
    private val mottattSykmeldingTopic: String
) {
    val sykmeldingId = ""

    fun start() {
        val sykmelding = databasePostgres.connection.getEnkelSykmeldingUtenStatus(sykmeldingId)
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
            mottattSykmeldingProudcer.sendSykmelding(
                sykmeldingKafkaMessage = mapTilMottattSykmelding(sykmelding),
                sykmeldingId = sykmeldingId,
                topic = mottattSykmeldingTopic
            )
            log.info("Sendt til mottatt-topic")
        } else {
            log.info("fant ikke sykmelding med id {}", sykmeldingId)
        }
    }

    private fun mapTilMottattSykmelding(enkelSykmeldingDbModel: EnkelSykmeldingDbModel): SykmeldingV2KafkaMessage {
        return SykmeldingV2KafkaMessage(
            sykmelding = enkelSykmeldingDbModel.toArbeidsgiverSykmelding(),
            kafkaMetadata = KafkaMetadataDTO(
                sykmeldingId = enkelSykmeldingDbModel.id,
                timestamp = enkelSykmeldingDbModel.mottattTidspunkt.atOffset(ZoneOffset.UTC),
                fnr = enkelSykmeldingDbModel.fnr,
                source = "macgyver"
            ),
            event = null
        )
    }
}
