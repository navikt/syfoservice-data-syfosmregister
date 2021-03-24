package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.ktor.util.KtorExperimentalAPI
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.clients.HttpClients
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.db.DatabasePostgresUtenVault
import no.nav.syfo.db.DatabaseSparenaproxyPostgres
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.kafka.EiaSykmeldingKafkaProducer
import no.nav.syfo.kafka.ReceivedSykmeldingKafkaProducer
import no.nav.syfo.kafka.RerunKafkaMessage
import no.nav.syfo.kafka.RerunKafkaMessageKafkaProducer
import no.nav.syfo.kafka.SykmeldingEndringsloggKafkaProducer
import no.nav.syfo.kafka.SykmeldingIdKafkaProducer
import no.nav.syfo.kafka.SykmeldingKafkaProducer
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.Eia
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Sykmeldingsdokument
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.papirsykmelding.DiagnoseService
import no.nav.syfo.papirsykmelding.GradService
import no.nav.syfo.papirsykmelding.PeriodeService
import no.nav.syfo.papirsykmelding.SlettInformasjonService
import no.nav.syfo.papirsykmelding.api.UpdateBehandletDatoService
import no.nav.syfo.papirsykmelding.api.UpdatePeriodeService
import no.nav.syfo.papirsykmelding.tilsyfoservice.SendTilSyfoserviceService
import no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.SykmeldingSyfoserviceKafkaProducer
import no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.model.SykmeldingSyfoserviceKafkaMessage
import no.nav.syfo.sak.avro.RegisterTask
import no.nav.syfo.service.BehandlingsutfallFraOppgaveTopicService
import no.nav.syfo.service.CheckSendtSykmeldinger
import no.nav.syfo.service.CheckTombstoneService
import no.nav.syfo.service.HentSykmeldingerFraEiaService
import no.nav.syfo.service.HentSykmeldingerFraSyfoServiceService
import no.nav.syfo.service.HentSykmeldingsidFraBackupService
import no.nav.syfo.service.InsertOKBehandlingsutfall
import no.nav.syfo.service.MapSykmeldingStringToSykemldignJsonMap
import no.nav.syfo.service.OppdaterStatusService
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
import no.nav.syfo.sykmelding.EnkelSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.MottattSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.MottattSykmeldingService
import no.nav.syfo.sykmelding.SendtSykmeldingService
import no.nav.syfo.sykmelding.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmelding.UpdateFnrService
import no.nav.syfo.sykmelding.kafka.model.MottattSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import no.nav.syfo.utils.JacksonKafkaSerializer
import no.nav.syfo.utils.getFileAsString
import no.nav.syfo.vault.RenewVaultService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
}

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfoservicedatasyfosmregister")

