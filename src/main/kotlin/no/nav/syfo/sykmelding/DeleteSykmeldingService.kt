package no.nav.syfo.sykmelding

import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.Environment
import no.nav.syfo.VaultConfig
import no.nav.syfo.VaultCredentials
import no.nav.syfo.aksessering.db.oracle.settTilSlettet
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.getVaultServiceUser
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.log
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_SLETTET
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.persistering.db.postgres.hentSykmeldingMedId
import no.nav.syfo.utils.JacksonKafkaSerializer
import no.nav.syfo.utils.getFileAsString
import no.nav.syfo.vault.RenewVaultService
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class DeleteSykmeldingService(
    environment: Environment,
    applicationState: ApplicationState
) {
    private val databasePostgres: DatabasePostgres
    private val databaseOracle: DatabaseOracle
    private val topic = environment.sykmeldingStatusTopic
    private val kafkaProducer: KafkaProducer<String, SykmeldingStatusKafkaMessageDTO>
    init {
        val vaultService = getVaultServiceUser()
        val kafkaBaseConfig = loadBaseConfig(environment, vaultService)
        val producerProperties = kafkaBaseConfig.toProducerConfig(
            environment.applicationName,
            valueSerializer = JacksonKafkaSerializer::class
        )
        val vaultConfig = VaultConfig(
            jdbcUrl = getFileAsString("/secrets/syfoservice/config/jdbc_url")
        )
        val syfoserviceVaultSecrets = VaultCredentials(
            databasePassword = getFileAsString("/secrets/syfoservice/credentials/password"),
            databaseUsername = getFileAsString("/secrets/syfoservice/credentials/username")
        )
        val vaultCredentialService = VaultCredentialService()
        RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
        databasePostgres = DatabasePostgres(environment, vaultCredentialService)
        databaseOracle = DatabaseOracle(vaultConfig, syfoserviceVaultSecrets)
        kafkaProducer = KafkaProducer(producerProperties)
    }
    private val sykmeldingIDs = listOf("4c170337-afce-4d2e-a3a8-a4b824798725")

    fun deleteSykmelding() {
        try {
            sykmeldingIDs.forEach { sykmeldingID ->
                val sykmelding = databasePostgres.connection.hentSykmeldingMedId(sykmeldingID)?.sykmeldingsopplysninger
                databaseOracle.settTilSlettet(sykmeldingID)
                kafkaProducer.send(ProducerRecord(topic, sykmeldingID, SykmeldingStatusKafkaMessageDTO(
                    KafkaMetadataDTO(sykmeldingID, OffsetDateTime.now(ZoneOffset.UTC), sykmelding!!.pasientFnr, "syfosmregister"),
                    SykmeldingStatusKafkaEventDTO(sykmeldingID, OffsetDateTime.now(ZoneOffset.UTC), STATUS_SLETTET, null, null)
                ))).get()
            }
        } catch (ex: Exception) {
            log.error("Error when deleting sykmelding", ex)
        }
    }
}
