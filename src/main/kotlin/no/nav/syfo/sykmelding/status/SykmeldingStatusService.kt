package no.nav.syfo.sykmelding.status

import java.lang.RuntimeException
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
                delay(120_000)
            }
        }
        try {
            while (lastMottattDato.isBefore(LocalDate.of(2020, 2, 18).plusDays(1))) {
                val sykmeldingIds = databasePostgres.connection.getSykmeldingIds(lastMottattDato)
                sykmeldingIds.forEach { it ->
                    val statuses = databasePostgres.getStatusesForSykmelding(it)
                    var first = true
                    val newStatuses = statuses.map {
                        val mapped: SykmeldingStatusEvent
                        if (it.timestamp == null) {
                            if (first && lastMottattDato.isAfter(LocalDate.of(2019, 9, 13))) {
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
                    var valid = checkTimestamps(newStatuses)
                    if (!valid) {
                        log.info("not valid timestamp order for sykmelding {}, fixing first timestamp", it)
                        newStatuses[0].timestamp = newStatuses[0].eventTimestamp.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
                        valid = checkTimestamps(newStatuses)
                    }
                    if (!valid) {
                        throw RuntimeException("timestamps not valid for sykmelding $it")
                    }
                    databasePostgres.connection.oppdaterSykmeldingStatusTimestamp(newStatuses)
                }
                updateCounter += sykmeldingIds.size
                lastMottattDato = lastMottattDato.plusDays(1)
            }
        } catch (ex: Exception) {
            log.info("Stopping due to exception")
        }

        log.info(
            "Ferdig med alle sykmeldingene, totalt {}, siste dato {}",
            updateCounter,
            lastMottattDato
        )
        loggingJob.cancelAndJoin()
    }

    private fun checkTimestamps(newStatuses: List<SykmeldingStatusEvent>): Boolean {
        var lastTimestamp = OffsetDateTime.MIN
        newStatuses.forEach {
            if (it.timestamp!!.isBefore(lastTimestamp)) {
                log.info("Incorrect timestamp order for sykmelding {}", it.sykmeldingId)
                return false
            }
            lastTimestamp = it.timestamp
        }
        return true
    }
}