@KtorExperimentalAPI
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
    val sykmeldingEndringsloggKafkaProducer = SykmeldingEndringsloggKafkaProducer(environment.endringsloggTopic, KafkaProducer<String, Sykmeldingsdokument>(producerProperties))
    val mottattSykmeldingKafkaProducer = MottattSykmeldingKafkaProducer(KafkaProducer<String, MottattSykmeldingKafkaMessage>(producerProperties), environment.mottattSykmeldingTopic)
    val sendtSykmeldingKafkaProducer = EnkelSykmeldingKafkaProducer(KafkaProducer<String, SykmeldingKafkaMessage>(producerProperties), environment.sendSykmeldingTopic)
    val bekreftetSykmeldingKafkaProducer = EnkelSykmeldingKafkaProducer(KafkaProducer<String, SykmeldingKafkaMessage>(producerProperties), environment.bekreftSykmeldingKafkaTopic)
    val statusKafkaProducer = SykmeldingStatusKafkaProducer(KafkaProducer(producerProperties), environment.sykmeldingStatusTopic)
    val updatePeriodeService = UpdatePeriodeService(
        databaseoracle = databaseOracle,
        databasePostgres = databasePostgres,
        sykmeldingEndringsloggKafkaProducer = sykmeldingEndringsloggKafkaProducer,
        mottattSykmeldingProudcer = mottattSykmeldingKafkaProducer,
        sendtSykmeldingProducer = sendtSykmeldingKafkaProducer,
        bekreftetSykmeldingKafkaProducer = bekreftetSykmeldingKafkaProducer
    )
    val updateBehandletDatoService = UpdateBehandletDatoService(
        databaseoracle = databaseOracle,
        databasePostgres = databasePostgres,
        sykmeldingEndringsloggKafkaProducer = sykmeldingEndringsloggKafkaProducer
    )

    val updateFnrService = UpdateFnrService(
        pdlPersonService = httpClients.pdlService,
        syfoSmRegisterDb = databasePostgres
    )
    val deleteSykmeldingService = DeleteSykmeldingService(environment, databasePostgres, databaseOracle, statusKafkaProducer, sykmeldingEndringsloggKafkaProducer)
    val applicationEngine = createApplicationEngine(
        env = environment,
        applicationState = applicationState,
        updatePeriodeService = updatePeriodeService,
        updateBehandletDatoService = updateBehandletDatoService,
        updateFnrService = updateFnrService,
        sendTilSyfoserviceService = createSendTilSyfoservice(environment, databasePostgres, producerProperties),
        diagnoseService = DiagnoseService(databaseOracle, databasePostgres, sykmeldingEndringsloggKafkaProducer),
        jwkProviderInternal = jwkProviderInternal,
        issuerServiceuser = jwtVaultSecrets.jwtIssuer,
        clientId = jwtVaultSecrets.clientId,
        appIds = listOf(jwtVaultSecrets.clientId),
        deleteSykmeldingService = deleteSykmeldingService
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    applicationServer.start()
    applicationState.ready = true

    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
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

fun createSendTilSyfoservice(environment: Environment, databasePostgres: DatabasePostgres, producerProperties: Properties): SendTilSyfoserviceService {
    val syfoserviceKafkaProducer = SykmeldingSyfoserviceKafkaProducer(KafkaProducer<String, SykmeldingSyfoserviceKafkaMessage>(producerProperties), environment.syfoserviceKafkaTopic)
    return SendTilSyfoserviceService(syfoserviceKafkaProducer, databasePostgres)
}

fun oppdaterStatus(applicationState: ApplicationState, environment: Environment) {
    val vaultServiceuser = getVaultServiceUser()
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
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
    val producerProperties =
        kafkaBaseConfig.toProducerConfig(
            environment.applicationName,
            valueSerializer = JacksonKafkaSerializer::class
        )
    val kafkaProducer = KafkaProducer<String, SykmeldingStatusKafkaMessageDTO>(producerProperties)
    val statusKafkaProducer = SykmeldingStatusKafkaProducer(kafkaProducer, environment.sykmeldingStatusTopic)
    val oppdaterStatusService = OppdaterStatusService(databaseOracle, statusKafkaProducer, databasePostgres)

    oppdaterStatusService.start()
}

fun opprett4ukersmeldinger(applicationState: ApplicationState, environment: Environment) {
    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()

    val databasePostgres = DatabaseSparenaproxyPostgres(environment, vaultCredentialService)
    val service = Arena4UkerService(
        applicationState,
        databasePostgres,
        LocalDate.parse(environment.lastIndexSparenaproxy).atStartOfDay().atZone(ZoneId.systemDefault()).withZoneSameInstant(
            ZoneOffset.UTC
        ).toOffsetDateTime()
    )
    service.run()
}

fun skrivMangledeSykmeldingTilTopic(applicationState: ApplicationState, environment: Environment) {
    val syfoserviceVaultSecrets = VaultCredentials(
        databasePassword = getFileAsString("/secrets/syfoservice/credentials/password"),
        databaseUsername = getFileAsString("/secrets/syfoservice/credentials/username")
    )

    val vaultConfig = VaultConfig(
        jdbcUrl = getFileAsString("/secrets/syfoservice/config/jdbc_url")
    )
    val vaultServiceuser = getVaultServiceUser()
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
    val databaseOracle = DatabaseOracle(vaultConfig, syfoserviceVaultSecrets)
    val producerProperties =
        kafkaBaseConfig.toProducerConfig(
            environment.applicationName,
            valueSerializer = JacksonKafkaSerializer::class
        )
    val kafkaProducer = KafkaProducer<String, ReceivedSykmelding>(producerProperties)
    val service = SkrivManglendeSykmelidngTilTopic(kafkaProducer = kafkaProducer, databaseOracle = databaseOracle)
    service.run()
}

suspend fun sendtMottattSykmeldinger(applicationState: ApplicationState, environment: Environment) {
    val vaultServiceuser = getVaultServiceUser()
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)

    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()

    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    val producerProperties =
        kafkaBaseConfig.toProducerConfig(
            environment.applicationName,
            valueSerializer = JacksonKafkaSerializer::class
        )
    val kafkaProducer = KafkaProducer<String, MottattSykmeldingKafkaMessage>(producerProperties)
    val mottatSykmeldingKafkaProducer =
        MottattSykmeldingKafkaProducer(kafkaProducer, environment.mottattSykmeldingTopic)
    delay(1000)
    val service = MottattSykmeldingService(
        applicationState,
        databasePostgres,
        mottatSykmeldingKafkaProducer,
        LocalDate.parse(environment.lastIndexSyfosmregister)
    )
    service.run()
}

