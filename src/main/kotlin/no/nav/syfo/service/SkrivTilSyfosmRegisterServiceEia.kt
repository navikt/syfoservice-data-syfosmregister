package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Duration
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.log
import no.nav.syfo.model.Eia
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.oppdaterSykmeldingsopplysninger
import org.apache.kafka.clients.consumer.KafkaConsumer

class SkrivTilSyfosmRegisterServiceEia(
    private val kafkaconsumerEia: KafkaConsumer<String, String>,
    private val databasePostgres: DatabaseInterfacePostgres,
    private val sm2013EiaSykmedlingTopic: String,
    private val applicationState: ApplicationState
) {

    fun run() {
        var counter = 0
        while (applicationState.ready) {
            kafkaconsumerEia.subscribe(
                listOf(
                    sm2013EiaSykmedlingTopic
                )
            )
            val listEia = kafkaconsumerEia.poll(Duration.ofMillis(0)).map { consumerRecord ->
                objectMapper.readValue<Eia>(consumerRecord.value())
            }

            counter++
            if (counter % 100 == 0) {
                log.info("searched through : {} sykmeldinger", counter)
            } else {
                databasePostgres.connection.oppdaterSykmeldingsopplysninger(listEia)
            }
        }
    }
}
