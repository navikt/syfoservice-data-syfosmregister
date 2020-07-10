package no.nav.syfo.legeerklaring
data class SimpleReceivedLegeerklaeering(val fellesformat: String)
data class SimpleLegeerklaeringKafkaMessage(val receivedLegeerklaering: SimpleReceivedLegeerklaeering)
