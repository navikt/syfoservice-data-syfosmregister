package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Duration
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.log
import no.nav.syfo.model.Eia
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.erSykmeldingsopplysningerLagret
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
        var counterDuplicates = 0
        while (applicationState.ready) {
            kafkaconsumerEia.subscribe(
                listOf(
                    sm2013EiaSykmedlingTopic
                )
            )
            kafkaconsumerEia.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
                val eia: Eia = objectMapper.readValue(consumerRecord.value())

                counter++
                if (counter % 10000 == 0) {
                    log.info("searched through : {} sykmeldinger", counter)
                } else if (databasePostgres.connection.erSykmeldingsopplysningerLagret(
                        "",
                        convertToMottakid(eia.mottakid)
                    )
                ) {
                    counterDuplicates++
                    if (counterDuplicates % 10000 == 0) {
                        log.info(
                            "10000 duplikater er registrer og vil ikke bli oppdatert", eia.mottakid
                        )
                    }
                } else {
                    databasePostgres.connection.oppdaterSykmeldingsopplysninger(eia)
                }
            }
        }
    }
}
