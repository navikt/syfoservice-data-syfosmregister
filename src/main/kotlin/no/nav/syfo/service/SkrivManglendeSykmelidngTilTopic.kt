package no.nav.syfo.service

import no.nav.syfo.aksessering.db.oracle.hentSykmeldingFraSyfoService
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.toReceivedSykmelding
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SkrivManglendeSykmelidngTilTopic(
    private val kafkaProducer: KafkaProducer<String, ReceivedSykmelding>,
    private val databaseOracle: DatabaseOracle
) {

    fun run() {
        var result = databaseOracle.hentSykmeldingFraSyfoService(
            listOf(
                "50c04985-7b25-47c5-9d90-7178cccbab5e"
            )
        )

        result.rows.forEach {
            val receivedSykmelding = toReceivedSykmelding(it)
            kafkaProducer.send(
                ProducerRecord(
                    "privat-syfo-sm2013-automatiskBehandling",
                    receivedSykmelding.sykmelding.id,
                    receivedSykmelding
                )
            )

            log.info("Sendt sykmelding til automatisk topic sykmeldingId {}", receivedSykmelding.sykmelding.id)
        }
    }
}
