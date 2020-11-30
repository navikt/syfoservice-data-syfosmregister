package no.nav.syfo.papirsykmelding.tilsyfoservice.kafka.model

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet

data class SykmeldingSyfoserviceKafkaMessage(
    val metadata: KafkaMessageMetadata,
    val helseopplysninger: HelseOpplysningerArbeidsuforhet
)
