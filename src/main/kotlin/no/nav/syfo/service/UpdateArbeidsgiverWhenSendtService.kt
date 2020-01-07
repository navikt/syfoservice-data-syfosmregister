package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.log
import no.nav.syfo.model.ArbeidsgiverStatus
import no.nav.syfo.model.Mapper.Companion.mapToSykmeldingStatusTopicEvent
import no.nav.syfo.model.ShortName
import no.nav.syfo.model.Sporsmal
import no.nav.syfo.model.StatusEvent
import no.nav.syfo.model.Svar
import no.nav.syfo.model.Svartype
import no.nav.syfo.model.SykmeldingStatusTopicEvent
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.hentArbeidsgiverStatus
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.time.LocalDateTime
import java.time.Month

class UpdateArbeidsgiverWhenSendtService(
    val kafkaConsumer: KafkaConsumer<String, String>,
    val databasePostgres: DatabasePostgres,
    val sykmeldingStatusCleanTopic: String,
    val applicationState: ApplicationState
) {

    private var searchCounter: Int = 0
    private var updateCounter: Int = 0

    fun run() {

        GlobalScope.launch {
            var lastCounter: Int = 0
            while (applicationState.ready) {
                if (lastCounter != searchCounter) {
                    log.info("Lest: {} events, oppdaterte arbeidsgivere: {}", searchCounter, updateCounter)
                    lastCounter = searchCounter
                }
                delay(30_000)
            }
        }

        kafkaConsumer.subscribe(
            listOf(sykmeldingStatusCleanTopic)
        )
        while (applicationState.ready) {
            val sykmeldingStatusTopicEvents = kafkaConsumer.poll(Duration.ofMillis(100)).asSequence().map {
                val map = objectMapper.readValue<Map<String, Any?>>(it.value())
                searchCounter++
                mapToSykmeldingStatusTopicEvent(map, null)
            }
                .filter { it.sykmeldingId.length <= 64 }
                .filter { it.status == StatusEvent.SENDT }
                .filter { it.created.isAfter(LocalDateTime.of(2019, Month.AUGUST, 1, 0, 0)) }

            for (update in sykmeldingStatusTopicEvents) {
                insertSendtStatusWithArbeidsgiver(update)
            }
        }
    }

    fun insertSendtStatusWithArbeidsgiver(sykmeldingStatusTopicEvent: SykmeldingStatusTopicEvent) {
        val sporsmalOgSvar: List<Sporsmal> = getSporsmalOgSvarForSend(sykmeldingStatusTopicEvent)
        val lagretArbeidsgiver = getArbeidsgiver(sykmeldingStatusTopicEvent.sykmeldingId)
        if (lagretArbeidsgiver == null) {
//            databasePostgres.slettOgInsertArbeidsgiver(sykmeldingStatusTopicEvent.sykmeldingId, sporsmalOgSvar, sykmeldingStatusTopicEvent.arbeidsgiver!!)
            updateCounter++
        }
    }

    private fun getArbeidsgiver(sykmeldingId: String): ArbeidsgiverStatus? {
        return databasePostgres.connection.hentArbeidsgiverStatus(sykmeldingId).firstOrNull()
    }
    private fun getSporsmalOgSvarForSend(sykmeldingStatusTopicEvent: SykmeldingStatusTopicEvent): List<Sporsmal> {
        val sporsmals = ArrayList<Sporsmal>()

        val arbeidssituasjon = Sporsmal(
            "Jeg er sykmeldt fra",
            ShortName.ARBEIDSSITUASJON,
            Svar(
                sykmeldingId = sykmeldingStatusTopicEvent.sykmeldingId,
                svartype = Svartype.ARBEIDSSITUASJON,
                svar = "ARBEIDSTAKER",
                sporsmalId = null
            )
        )
        sporsmals.add(arbeidssituasjon)
        return sporsmals
    }
}