fun chechSendtSykmelding(applicationState: ApplicationState, environment: Environment) {
    val vaultServiceuser = getVaultServiceUser()
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-sendt-sykmelding-5",
        valueDeserializer = StringDeserializer::class
    )
    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100")
    val sendtSykmeldingerConsumer = KafkaConsumer<String, String?>(consumerProperties)
    sendtSykmeldingerConsumer.subscribe(listOf(environment.sendSykmeldingTopic))

    GlobalScope.launch {
        CheckSendtSykmeldinger(sendtSykmeldingerConsumer, applicationState).run()
    }
}

fun readAndCheckTombstone(applicationState: ApplicationState, environment: Environment) {
    val vaultServiceuser = getVaultServiceUser()
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-bekreftet-sykmelding-8",
        valueDeserializer = StringDeserializer::class
    )
    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100")
    val tombstoneConsumer = KafkaConsumer<String, String?>(consumerProperties)
    tombstoneConsumer.subscribe(listOf(environment.bekreftSykmeldingKafkaTopic))

    GlobalScope.launch {
        CheckTombstoneService(tombstoneConsumer, applicationState).run()
    }
}

fun sendBekreftetSykmeldinger(applicationState: ApplicationState, environment: Environment) {
    val vaultServiceuser = getVaultServiceUser()
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)

    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()

    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    val producerProperties =
        kafkaBaseConfig.toProducerConfig(
            environment.applicationName,
            valueSerializer = JacksonKafkaSerializer::class
        )
    val kafkaProducer = KafkaProducer<String, SykmeldingKafkaMessage>(producerProperties)
    val sendSykmeldingKafkaProducer =
        EnkelSykmeldingKafkaProducer(kafkaProducer, environment.bekreftSykmeldingKafkaTopic)
    val service = BekreftSykmeldingService(
        applicationState,
        databasePostgres,
        sendSykmeldingKafkaProducer,
        LocalDate.parse(environment.lastIndexSyfosmregister)
    )
    service.run()
}

fun sendSendtSykmeldinger(applicationState: ApplicationState, environment: Environment) {
    val vaultServiceuser = getVaultServiceUser()
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)

    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()

    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    val producerProperties =
        kafkaBaseConfig.toProducerConfig(
            environment.applicationName,
            valueSerializer = JacksonKafkaSerializer::class
        )
    val kafkaProducer = KafkaProducer<String, SykmeldingKafkaMessage>(producerProperties)
    val sendSykmeldingKafkaProducer =
        EnkelSykmeldingKafkaProducer(kafkaProducer, environment.sendSykmeldingTopic)
    val service = SendtSykmeldingService(
        applicationState,
        databasePostgres,
        sendSykmeldingKafkaProducer,
        LocalDate.parse(environment.lastIndexSyfosmregister)
    )
    service.republishSendtSykmelding()
}

