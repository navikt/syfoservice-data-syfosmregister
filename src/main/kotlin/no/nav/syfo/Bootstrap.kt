package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.clients.HttpClients
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.identendring.UpdateFnrService
import no.nav.syfo.kafka.SykmeldingEndringsloggKafkaProducer
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.Sykmeldingsdokument
import no.nav.syfo.narmesteleder.NarmesteLederResponseKafkaProducer
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.narmesteleder.kafkamodel.NlRequestKafkaMessage
import no.nav.syfo.narmesteleder.kafkamodel.NlResponseKafkaMessage
import no.nav.syfo.papirsykmelding.DiagnoseService
import no.nav.syfo.papirsykmelding.GradService
import no.nav.syfo.papirsykmelding.PeriodeService
import no.nav.syfo.papirsykmelding.SlettInformasjonService
import no.nav.syfo.papirsykmelding.api.UpdateBehandletDatoService
import no.nav.syfo.papirsykmelding.api.UpdatePeriodeService
import no.nav.syfo.service.GjenapneSykmeldingService
import no.nav.syfo.sykmelding.DeleteSykmeldingService
import no.nav.syfo.sykmelding.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmelding.aivenmigrering.SykmeldingV2KafkaMessage
import no.nav.syfo.sykmelding.aivenmigrering.SykmeldingV2KafkaProducer
import no.nav.syfo.utils.JacksonKafkaSerializer
import no.nav.syfo.utils.JacksonNullableKafkaSerializer
import no.nav.syfo.utils.getFileAsString
import no.nav.syfo.vault.RenewVaultService
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
}

val legeerklaringObjectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
}

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfoservicedatasyfosmregister")

