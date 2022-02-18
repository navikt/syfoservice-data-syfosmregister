package no.nav.syfo.service

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.log
import no.nav.syfo.model.Behandlingsutfall
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.db.postgres.hentSykmeldingIdManglerBehandlingsutfall
import no.nav.syfo.persistering.db.postgres.lagreBehandlingsutfallAndCommit
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sak.avro.RegisterTask
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId

@DelicateCoroutinesApi
class BehandlingsutfallFraOppgaveTopicService(
    private val kafkaConsumer: KafkaConsumer<String, RegisterTask>,
    private val databasePostgres: DatabaseInterfacePostgres,
    private val oppgaveTopic: String,
    private val applicationState: ApplicationState,
    private val ruleMap: Map<String, RuleInfo>
) {

    fun lagreManuellbehandlingFraOppgaveTopic() {
        kafkaConsumer.subscribe(
            listOf(
                oppgaveTopic
            )
        )
        var counterAll = 0
        var counterOppdatertBehandlingsutfall = 0
        var lastCounter = 0
        var lastTimestamp = LocalDateTime.of(2019, 5, 1, 0, 0)
        GlobalScope.launch {
            while (applicationState.ready) {
                if (lastCounter != counterAll) {
                    log.info(
                        "Lest {} oppgaver totalt, antall oppdaterte behandlingsutfall {}, lastTimestamp {}",
                        counterAll, counterOppdatertBehandlingsutfall, lastTimestamp
                    )
                    lastCounter = counterAll
                }
                delay(30000)
            }
        }
        while (applicationState.ready) {
            val opprettedeOppgaver: List<ProduceTask> =
                kafkaConsumer.poll(Duration.ofMillis(100)).filter {
                    counterAll++
                    lastTimestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(it.timestamp()), ZoneId.systemDefault())
                    lastTimestamp.isBefore(LocalDateTime.of(2019, Month.NOVEMBER, 1, 0, 0))
                }.map {
                    it.value().produceTask
                }
            for (oppgave in opprettedeOppgaver) {
                try {
                    val sykmeldingId =
                        databasePostgres.connection.hentSykmeldingIdManglerBehandlingsutfall(oppgave.messageId)
                    if (sykmeldingId != null) {
                        databasePostgres.connection.lagreBehandlingsutfallAndCommit(
                            Behandlingsutfall(
                                sykmeldingId,
                                ValidationResult(
                                    Status.MANUAL_PROCESSING,
                                    mapOppgaveTilRegler(oppgave.beskrivelse, ruleMap)
                                )
                            )
                        )
                        counterOppdatertBehandlingsutfall++
                    }
                } catch (ex: Exception) {
                    log.error("Noe gikk galt med msgId {}", oppgave.messageId, ex)
                    applicationState.ready = false
                    break
                }
            }
        }
    }
}

fun mapOppgaveTilRegler(oppgavebeskrivelse: String, ruleMap: Map<String, RuleInfo>): List<RuleInfo> {
    val regelListe = ArrayList<RuleInfo>()
    val regler: String = oppgavebeskrivelse.substringAfter(": ").trimStart('(').trimEnd(')')

    val liste = regler.split(", ")
    liste.forEach {
        val ruleInfo = ruleMap[it]
        if (ruleInfo != null && ruleInfo.ruleName != "IGNORE") {
            regelListe.add(ruleInfo)
        }
    }
    return regelListe
}