fun opprettPdf(applicationState: ApplicationState, environment: Environment) {
    val vaultServiceuser = getVaultServiceUser()
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)

    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-backup-id-consumer-3",
        valueDeserializer = StringDeserializer::class
    )
    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10")
    val kafkaConsumerIdFraBackup = KafkaConsumer<String, String>(consumerProperties)
    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    val producerProperties =
        kafkaBaseConfig.toProducerConfig(
            environment.applicationName,
            valueSerializer = JacksonKafkaSerializer::class
        )
    val kafkaProducer = KafkaProducer<String, RerunKafkaMessage>(producerProperties)
    val rerunKafkaMessageKafkaProducer =
        RerunKafkaMessageKafkaProducer(environment.rerunTopic, kafkaProducer)
    val service = OpprettPdfService(
        applicationState,
        kafkaConsumerIdFraBackup,
        rerunKafkaMessageKafkaProducer,
        environment.idUtenBehandlingsutfallFraBackupTopic,
        databasePostgres
    )
    service.run()
}

fun hentSykmeldingerFraBackupUtenBehandlingsutfallOgPubliserTilTopic(applicationState: ApplicationState, environment: Environment) {
    val vaultServiceuser = getVaultServiceUser()

    val databaseVaultSecrets = VaultCredentials(
        databasePassword = getFileAsString("/secrets/syfoservice/credentials/password"),
        databaseUsername = getFileAsString("/secrets/syfoservice/credentials/username")
        // backupDbUsername = getEnvVar("BACKUP_USERNAME"),
        // backupDbPassword = getEnvVar("BACKUP_PASSWORD")
    )

    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
    val producerProperties =
        kafkaBaseConfig.toProducerConfig(environment.applicationName, valueSerializer = JacksonKafkaSerializer::class)

    val kafkaProducer = KafkaProducer<String, String>(producerProperties)
    val sykmeldingIdKafkaProducer = SykmeldingIdKafkaProducer(environment.idUtenBehandlingsutfallFraBackupTopic, kafkaProducer)

    val databasePostgresUtenVault = DatabasePostgresUtenVault(environment, databaseVaultSecrets)

    HentSykmeldingsidFraBackupService(
        sykmeldingIdKafkaProducer, databasePostgresUtenVault, environment.lastIndexBackup, applicationState
    ).run()
}

fun addSykmeldingerToReceivedTopic(applicationState: ApplicationState, environment: Environment) {

    val vaultServiceuser = getVaultServiceUser()
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)

    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-sykmelding-clean-consumer-15",
        valueDeserializer = StringDeserializer::class
    )
    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10")
    val kafkaConsumerCleanSykmelding = KafkaConsumer<String, String>(consumerProperties)
    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    val producerProperties =
        kafkaBaseConfig.toProducerConfig(
            environment.applicationName,
            valueSerializer = JacksonKafkaSerializer::class
        )
    val kafkaProducer = KafkaProducer<String, ReceivedSykmelding>(producerProperties)
    val receivedSykmeldingKafkaProducer =
        ReceivedSykmeldingKafkaProducer(environment.sm2013ReceivedSykmelding, kafkaProducer)
    val service = WriteReceivedSykmeldingService(
        applicationState,
        kafkaConsumerCleanSykmelding,
        receivedSykmeldingKafkaProducer,
        environment.sykmeldingCleanTopicFull,
        databasePostgres
    )
    service.run()
}
//
// fun hentArbeidsgiverInformasjonPaaSykmelding(
//    applicationState: ApplicationState,
//    environment: Environment
// ) {
//
//    val vaultServiceuser = VaultServiceUser(
//        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
//        serviceuserUsername = getFileAsString("/secrets/serviceuser/username")
//    )
//
//    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
//    val producerProperties =
//        kafkaBaseConfig.toProducerConfig(
//            environment.applicationName,
//            valueSerializer = JacksonKafkaSerializer::class
//        )
//
//    val kafkaproducerArbeidsgiverSykmeldingString = KafkaProducer<String, String>(producerProperties)
//
//    val arbeidsgiverSykmeldingKafkaProducer =
//        ArbeidsgiverSykmeldingKafkaProducer(
//            environment.sm2013SyfoSericeSykmeldingArbeidsgiverTopic,
//            kafkaproducerArbeidsgiverSykmeldingString
//        )
//
//    val syfoserviceVaultSecrets = VaultCredentials(
//        databasePassword = getFileAsString("/secrets/syfoservice/credentials/password"),
//        databaseUsername = getFileAsString("/secrets/syfoservice/credentials/username")
//    )
//
//    val vaultConfig = VaultConfig(
//        jdbcUrl = getFileAsString("/secrets/syfoservice/config/jdbc_url")
//    )
//
//    val databaseOracle = DatabaseOracle(vaultConfig, syfoserviceVaultSecrets)
//
//    HentArbeidsGiverOgSporsmalFraSyfoServiceService(
//        arbeidsgiverSykmeldingKafkaProducer,
//        databaseOracle,
//        1_000
//    ).run()
// }

