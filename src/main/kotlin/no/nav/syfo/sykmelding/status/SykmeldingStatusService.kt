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
import no.nav.syfo.model.ArbeidsgiverStatus
import no.nav.syfo.model.ShortName
import no.nav.syfo.model.Sporsmal
import no.nav.syfo.model.StatusEvent
import no.nav.syfo.model.Svartype
import no.nav.syfo.model.SykmeldingStatusEvent
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.persistering.db.postgres.getStatusesForSykmelding
import no.nav.syfo.persistering.db.postgres.getSykmeldingIds
import no.nav.syfo.persistering.db.postgres.getSykmeldingIdsAndFnr
import no.nav.syfo.persistering.db.postgres.hentArbeidsgiverStatus
import no.nav.syfo.persistering.db.postgres.hentSporsmalOgSvar
import no.nav.syfo.persistering.db.postgres.oppdaterSykmeldingStatusTimestamp
import no.nav.syfo.sykmelding.model.SykmeldingIdAndFnr
import no.nav.syfo.utils.JacksonKafkaSerializer
import no.nav.syfo.vault.RenewVaultService
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

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
            kafkaBaseConfig.toProducerConfig(
                environment.applicationName,
                valueSerializer = JacksonKafkaSerializer::class
            )
        kafkaPublisher = KafkaProducer(producerProperties)
        val vaultCredentialService = VaultCredentialService()
        RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
        databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    }

    suspend fun publishToStatusTopic() {
        var totalSykmeldinger = 0
        var totalStatuses = 0
        var onlyOneStatus = 0
        var lastMottattDato = LocalDate.parse(environment.lastIndexSyfosmregister)
        val loggingJob = GlobalScope.launch {
            while (applicationState.ready) {
                log.info(
                    "Antall sykmeldinger prosessert: {}, lastMottattDato {}, statuser {}, with only one {}",
                    totalSykmeldinger,
                    lastMottattDato,
                    totalStatuses,
                    onlyOneStatus
                )
                delay(30_000)
            }
        }
        log.info("Starting")
        try {
            while (lastMottattDato.isBefore(LocalDate.now().plusDays(1))) {
                val sykmeldingsIds = databasePostgres.connection.getSykmeldingIdsAndFnr(lastMottattDato)
                sykmeldingsIds.forEach {
                    val statuses = databasePostgres.getStatusesForSykmelding(it.sykmeldingId)
                    totalSykmeldinger++
                    if (statuses.isEmpty()) {
                        log.info("no statuses found for sykmeldingId {}", it)
                    }
                    val firstStatus = statuses.first()
                    val lastStatus = statuses.last()

                    val firstSykmeldingStatusKafkaMessageDTO = getSykmeldingStatusKafkaMessage(it, firstStatus)
                    kafkaPublisher.send(ProducerRecord(topic, it.sykmeldingId, firstSykmeldingStatusKafkaMessageDTO))

                    if (lastStatus != firstStatus && lastStatus.event != StatusEvent.APEN) {
                        totalStatuses += 2
                        val secondStatus = getSykmeldingStatusKafkaMessage(it, lastStatus)
                        kafkaPublisher.send(ProducerRecord(topic, it.sykmeldingId, secondStatus))
                    } else {
                        onlyOneStatus++
                    }
                }
                lastMottattDato.plusDays(1)
            }
        } catch (ex: Exception) {
            log.info("Noe gikk galt", ex)
        }

        log.info(
            "Ferdig, sykmeldinger prosessert: {}, lastMottattDato {}, statuser {}, with only one {}",
            totalSykmeldinger,
            lastMottattDato,
            totalStatuses,
            onlyOneStatus
        )
        loggingJob.cancelAndJoin()
    }

    private fun getSykmeldingStatusKafkaMessage(
        it: SykmeldingIdAndFnr,
        firstStatus: SykmeldingStatusEvent
    ): SykmeldingStatusKafkaMessageDTO {
        val firstSykmeldingStatusEvent = SykmeldingStatusKafkaEventDTO(
            it.sykmeldingId,
            firstStatus.timestamp!!,
            firstStatus.event.toDto(),
            getArbeidsgiverStatusDto(firstStatus),
            getSporsmals(firstStatus)
        )

        val firstSykmeldingStatusKafkaMessageDTO = SykmeldingStatusKafkaMessageDTO(
            kafkaMetadata = KafkaMetadataDTO(
                sykmeldingId = it.sykmeldingId,
                fnr = it.fnr,
                timestamp = firstStatus.timestamp!!,
                source = "syfoservice"
            ),
            event = firstSykmeldingStatusEvent
        )
        return firstSykmeldingStatusKafkaMessageDTO
    }

    private fun getSporsmals(status: SykmeldingStatusEvent): List<SporsmalOgSvarDTO>? {
        return when (status.event) {
            StatusEvent.SENDT -> databasePostgres.connection.hentSporsmalOgSvar(status.sykmeldingId).toDto()
            StatusEvent.BEKREFTET -> databasePostgres.connection.hentSporsmalOgSvar(status.sykmeldingId).toDto()
            else -> null
        }
    }

    private fun getArbeidsgiverStatusDto(status: SykmeldingStatusEvent): ArbeidsgiverStatusDTO? {
        return when (status.event) {
            StatusEvent.SENDT -> databasePostgres.connection.hentArbeidsgiverStatus(status.sykmeldingId).first().toDto()
            else -> null
        }
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
            while (lastMottattDato.isBefore(LocalDate.of(2020, 2, 19).plusDays(1))) {
                val sykmeldingIds = databasePostgres.connection.getSykmeldingIds(lastMottattDato)
                sykmeldingIds.forEach { it ->
                    val statuses = databasePostgres.getStatusesForSykmelding(it)
                    var first = true
                    var needToUpdate = false
                    var lastTimestamp = OffsetDateTime.MIN
                    val newStatuses = statuses.map {
                        val mapped: SykmeldingStatusEvent
                        if (it.timestamp == null) {
                            needToUpdate = true
                            if (first) {
                                mapped = it.copy(timestamp = it.eventTimestamp.atOffset(ZoneOffset.UTC))
                            } else {
                                var timestamp =
                                    it.eventTimestamp.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC)
                                        .toOffsetDateTime()
                                mapped = if (timestamp.isBefore(lastTimestamp)) {
                                    it.copy(timestamp = it.eventTimestamp.atOffset(ZoneOffset.UTC))
                                } else {
                                    it.copy(
                                        timestamp = it.eventTimestamp.atZone(ZoneId.systemDefault())
                                            .withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
                                    )
                                }
                            }
                        } else {
                            mapped = it
                        }
                        lastTimestamp = mapped.timestamp
                        first = false
                        mapped
                    }
                    var valid = checkTimestamps(newStatuses)
                    if (!valid) {
                        throw RuntimeException("incorrect timestamps")
                    }
                    if (needToUpdate) {
                        databasePostgres.connection.oppdaterSykmeldingStatusTimestamp(newStatuses)
                    }
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

private fun List<Sporsmal>.toDto(): List<SporsmalOgSvarDTO>? {
    return map {
        SporsmalOgSvarDTO(
            tekst = it.tekst,
            shortName = it.shortName.toDto(),
            svar = it.svar.svar,
            svartype = it.svar.svartype.toDto()
        )
    }
}

private fun Svartype.toDto(): SvartypeDTO {
    return when (this) {
        Svartype.ARBEIDSSITUASJON -> SvartypeDTO.ARBEIDSSITUASJON
        Svartype.PERIODER -> SvartypeDTO.PERIODER
        Svartype.JA_NEI -> SvartypeDTO.JA_NEI
    }
}

private fun ShortName.toDto(): ShortNameDTO {
    return when (this) {
        ShortName.ARBEIDSSITUASJON -> ShortNameDTO.ARBEIDSSITUASJON
        ShortName.NY_NARMESTE_LEDER -> ShortNameDTO.NY_NARMESTE_LEDER
        ShortName.FRAVAER -> ShortNameDTO.FRAVAER
        ShortName.PERIODE -> ShortNameDTO.PERIODE
        ShortName.FORSIKRING -> ShortNameDTO.FORSIKRING
    }
}

private fun ArbeidsgiverStatus.toDto(): ArbeidsgiverStatusDTO? {
    return ArbeidsgiverStatusDTO(
        orgnummer = this.orgnummer,
        juridiskOrgnummer = this.juridiskOrgnummer,
        orgNavn = this.orgnavn
    )
}

private fun StatusEvent.toDto(): StatusEventDTO {
    return when (this) {
        StatusEvent.APEN -> StatusEventDTO.APEN
        StatusEvent.AVBRUTT -> StatusEventDTO.AVBRUTT
        StatusEvent.UTGATT -> StatusEventDTO.UTGATT
        StatusEvent.SENDT -> StatusEventDTO.SENDT
        StatusEvent.BEKREFTET -> StatusEventDTO.BEKREFTET

        else -> throw IllegalArgumentException("Got Illegal status ${this.name}")
    }
}
