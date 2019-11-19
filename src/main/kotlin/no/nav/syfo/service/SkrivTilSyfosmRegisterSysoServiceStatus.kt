package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Duration
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.log
import no.nav.syfo.model.StatusSyfoService
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.oppdaterSykmeldingStatus
import org.apache.kafka.clients.consumer.KafkaConsumer

class SkrivTilSyfosmRegisterSysoServiceStatus(
    private val kafkaconsumerSyfoServiceSykmeldingStatus: KafkaConsumer<String, String>,
    private val databasePostgres: DatabaseInterfacePostgres,
    private val sm2013SyfoSericeSykmeldingStatusTopic: String,
    private val applicationState: ApplicationState
) {

    fun run() {
        var counter = 0
        kafkaconsumerSyfoServiceSykmeldingStatus.subscribe(
            listOf(
                sm2013SyfoSericeSykmeldingStatusTopic
            )
        )
        log.info("Started kafkakonsumer")
        while (applicationState.ready) {
            val listStatusSyfoService: List<StatusSyfoService> = kafkaconsumerSyfoServiceSykmeldingStatus.poll(Duration.ofMillis(100)).map {
                objectMapper.readValue<StatusSyfoService>(it.value())
            }
            if (listStatusSyfoService.isNotEmpty()) {
                counter += listStatusSyfoService.size
                if (counter % 10_000 == 0) {
                    log.info("searched through : {} sykmeldinger status", counter)
                } else {
                    databasePostgres.connection.oppdaterSykmeldingStatus(listStatusSyfoService)
                }
            }
        }
    }
}