// suspend fun hentBehandlingsutfallOgSkrivTilTopic(applicationState: ApplicationState, environment: Environment) {
//    val vaultServiceuser = getVaultServiceUser()
//
//    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
//    val producerProperties =
//        kafkaBaseConfig.toProducerConfig(environment.applicationName, valueSerializer = JacksonKafkaSerializer::class)
//
//    val kafkaProducerRS = KafkaProducer<String, ReceivedSykmelding>(producerProperties)
//    val receivedSykmeldingKafkaProducer =
//        ReceivedSykmeldingKafkaProducer(environment.receivedSykmeldingBackupTopic, kafkaProducerRS)
//
//    val kafkaProducerBU = KafkaProducer<String, Behandlingsutfall>(producerProperties)
//    val behandlingsutfallKafkaProducer = BehandlingsutfallKafkaProducer(environment.behandlingsutfallBackupTopic, ka)
//
//    val vaultCredentialService = VaultCredentialService()
//    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
//    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
//
//    HentSykmeldingerFraSyfosmregisterService(
//        receivedSykmeldingKafkaProducer = receivedSykmeldingKafkaProducer,
//        behandlingsutfallKafkaProducer = behandlingsutfallKafkaProducer,
//        databasePostgres = databasePostgres, lastIndexSyfosmregister = environment.lastIndexSyfosmregister, applicationState = applicationState
//    ).skrivBehandlingsutfallTilTopic()
// }

fun lagreOkBehandlingsutfall(applicationState: ApplicationState, environment: Environment) {
    val vaultServiceuser = getVaultServiceUser()
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)

    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-sykmelding-clean-consumer-7",
        valueDeserializer = StringDeserializer::class
    )

    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10")
    val kafkaConsumerCleanSykmelding = KafkaConsumer<String, String>(consumerProperties)
    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    val insertOKBehandlingsutfall = InsertOKBehandlingsutfall(
        kafkaConsumerCleanSykmelding,
        databasePostgres,
        environment.sykmeldingCleanTopicFull,
        applicationState
    )
    insertOKBehandlingsutfall.run()
}

fun readFromRegistrerOppgaveTopic(
    applicationState: ApplicationState,
    environment: Environment,
    ruleMap: Map<String, RuleInfo>
) {

    val vaultServiceuser = getVaultServiceUser()
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)

    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-sykmelding-clean-consumer-5",
        valueDeserializer = KafkaAvroDeserializer::class
    )

    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10")
    val kafkaconsumerOppgave = KafkaConsumer<String, RegisterTask>(consumerProperties)
    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    val behandlingsutfallFraOppgaveTopicService = BehandlingsutfallFraOppgaveTopicService(
        kafkaconsumerOppgave,
        databasePostgres,
        environment.oppgaveTopic,
        applicationState,
        ruleMap
    )
    behandlingsutfallFraOppgaveTopicService.lagreManuellbehandlingFraOppgaveTopic()
}

