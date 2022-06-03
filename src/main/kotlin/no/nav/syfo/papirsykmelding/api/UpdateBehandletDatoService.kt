package no.nav.syfo.papirsykmelding.api

import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.kafka.SykmeldingEndringsloggKafkaProducer
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.hentSykmeldingsdokument
import no.nav.syfo.persistering.db.postgres.updateBehandletTidspunkt
import java.time.LocalDate

class UpdateBehandletDatoService(
    private val databasePostgres: DatabasePostgres,
    private val sykmeldingEndringsloggKafkaProducer: SykmeldingEndringsloggKafkaProducer
) {
    fun updateBehandletDato(sykmeldingId: String, behandletDato: LocalDate) {
        val sykmeldingsdokument = databasePostgres.connection.hentSykmeldingsdokument(sykmeldingId)
        val oppdatertBehandletTidspunkt = behandletDato.atTime(12, 0)

        if (sykmeldingsdokument != null) {
            log.info(
                "Endrer behandletDato fra ${objectMapper.writeValueAsString(sykmeldingsdokument.sykmelding.behandletTidspunkt)}" +
                    " til ${objectMapper.writeValueAsString(oppdatertBehandletTidspunkt)} for id $sykmeldingId"
            )
            sykmeldingEndringsloggKafkaProducer.publishToKafka(sykmeldingsdokument)

            databasePostgres.updateBehandletTidspunkt(sykmeldingId, oppdatertBehandletTidspunkt)

            log.info("BehandletDato er oppdatert")
        } else {
            log.info("Fant ikke sykmelding med id {}", sykmeldingId)
            throw RuntimeException("Fant ikke sykmelding med id $sykmeldingId")
        }
    }
}
