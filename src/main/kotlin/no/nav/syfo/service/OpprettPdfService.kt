package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Duration
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.kafka.RerunKafkaMessage
import no.nav.syfo.kafka.RerunKafkaMessageKafkaProducer
import no.nav.syfo.log
import no.nav.syfo.model.Status
import no.nav.syfo.model.toReceivedSykmelding
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.SykmeldingDbModel
import no.nav.syfo.persistering.db.postgres.hentSykmeldingMedBehandlingsutfallForId
import org.apache.kafka.clients.consumer.KafkaConsumer

class OpprettPdfService(
    private val applicationState: ApplicationState,
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val kafkaProducer: RerunKafkaMessageKafkaProducer,
    private val idUtenBehandlingsutfallFraBackupTopic: String,
    private val databaseInterfacePostgres: DatabaseInterfacePostgres
) {

    fun run() {
        kafkaConsumer.subscribe(
            listOf(
                idUtenBehandlingsutfallFraBackupTopic
            )
        )
        var counterAll = 0
        var counterOpprettPdf = 0
        var lastCounter = 0
        var lastId = ""
        GlobalScope.launch {
            while (applicationState.ready) {
                if (lastCounter != counterAll) {
                    log.info(
                        "Lest {} id totalt, antall det er opprettet pdf for {}, siste leste id: {}",
                        counterAll, counterOpprettPdf, lastId
                    )
                    lastCounter = counterAll
                }
                delay(30000)
            }
        }
        while (applicationState.ready) {
            val iderFraBackup: List<String> =
                kafkaConsumer.poll(Duration.ofMillis(100))
                .map {
                    objectMapper.readValue<String>(it.value())
                }
            for (id in iderFraBackup) {
                lastId = id
                counterAll++
                try {
                    val sykmeldingMedBehandlingsutfall = databaseInterfacePostgres.connection.hentSykmeldingMedBehandlingsutfallForId(id).firstOrNull()
                    if (sykmeldingMedBehandlingsutfall?.behandlingsutfall != null &&
                        sykmeldingMedBehandlingsutfall.behandlingsutfall.behandlingsutfall.status == Status.OK) {
                        val rerunKafkaMessage = RerunKafkaMessage(toReceivedSykmelding(
                            SykmeldingDbModel(sykmeldingMedBehandlingsutfall.sykmeldingsopplysninger, sykmeldingMedBehandlingsutfall.sykmeldingsdokument)),
                            sykmeldingMedBehandlingsutfall.behandlingsutfall.behandlingsutfall)
                        kafkaProducer.publishToKafka(rerunKafkaMessage)
                        counterOpprettPdf++
                    }
                } catch (ex: Exception) {
                    log.error("Noe gikk galt med id {}", id, ex)
                    applicationState.ready = false
                    break
                }
            }
        }
    }
}