fun insertMissingArbeidsgivere(applicationState: ApplicationState, environment: Environment) {
    val vaultServiceuser = getVaultServiceUser()
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)

    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-sykmelding-clean-consumer-6",
        valueDeserializer = StringDeserializer::class
    )

    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100")
    val kafkaConsumerCleanSykmelding = KafkaConsumer<String, String>(consumerProperties)
    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    val updateArbeidsgiverWhenSendtService = UpdateArbeidsgiverWhenSendtService(
        kafkaConsumerCleanSykmelding,
        databasePostgres,
        environment.sykmeldingCleanTopicFull,
        applicationState
    )
    updateArbeidsgiverWhenSendtService.run()
}

fun ryddDuplikateSykmeldinger(applicationState: ApplicationState, environment: Environment) {
    val vaultServiceuser = getVaultServiceUser()
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)

    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-sykmelding-clean-consumer-4",
        valueDeserializer = StringDeserializer::class
    )

    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10")
    val kafkaConsumerCleanSykmelding = KafkaConsumer<String, String>(consumerProperties)
    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    val ryddDuplikateSykmeldingerService = RyddDuplikateSykmeldingerService(
        kafkaConsumerCleanSykmelding,
        databasePostgres,
        environment.sykmeldingCleanTopicFull,
        applicationState
    )
    ryddDuplikateSykmeldingerService.ryddDuplikateSykmeldinger()
}

fun oppdaterIds(applicationState: ApplicationState, environment: Environment) {
    val vaultConfig = VaultConfig(
        jdbcUrl = getFileAsString("/secrets/syfoservice/config/jdbc_url")
    )
    val vaultServiceuser = getVaultServiceUser()
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)

    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-sykmelding-clean-consumer-14",
        valueDeserializer = StringDeserializer::class
    )
    val syfoserviceVaultSecrets = VaultCredentials(
        databasePassword = getFileAsString("/secrets/syfoservice/credentials/password"),
        databaseUsername = getFileAsString("/secrets/syfoservice/credentials/username")
        // backupDbUsername = getEnvVar("BACKUP_USERNAME"),
        // backupDbPassword = getEnvVar("BACKUP_PASSWORD")
    )
    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1")
    val kafkaConsumerCleanSykmelding = KafkaConsumer<String, String>(consumerProperties)
    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    val updateService = UpdateStatusService(databasePostgres)
    val databaseOracle = DatabaseOracle(vaultConfig, syfoserviceVaultSecrets)
    val skrivTilSyfosmRegisterSyfoService = SkrivTilSyfosmRegisterSyfoService(
        kafkaConsumerCleanSykmelding,
        databasePostgres,
        environment.sykmeldingCleanTopicFull,
        applicationState,
        updateService,
        databaseOracle
    )
    skrivTilSyfosmRegisterSyfoService.updateId()
}

fun leggInnBehandlingsstatusForSykmeldinger(applicationState: ApplicationState, environment: Environment) {
    val vaultServiceuser = getVaultServiceUser()
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)

    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-sykmelding-clean-consumer-19",
        valueDeserializer = StringDeserializer::class
    )
    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500")
    val kafkaConsumerCleanSykmelding = KafkaConsumer<String, String>(consumerProperties)
    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    val skrivBehandlingsutfallTilSyfosmRegisterService = SkrivBehandlingsutfallTilSyfosmRegisterService(
        kafkaConsumerCleanSykmelding,
        databasePostgres,
        environment.sykmeldingCleanTopic,
        applicationState
    )
    skrivBehandlingsutfallTilSyfosmRegisterService.leggInnBehandlingsutfall()
}

