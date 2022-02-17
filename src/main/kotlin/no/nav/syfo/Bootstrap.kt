package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.auth.Credentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.clients.HttpClients
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.db.DatabasePostgresManuell
import no.nav.syfo.db.DatabasePostgresUtenVault
import no.nav.syfo.db.DatabaseSparenaproxyPostgres
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.identendring.UpdateFnrService
import no.nav.syfo.kafka.EiaSykmeldingKafkaProducer
import no.nav.syfo.kafka.ReceivedSykmeldingKafkaProducer
import no.nav.syfo.kafka.RerunKafkaMessage
import no.nav.syfo.kafka.RerunKafkaMessageKafkaProducer
import no.nav.syfo.kafka.SykmeldingEndringsloggKafkaProducer
import no.nav.syfo.kafka.SykmeldingIdKafkaProducer
import no.nav.syfo.kafka.SykmeldingKafkaProducer
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.manuell.ValidationResultService
import no.nav.syfo.model.Eia
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Sykmeldingsdokument
import no.nav.syfo.narmesteleder.NarmesteLederFraSyfoServiceService
import no.nav.syfo.narmesteleder.NarmesteLederResponseKafkaProducer
import no.nav.syfo.narmesteleder.SyfoServiceNarmesteLeder
import no.nav.syfo.narmesteleder.SyfoServiceNarmesteLederKafkaProducer
import no.nav.syfo.narmesteleder.kafkamodel.NlResponseKafkaMessage
import no.nav.syfo.papirsykmelding.DiagnoseService
import no.nav.syfo.papirsykmelding.GradService
import no.nav.syfo.papirsykmelding.PeriodeService
import no.nav.syfo.papirsykmelding.SlettInformasjonService
import no.nav.syfo.papirsykmelding.api.UpdateBehandletDatoService
import no.nav.syfo.papirsykmelding.api.UpdatePeriodeService
import no.nav.syfo.papirsykmelding.tilsyfoservice.SendTilSyfoserviceService
import no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.SykmeldingSyfoserviceKafkaProducer
import no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.model.SykmeldingSyfoserviceKafkaMessage
import no.nav.syfo.pdf.rerun.kafka.RerunKafkaProducer
import no.nav.syfo.pdf.rerun.service.RerunKafkaService
import no.nav.syfo.sak.avro.RegisterTask
import no.nav.syfo.service.BehandlingsutfallFraOppgaveTopicService
import no.nav.syfo.service.CheckSendtSykmeldinger
import no.nav.syfo.service.CheckTombstoneService
import no.nav.syfo.service.GjenapneSykmeldingService
import no.nav.syfo.service.HentSykmeldingerFraEiaService
import no.nav.syfo.service.HentSykmeldingerFraSyfoServiceService
import no.nav.syfo.service.HentSykmeldingsidFraBackupService
import no.nav.syfo.service.InsertOKBehandlingsutfall
import no.nav.syfo.service.MapSykmeldingStringToSykemldignJsonMap
import no.nav.syfo.service.OpprettPdfService
import no.nav.syfo.service.RyddDuplikateSykmeldingerService
import no.nav.syfo.service.SkrivBehandlingsutfallTilSyfosmRegisterService
import no.nav.syfo.service.SkrivManglendeSykmelidngTilTopic
import no.nav.syfo.service.SkrivTilSyfosmRegisterServiceEia
import no.nav.syfo.service.SkrivTilSyfosmRegisterSyfoService
import no.nav.syfo.service.UpdateArbeidsgiverWhenSendtService
import no.nav.syfo.service.UpdateStatusService
import no.nav.syfo.service.WriteReceivedSykmeldingService
import no.nav.syfo.sparenaproxy.Arena4UkerService
import no.nav.syfo.sykmelding.BekreftSykmeldingService
import no.nav.syfo.sykmelding.DeleteSykmeldingService
import no.nav.syfo.sykmelding.MottattSykmeldingService
import no.nav.syfo.sykmelding.SendtSykmeldingService
import no.nav.syfo.sykmelding.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmelding.aivenmigrering.SykmeldingV2KafkaMessage
import no.nav.syfo.sykmelding.aivenmigrering.SykmeldingV2KafkaProducer
import no.nav.syfo.utils.JacksonKafkaSerializer
import no.nav.syfo.utils.JacksonNullableKafkaSerializer
import no.nav.syfo.utils.getFileAsString
import no.nav.syfo.vault.RenewVaultService
import no.nav.syfo.vedlegg.google.BucketService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Properties
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

    val vaultConfig = VaultConfig(
        jdbcUrl = getFileAsString("/secrets/syfoservice/config/jdbc_url")
    )

    val syfoserviceVaultSecrets = VaultCredentials(
        databasePassword = getFileAsString("/secrets/syfoservice/credentials/password"),
        databaseUsername = getFileAsString("/secrets/syfoservice/credentials/username")
    )
    val vaultCredentialService = VaultCredentialService()
    val vaultServiceuser = getVaultServiceUser()

    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    val databaseOracle = DatabaseOracle(vaultConfig, syfoserviceVaultSecrets)

    val httpClients = HttpClients(environment, vaultServiceuser)

    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
    val producerProperties =
        kafkaBaseConfig.toProducerConfig(
            environment.applicationName,
            valueSerializer = JacksonKafkaSerializer::class
        )
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
    val aivenProducerProperties = KafkaUtils.getAivenKafkaConfig()
        .toProducerConfig(environment.applicationName, JacksonKafkaSerializer::class, StringSerializer::class).apply {
            this[ProducerConfig.ACKS_CONFIG] = "1"
            this[ProducerConfig.RETRIES_CONFIG] = 1000
            this[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = "false"
        }
    val sykmeldingEndringsloggKafkaProducer = SykmeldingEndringsloggKafkaProducer(
        environment.endringsloggTopic,
        KafkaProducer<String, Sykmeldingsdokument>(producerProperties)
    )
    val statusKafkaProducer =
        SykmeldingStatusKafkaProducer(KafkaProducer(aivenProducerProperties), environment.aivenSykmeldingStatusTopic)
    val updatePeriodeService = UpdatePeriodeService(
        databaseoracle = databaseOracle,
        databasePostgres = databasePostgres,
        sykmeldingEndringsloggKafkaProducer = sykmeldingEndringsloggKafkaProducer,
        sykmeldingProducer = SykmeldingV2KafkaProducer(kafkaAivenProducer),
        mottattSykmeldingTopic = environment.mottattSykmeldingV2Topic,
        sendtSykmeldingTopic = environment.sendSykmeldingV2Topic,
        bekreftetSykmeldingTopic = environment.bekreftSykmeldingV2KafkaTopic
    )
    val updateBehandletDatoService = UpdateBehandletDatoService(
        databaseoracle = databaseOracle,
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

    val deleteSykmeldingService = DeleteSykmeldingService(
        environment,
        databasePostgres,
        databaseOracle,
        statusKafkaProducer,
        sykmeldingEndringsloggKafkaProducer
    )

    val producerConfigRerun = kafkaBaseConfig.toProducerConfig(
        "${environment.applicationName}-producer", valueSerializer = StringSerializer::class
    )
    val rerunKafkaService =
        RerunKafkaService(databasePostgres, RerunKafkaProducer(KafkaProducer(producerConfigRerun), environment))

    val gjenapneSykmeldingService = GjenapneSykmeldingService(
        databaseOracle,
        statusKafkaProducer,
        databasePostgres
    )

    val sykmeldingStorageCredentials: Credentials =
        GoogleCredentials.fromStream(FileInputStream("/var/run/secrets/nais.io/vault/sykmelding-google-creds.json"))
    val sykmeldingStorage: Storage =
        StorageOptions.newBuilder().setCredentials(sykmeldingStorageCredentials).build().service
    val sykmeldingBucketService = BucketService(environment.sykmeldingBucketName, sykmeldingStorage)

    val paleStorageCredentials: Credentials =
        GoogleCredentials.fromStream(FileInputStream("/var/run/secrets/nais.io/vault/pale2-google-creds.json"))
    val paleStorage: Storage = StorageOptions.newBuilder().setCredentials(paleStorageCredentials).build().service
    val paleBucketService = BucketService(environment.paleBucketName, paleStorage)

    val applicationEngine = createApplicationEngine(
        env = environment,
        applicationState = applicationState,
        updatePeriodeService = updatePeriodeService,
        updateBehandletDatoService = updateBehandletDatoService,
        updateFnrService = updateFnrService,
        sendTilSyfoserviceService = createSendTilSyfoservice(environment, databasePostgres, aivenProducerProperties),
        diagnoseService = DiagnoseService(databaseOracle, databasePostgres, sykmeldingEndringsloggKafkaProducer),
        oppgaveClient = httpClients.oppgaveClient,
        jwkProviderInternal = jwkProviderInternal,
        issuerServiceuser = jwtVaultSecrets.jwtIssuer,
        clientId = jwtVaultSecrets.clientId,
        appIds = listOf(jwtVaultSecrets.clientId),
        deleteSykmeldingService = deleteSykmeldingService,
        rerunKafkaService = rerunKafkaService,
        gjenapneSykmeldingService = gjenapneSykmeldingService
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    applicationServer.start()
    applicationState.ready = true

    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
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

fun createSendTilSyfoservice(
    environment: Environment,
    databasePostgres: DatabasePostgres,
    producerProperties: Properties
): SendTilSyfoserviceService {
    val syfoserviceKafkaProducer = SykmeldingSyfoserviceKafkaProducer(
        KafkaProducer<String, SykmeldingSyfoserviceKafkaMessage>(producerProperties),
        environment.syfoserviceKafkaTopic
    )
    return SendTilSyfoserviceService(syfoserviceKafkaProducer, databasePostgres)
}


fun getVaultServiceUser(): VaultServiceUser {
    val vaultServiceuser = VaultServiceUser(
        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
        serviceuserUsername = getFileAsString("/secrets/serviceuser/username")
    )
    return vaultServiceuser
}
