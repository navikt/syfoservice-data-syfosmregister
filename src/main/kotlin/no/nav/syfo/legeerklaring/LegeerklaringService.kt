package no.nav.syfo.legeerklaring

import java.time.Duration
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.getVaultServiceUser
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.legeerklaring.LegeerklaringMapper.Companion.mapToXml
import no.nav.syfo.legeerklaring.util.extractLegeerklaering
import no.nav.syfo.legeerklaring.util.extractOrganisationHerNumberFromSender
import no.nav.syfo.legeerklaring.util.extractOrganisationNumberFromSender
import no.nav.syfo.legeerklaring.util.extractOrganisationRashNumberFromSender
import no.nav.syfo.legeerklaring.util.extractPersonIdent
import no.nav.syfo.legeerklaring.util.extractSenderOrganisationName
import no.nav.syfo.legeerklaring.util.sha256hashstring
import no.nav.syfo.log
import no.nav.syfo.utils.get
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

class LegeerklaringService(private val environment: Environment, private val applicationState: ApplicationState) {

    private val kafkaConsumer: KafkaConsumer<String?, String>
    init {
        val vaultServiceuser = getVaultServiceUser()
        val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceuser)
        val consumerProperties = kafkaBaseConfig.toConsumerConfig("${environment.applicationName}-consumer-2",
        StringDeserializer::class)
        kafkaConsumer = KafkaConsumer(consumerProperties)
    }

    fun start() {
        var counter = 0
        GlobalScope.launch {
            while (applicationState.ready) {
                log.info("Lest og mapped {} legeærklæringer", counter)
                delay(30_000)
            }
        }
        kafkaConsumer.subscribe(listOf(environment.pale2dump))
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofMillis(1000))
            records.forEach {
                val fellesformat = mapToXml(it.value())
                val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
                val msgHead = fellesformat.get<XMLMsgHead>()
                val ediLoggId = receiverBlock.ediLoggId
                val msgId = msgHead.msgInfo.msgId
                val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
                val legeerklaringxml = extractLegeerklaering(fellesformat)
                val sha256String = sha256hashstring(legeerklaringxml)
                val fnrPasient = extractPersonIdent(legeerklaringxml)!!
                val legekontorOrgName = extractSenderOrganisationName(fellesformat)
                val fnrLege = receiverBlock.avsenderFnrFraDigSignatur
                val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
                val legekontorReshId = extractOrganisationRashNumberFromSender(fellesformat)?.id

                counter++
            }
        }
    }
}
