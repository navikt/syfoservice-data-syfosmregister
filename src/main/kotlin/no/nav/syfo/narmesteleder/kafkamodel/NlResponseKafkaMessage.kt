package no.nav.syfo.narmesteleder.kafkamodel

data class NlResponseKafkaMessage(
    val kafkaMetadata: KafkaMetadata,
    val nlResponse: NlResponse?,
    val nlAvbrutt: NlAvbrutt? = null
)
