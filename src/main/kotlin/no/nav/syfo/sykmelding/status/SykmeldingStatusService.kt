package no.nav.syfo.sykmelding.status

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.getVaultServiceUser
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.log
import no.nav.syfo.model.SykmeldingStatusEvent
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.persistering.db.postgres.getStatusesForSykmelding
import no.nav.syfo.persistering.db.postgres.getSykmeldingIds
import no.nav.syfo.persistering.db.postgres.oppdaterSykmeldingStatusTimestamp
import no.nav.syfo.utils.JacksonKafkaSerializer
import no.nav.syfo.vault.RenewVaultService
import org.apache.kafka.clients.producer.KafkaProducer

class SykmeldingStatusService(
    private val applicationState: ApplicationState,
    private val environment: Environment
) {
    private val kafkaPublisher: KafkaProducer<String, SykmeldingStatusKafkaMessageDTO>
    private val databasePostgres: DatabasePostgres
    private val topic = environment.sykmeldingStatusBackupTopic
    init {
        val vaultServiceuser = getVaultServiceUser()
        val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
        val producerProperties =
            kafkaBaseConfig.toProducerConfig(environment.applicationName, valueSerializer = JacksonKafkaSerializer::class)
        kafkaPublisher = KafkaProducer(producerProperties)
        val vaultCredentialService = VaultCredentialService()
        RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
        databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    }

    suspend fun startUpdateTimestamps() {
        var updateCounter = 0
        var lastMottattDato = LocalDate.parse(environment.lastIndexSyfosmregister)
        val loggingJob = GlobalScope.launch {
            while (applicationState.ready) {
                log.info(
                    "Antall sykmeldinger prosessert: {}, lastMottattDato {}",
                    updateCounter,
                    lastMottattDato
                )
                delay(30_000)
            }
        }

        while (updateCounter == 0) {
            val sykmeldingIds = databasePostgres.connection.getSykmeldingIds(lastMottattDato)
            sykmeldingIds.forEach { it ->
                val statuses = databasePostgres.getStatusesForSykmelding(it)
                var first = true
                val newStatuses = statuses.map {
                    val mapped: SykmeldingStatusEvent
                    if (it.timestamp == null) {
                        if (first) {
                            mapped = it.copy(timestamp = it.eventTimestamp.atOffset(ZoneOffset.UTC))
                        } else {
                            mapped = it.copy(timestamp = it.eventTimestamp.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime())
                        }
                    } else {
                        mapped = it
                    }
                    first = false
                    mapped
                }
                var lastTimestamp = OffsetDateTime.MIN
                newStatuses.forEach {
                    if (it.timestamp!!.isBefore(lastTimestamp)) {
                        log.error("Inncorrect timestamp order for sykmelding {}", it.sykmeldingId)
                    }
                    lastTimestamp = it.timestamp
                }
                updateCounter += newStatuses.size
                databasePostgres.connection.oppdaterSykmeldingStatusTimestamp(newStatuses)
            }
            lastMottattDato = lastMottattDato.plusDays(1)
        }
        log.info(
            "Ferdig med alle sykmeldingene, totalt {}, siste dato {}",
            updateCounter,
            lastMottattDato
        )
        loggingJob.cancelAndJoin()
    }
}
