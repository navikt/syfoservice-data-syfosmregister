package no.nav.syfo.legeerklaring

import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.legeerklaering.Legeerklaring
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.legeerklaringObjectMapper
import no.nav.syfo.utils.get

class LegeerklaringMapper private constructor() {
    companion object {
        fun mapToXml(value: String): XMLEIFellesformat {
            val fellesformatJson = legeerklaringObjectMapper.readValue<XMLEIFellesformat>(value)
            for (i: Int in fellesformatJson.any.indices) {
                fellesformatJson.any[i] = convertToXmlobject(fellesformatJson.any[i])
            }

            val xmlMsgHead = fellesformatJson.get<XMLMsgHead>()
            for (i: Int in xmlMsgHead.document.indices) {
                if (xmlMsgHead.document[i].refDoc.msgType.v == "XML") {
                    xmlMsgHead.document[i].refDoc.content.any[0] = legeerklaringObjectMapper.convertValue<Legeerklaring>(xmlMsgHead.document[i].refDoc.content.any[0])
                }
            }

            xmlMsgHead.document.removeIf { it.documentConnection?.v == "V" }
            return fellesformatJson
        }

        private fun convertToXmlobject(any: Any): Any {
            try {
                return legeerklaringObjectMapper.convertValue<XMLMottakenhetBlokk>(any)
            } catch (ex: Exception) {
            }

            try {
                return legeerklaringObjectMapper.convertValue<XMLMsgHead>(any)
            } catch (ex: Exception) {
                throw RuntimeException("Could not convert object")
            }
        }
    }
}
