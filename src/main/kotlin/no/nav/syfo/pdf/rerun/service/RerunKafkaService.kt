package no.nav.syfo.pdf.rerun.service

import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.model.Behandlingsutfall
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.pdf.rerun.api.RerunRequest
import no.nav.syfo.pdf.rerun.database.erBehandlingsutfallLagret
import no.nav.syfo.pdf.rerun.database.getSykmeldingerByIds
import no.nav.syfo.pdf.rerun.database.opprettBehandlingsutfall
import no.nav.syfo.pdf.rerun.kafka.RerunKafkaProducer
import org.slf4j.LoggerFactory

data class RerunKafkaMessage(val receivedSykmelding: ReceivedSykmelding, val validationResult: ValidationResult)

class RerunKafkaService(private val database: DatabaseInterfacePostgres, private val rerunKafkaProducer: RerunKafkaProducer) {

    private val log = LoggerFactory.getLogger(RerunKafkaService::class.java)

    fun rerun(rerunRequest: RerunRequest): List<String> {
        log.info("Got list with {} sykmeldinger", rerunRequest.ids.size)
        val receivedSykmelding = database.getSykmeldingerByIds(rerunRequest.ids)
        log.info("Got {} sykmeldinger from database", receivedSykmelding.size)

        val listOk = mutableListOf<String>()

        receivedSykmelding.forEach {
            publishToKafka(RerunKafkaMessage(receivedSykmelding = it, validationResult = rerunRequest.behandlingsutfall))
            if (database.erBehandlingsutfallLagret(it.sykmelding.id)) {
                log.info(
                    "Behandlingsutfall for sykmelding med id {} er allerede lagret i databasen", it.sykmelding.id
                )
            } else {
                database.opprettBehandlingsutfall(
                    Behandlingsutfall(
                        id = it.sykmelding.id,
                        behandlingsutfall = rerunRequest.behandlingsutfall
                    )
                )
                log.info("Behandlingsutfall lagret i databasen, sykmeldingId: {}", it.sykmelding.id)
            }
        }
        return listOk
    }

    private fun publishToKafka(rerunKafkaMessage: RerunKafkaMessage) {
        log.info("Publishing receivedSykmeling to reruntopic: ${rerunKafkaMessage.receivedSykmelding.sykmelding.id}")
        rerunKafkaProducer.publishToKafka(rerunKafkaMessage)
    }
}
