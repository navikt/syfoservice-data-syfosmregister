package no.nav.syfo.sykmelding

import no.nav.syfo.Environment
import no.nav.syfo.aksessering.db.oracle.settTilSlettet
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.kafka.SykmeldingEndringsloggKafkaProducer
import no.nav.syfo.log
import no.nav.syfo.model.sykmeldingstatus.STATUS_SLETTET
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.persistering.db.postgres.hentSykmeldingMedId
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DeleteSykmeldingService(
    val environment: Environment,
    val databasePostgres: DatabasePostgres,
    val databaseOracle: DatabaseOracle,
    val kafkaProducer: SykmeldingStatusKafkaProducer,
    val endringsloggKafkaProducer: SykmeldingEndringsloggKafkaProducer

) {
    val topic = environment.sykmeldingStatusTopic

    fun deleteSykmelding(sykmeldingID: String) {

        val sykmelding = databasePostgres.connection.hentSykmeldingMedId(sykmeldingID)
        if (sykmelding != null) {
            endringsloggKafkaProducer.publishToKafka(sykmelding.sykmeldingsdokument!!)
            databaseOracle.settTilSlettet(sykmeldingID)
            kafkaProducer.send(
                SykmeldingStatusKafkaEventDTO(
                    sykmeldingID,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    STATUS_SLETTET,
                    null,
                    null
                ),
                "macgyver",
                sykmelding.sykmeldingsopplysninger.pasientFnr
            )
        } else {
            log.info("Could not find symkelding with id $sykmeldingID")
        }
    }
}
