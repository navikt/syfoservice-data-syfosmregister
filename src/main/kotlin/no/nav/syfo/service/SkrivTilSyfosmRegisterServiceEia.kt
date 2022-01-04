package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.log
import no.nav.syfo.model.Eia
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.oppdaterSykmeldingsopplysninger
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class SkrivTilSyfosmRegisterServiceEia(
    private val kafkaconsumerEia: KafkaConsumer<String, String>,
    private val databasePostgres: DatabaseInterfacePostgres,
    private val sm2013EiaSykmedlingTopic: String,
    private val applicationState: ApplicationState
) {

    fun run() {
        var counter = 0
        kafkaconsumerEia.subscribe(
            listOf(
                sm2013EiaSykmedlingTopic
            )
        )
        log.info("Started kafkakonsumer")
        while (applicationState.ready) {
            val listEia: List<Eia> = kafkaconsumerEia.poll(Duration.ofMillis(100)).map {
                objectMapper.readValue<Eia>(it.value())
            }
            if (listEia.isNotEmpty()) {
                counter += listEia.size
                databasePostgres.connection.oppdaterSykmeldingsopplysninger(listEia)
                log.info("Updated: {} sykmeldinger", counter)
            }
        }
    }
}