fun hentSykemldingerFraEia(environment: Environment) {
    val vaultSecrets = VaultCredentials(
        databasePassword = getFileAsString("/secrets/eia/credentials/password"),
        databaseUsername = getFileAsString("/secrets/eia/credentials/username")
        // backupDbUsername = getEnvVar("BACKUP_USERNAME"),
        // backupDbPassword = getEnvVar("BACKUP_PASSWORD")
    )
    val vaultServiceuser = getVaultServiceUser()

    val vaultConfig = VaultConfig(
        jdbcUrl = getFileAsString("/secrets/eia/config/jdbc_url")
    )

    val databaseOracle = DatabaseOracle(vaultConfig, vaultSecrets)
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
    val producerProperties =
        kafkaBaseConfig.toProducerConfig(
            environment.applicationName,
            valueSerializer = JacksonKafkaSerializer::class
        )

    val kafkaproducerEiaSykmelding = KafkaProducer<String, Eia>(producerProperties)
    HentSykmeldingerFraEiaService(
        EiaSykmeldingKafkaProducer(
            environment.sm2013EiaSykmedlingTopic,
            kafkaproducerEiaSykmelding
        ),
        databaseOracle, 10_000, environment.lastIndexEia
    ).run()
}

fun hentSykemeldingerFraSyfoserviceOgPubliserTilTopic(environment: Environment, applicationState: ApplicationState) {
    val vaultConfig = VaultConfig(
        jdbcUrl = getFileAsString("/secrets/syfoservice/config/jdbc_url")
    )
    val vaultServiceuser = getVaultServiceUser()

    val syfoserviceVaultSecrets = VaultCredentials(
        databasePassword = getFileAsString("/secrets/syfoservice/credentials/password"),
        databaseUsername = getFileAsString("/secrets/syfoservice/credentials/username")
        // backupDbUsername = getEnvVar("BACKUP_USERNAME")
        // backupDbPassword = getEnvVar("BACKUP_PASSWORD")
    )
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
    val producerProperties =
        kafkaBaseConfig.toProducerConfig(environment.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    val kafkaProducerClean = KafkaProducer<String, Map<String, Any?>>(producerProperties)

    val databaseOracle = DatabaseOracle(vaultConfig, syfoserviceVaultSecrets)
    HentSykmeldingerFraSyfoServiceService(
        SykmeldingKafkaProducer(environment.sykmeldingCleanTopicFull, kafkaProducerClean),
        databaseOracle, 10_000, environment.lastIndexSyfoservice
    ).run()
}

fun oppdaterFraEia(applicationState: ApplicationState, environment: Environment) {
    val vaultServiceuser = getVaultServiceUser()
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-eia-consumer",
        valueDeserializer = StringDeserializer::class
    )
    val vaultCredentialService = VaultCredentialService()
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    val databasePostgres = DatabasePostgres(environment, vaultCredentialService)
    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500")
    val kafkaconsumerrEiaSykmelding = KafkaConsumer<String, String>(consumerProperties)
    SkrivTilSyfosmRegisterServiceEia(
        kafkaconsumerrEiaSykmelding,
        databasePostgres,
        environment.sm2013EiaSykmedlingTopic,
        applicationState
    ).run()
}

fun runMapStringToJsonMap(
    applicationState: ApplicationState,
    environment: Environment
) {

    val vaultServiceuser = getVaultServiceUser()
    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)

    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-sykmelding-string-consumer-1",
        valueDeserializer = StringDeserializer::class
    )
    consumerProperties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10")
    val kafkaConsumerStringSykmelding = KafkaConsumer<String, String>(consumerProperties)

    val producerProperties =
        kafkaBaseConfig.toProducerConfig(environment.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    val kafkaProducerClean = KafkaProducer<String, Map<String, String?>>(producerProperties)

    MapSykmeldingStringToSykemldignJsonMap(
        kafkaConsumerStringSykmelding,
        kafkaProducerClean,
        environment.sykmeldingCleanTopic,
        environment.sm2013SyfoserviceSykmeldingTopic,
        applicationState
    ).run()
}

fun getVaultServiceUser(): VaultServiceUser {
    val vaultServiceuser = VaultServiceUser(
        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
        serviceuserUsername = getFileAsString("/secrets/serviceuser/username")
    )
    return vaultServiceuser
}