@DelicateCoroutinesApi
fun main() {
    val environment = Environment()
    val applicationState = ApplicationState()

    val jwtVaultSecrets = JwtVaultSecrets()
    val jwkProviderInternal = JwkProviderBuilder(URL(jwtVaultSecrets.internalJwtWellKnownUri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val vaultCredentialService = VaultCredentialService()
    val vaultServiceuser = getVaultServiceUser()

    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)

    val httpClients = HttpClients(environment, vaultServiceuser)

    val kafkaAivenProducer = KafkaProducer<String, SykmeldingV2KafkaMessage?>(
        KafkaUtils
            .getAivenKafkaConfig()
            .toProducerConfig("macgyver-producer", JacksonNullableKafkaSerializer::class, StringSerializer::class)
            .apply {
                this[ProducerConfig.ACKS_CONFIG] = "1"
                this[ProducerConfig.RETRIES_CONFIG] = 1000
                this[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = "false"
            }
    )
    val kafkaAivenNarmestelederRequestProducer = KafkaProducer<String, NlRequestKafkaMessage>(
        KafkaUtils.getAivenKafkaConfig()
            .toProducerConfig("macgyver-producer", JacksonKafkaSerializer::class, StringSerializer::class)
    )
    val aivenProducerProperties = KafkaUtils.getAivenKafkaConfig()
        .toProducerConfig(environment.applicationName, JacksonKafkaSerializer::class, StringSerializer::class)
    val kafkaproducerEndringsloggSykmelding = KafkaProducer<String, Sykmeldingsdokument>(aivenProducerProperties)
    val sykmeldingEndringsloggKafkaProducer = SykmeldingEndringsloggKafkaProducer(
        environment.aivenEndringsloggTopic,
        kafkaproducerEndringsloggSykmelding
    )

    val statusKafkaProducer =
        SykmeldingStatusKafkaProducer(KafkaProducer(aivenProducerProperties), environment.aivenSykmeldingStatusTopic)
    val updatePeriodeService = UpdatePeriodeService(
        databasePostgres = databasePostgres,
        sykmeldingEndringsloggKafkaProducer = sykmeldingEndringsloggKafkaProducer,
        sykmeldingProducer = SykmeldingV2KafkaProducer(kafkaAivenProducer),
        mottattSykmeldingTopic = environment.mottattSykmeldingV2Topic,
        sendtSykmeldingTopic = environment.sendSykmeldingV2Topic,
        bekreftetSykmeldingTopic = environment.bekreftSykmeldingV2KafkaTopic
    )
    val updateBehandletDatoService = UpdateBehandletDatoService(
        databasePostgres = databasePostgres,
        sykmeldingEndringsloggKafkaProducer = sykmeldingEndringsloggKafkaProducer
    )

    val sendtSykmeldingKafkaProducerFnr = SykmeldingV2KafkaProducer(kafkaAivenProducer)
    val narmesteLederResponseKafkaProducer = NarmesteLederResponseKafkaProducer(
        environment.nlResponseTopic,
        KafkaProducer<String, NlResponseKafkaMessage>(
            KafkaUtils
                .getAivenKafkaConfig()
                .toProducerConfig("macgyver-producer", JacksonNullableKafkaSerializer::class, StringSerializer::class)
        )
    )

    val updateFnrService = UpdateFnrService(
        pdlPersonService = httpClients.pdlService,
        syfoSmRegisterDb = databasePostgres,
        sendtSykmeldingKafkaProducer = sendtSykmeldingKafkaProducerFnr,
        narmesteLederResponseKafkaProducer = narmesteLederResponseKafkaProducer,
        narmestelederClient = httpClients.narmestelederClient,
        sendtSykmeldingTopic = environment.sendSykmeldingV2Topic
    )

    val tombstoneProducer = KafkaProducer<String, Any?>(
        KafkaUtils
            .getAivenKafkaConfig()
            .toProducerConfig("macgyver-tobstone-producer", JacksonNullableKafkaSerializer::class)
    )

    val deleteSykmeldingService = DeleteSykmeldingService(
        environment,
        databasePostgres,
        statusKafkaProducer,
        sykmeldingEndringsloggKafkaProducer,
        tombstoneProducer,
        listOf(environment.manuellTopic, environment.papirSmRegistreringTopic)
    )

    val gjenapneSykmeldingService = GjenapneSykmeldingService(
        statusKafkaProducer,
        databasePostgres
    )
    val narmestelederService = NarmestelederService(
        pdlService = httpClients.pdlService,
        kafkaAivenNarmestelederRequestProducer,
        environment.narmestelederRequestTopic
    )
    val applicationEngine = createApplicationEngine(
        env = environment,
        applicationState = applicationState,
        updatePeriodeService = updatePeriodeService,
        updateBehandletDatoService = updateBehandletDatoService,
        updateFnrService = updateFnrService,
        diagnoseService = DiagnoseService(databasePostgres, sykmeldingEndringsloggKafkaProducer),
        oppgaveClient = httpClients.oppgaveClient,
        jwkProviderInternal = jwkProviderInternal,
        issuerServiceuser = jwtVaultSecrets.jwtIssuer,
        clientId = jwtVaultSecrets.clientId,
        appIds = listOf(jwtVaultSecrets.clientId),
        deleteSykmeldingService = deleteSykmeldingService,
        gjenapneSykmeldingService = gjenapneSykmeldingService,
        narmestelederService = narmestelederService
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()

    applicationServer.start()
}

@DelicateCoroutinesApi
fun startBackgroundJob(applicationState: ApplicationState, block: suspend CoroutineScope.() -> Unit) {
    GlobalScope.launch(Dispatchers.IO) {
        try {
            block()
        } catch (ex: Exception) {
            log.error("Error in background task, restarting application", ex)
            applicationState.alive = false
            applicationState.ready = false
        }
    }
}

fun getDatabasePostgres(): DatabasePostgres {
    val environment = Environment()
    val vaultCredentialService = VaultCredentialService()
    return DatabasePostgres(environment, vaultCredentialService)
}

fun getDatabaseOracle(): DatabaseOracle {
    val vaultConfig = VaultConfig(
        jdbcUrl = getFileAsString("/secrets/syfoservice/config/jdbc_url")
    )
    val syfoserviceVaultSecrets = VaultCredentials(
        databasePassword = getFileAsString("/secrets/syfoservice/credentials/password"),
        databaseUsername = getFileAsString("/secrets/syfoservice/credentials/username")
    )
    return DatabaseOracle(vaultConfig, syfoserviceVaultSecrets)
}

fun updatePeriode(databaseOracle: DatabaseOracle, databasePostgres: DatabasePostgres) {
    val periodeService = PeriodeService(databaseOracle, databasePostgres)
    periodeService.start()
}

fun slettInfo(applicationState: ApplicationState, environment: Environment) {
    val vaultConfig = VaultConfig(
        jdbcUrl = getFileAsString("/secrets/syfoservice/config/jdbc_url")
    )
    val syfoserviceVaultSecrets = VaultCredentials(
        databasePassword = getFileAsString("/secrets/syfoservice/credentials/password"),
        databaseUsername = getFileAsString("/secrets/syfoservice/credentials/username")
    )
    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()

    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    val databaseOracle = DatabaseOracle(vaultConfig, syfoserviceVaultSecrets)

    val slettInformasjonService = SlettInformasjonService(databaseOracle, databasePostgres)
    slettInformasjonService.start()
}

fun updateGrad(applicationState: ApplicationState, environment: Environment) {
    val vaultConfig = VaultConfig(
        jdbcUrl = getFileAsString("/secrets/syfoservice/config/jdbc_url")
    )
    val syfoserviceVaultSecrets = VaultCredentials(
        databasePassword = getFileAsString("/secrets/syfoservice/credentials/password"),
        databaseUsername = getFileAsString("/secrets/syfoservice/credentials/username")
    )
    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()

    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    val databaseOracle = DatabaseOracle(vaultConfig, syfoserviceVaultSecrets)

    val gradService = GradService(databaseOracle, databasePostgres)
    // gradService.start()
    gradService.addPeriode()
}

fun getVaultServiceUser(): VaultServiceUser {
    val vaultServiceuser = VaultServiceUser(
        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
        serviceuserUsername = getFileAsString("/secrets/serviceuser/username")
    )
    return vaultServiceuser
}
