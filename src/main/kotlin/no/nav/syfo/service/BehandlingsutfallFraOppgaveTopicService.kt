package no.nav.syfo.service

import java.time.Duration
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.log
import no.nav.syfo.model.ProduceTask
import no.nav.syfo.model.RegisterTask
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.objectMapper
import org.apache.kafka.clients.consumer.KafkaConsumer

class BehandlingsutfallFraOppgaveTopicService(
    private val kafkaConsumer: KafkaConsumer<String, RegisterTask>,
    private val databasePostgres: DatabaseInterfacePostgres,
    private val oppgaveTopic: String,
    private val applicationState: ApplicationState
) {

    fun lagreManuellbehandlingFraOppgaveTopic() {
        kafkaConsumer.subscribe(
            listOf(
                oppgaveTopic
            )
        )
        var counterAll = 0
        var counter = 0
        var counterDuplikat = 0
        var counterOppdatertBehandlingsutfall = 0
        var lastCounter = 0
        val map = HashMap<String, Int>()
        GlobalScope.launch {
            while (applicationState.ready) {
                if (lastCounter != counterAll) {
                    log.info(
                        "Lest {} oppgaver totalt, Data types {}",
                        counterAll, objectMapper.writeValueAsString(map)
                    )
                    lastCounter = counterAll
                }
                delay(30000)
            }
        }
        while (applicationState.ready) {
            val opprettedeOppgaver: List<ProduceTask> =
                kafkaConsumer.poll(Duration.ofMillis(100)).map {
                    counterAll++
                    it.value().produceTask
                }
            for (oppgave in opprettedeOppgaver) {
                val regelListe = mapOppgaveTilRegler(oppgave.beskrivelse)
                regelListe.forEach {
                    if (!map.containsKey(it.messageForSender)) {
                        map[it.messageForSender] = 0
                    }
                    map[it.messageForSender] = map[it.messageForSender]!!.plus(1)
                }

            }
//            for (oppgave in opprettedeOppgaver) {
//                try {
//                    val sykmeldingId = databasePostgres.connection.hentSykmeldingIdManglerBehandlingsutfall(oppgave.messageId)
//                    if (sykmeldingId != null) {
//                        databasePostgres.connection.lagreBehandlingsutfall(Behandlingsutfall(sykmeldingId, ValidationResult(Status.MANUAL_PROCESSING, mapOppgaveTilRegler(oppgave.beskrivelse))))
//                        counterOppdatertBehandlingsutfall++
//                    }
//                    // finn riktig sykmelding, mottatt før 2020 og mangler behandlingsutfall (hent kun id)
//                    // hvis treff: Lagre behandlingsutfall
//                } catch (ex: Exception) {
//                    log.error("Noe gikk galt med msgId {}", oppgave.messageId, ex)
//                    applicationState.ready = false
//                    break
//                }
//            }
        }
    }
}

fun mapOppgaveTilRegler(oppgavebeskrivelse: String): List<RuleInfo> {
    System.out.println(oppgavebeskrivelse)
    val regelListe = ArrayList<RuleInfo>()
    val regler: String = oppgavebeskrivelse.substringAfter(": ").trimStart('(').trimEnd(')')
    System.out.println(regler)

    val liste = regler.split(", ")
    liste.forEach {
        regelListe.add(RuleInfo(it, it, it, Status.MANUAL_PROCESSING))
    }
    return regelListe
}
