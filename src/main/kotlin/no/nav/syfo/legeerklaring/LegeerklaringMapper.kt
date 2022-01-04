package no.nav.syfo.legeerklaring

import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.legeerklaering.Legeerklaring
import no.nav.helse.msgHead.ObjectFactory
import no.nav.helse.msgHead.XMLKeyValueType
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLRSAKeyValueType
import no.nav.helse.msgHead.XMLX509DataType
import no.nav.syfo.legeerklaringObjectMapper
import no.nav.syfo.objectMapper
import no.nav.syfo.utils.fellesformatUnmarshaller
import no.nav.syfo.utils.get
import java.io.StringReader
import javax.xml.namespace.QName

data class CustomeX509Cert(val value: String)
data class CustomeX509DataType(val x509IssuerSerialOrX509SKIOrX509SubjectName: List<CustomeX509Cert>)
data class CustomX509JAXBElement(val name: QName, val declaredType: Class<Any>?, val value: CustomeX509DataType)

data class CustomeKeyValueType(val modulus: String, val exponent: String)
data class CustomKeyValueElement(val name: QName, val declaredType: Class<Any?>, val value: CustomeKeyValueType)
data class CustomContent(var content: ArrayList<Any>)

data class CustomXmlKeyValueElement(val name: QName, val declaredType: Class<Any>?, val value: CustomContent)

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
                    xmlMsgHead.document[i].refDoc.content.any[0] =
                        legeerklaringObjectMapper.convertValue<Legeerklaring>(xmlMsgHead.document[i].refDoc.content.any[0])
                }
            }
            if (xmlMsgHead.signature?.keyInfo?.content != null) {
                xmlMsgHead.signature.keyInfo.content.removeIf { it is String }
                for (i: Int in xmlMsgHead.signature.keyInfo.content.indices) {
                    xmlMsgHead.signature.keyInfo.content[i] =
                        convertKeyInfoToXmlobject(xmlMsgHead.signature.keyInfo.content[i])
                }
            }

            xmlMsgHead.document.removeIf { it.documentConnection?.v == "V" }
            return fellesformatJson
        }

        private fun convertKeyInfoToXmlobject(any: Any): Any {
            try {
                val jsonNode = objectMapper.convertValue<CustomX509JAXBElement>(any)
                if (jsonNode.declaredType == XMLX509DataType::class.java) {
                    val x509Data = ObjectFactory().createXMLX509DataType()
                    x509Data.x509IssuerSerialOrX509SKIOrX509SubjectName.add(
                        ObjectFactory().createXMLX509DataTypeX509Certificate(
                            jsonNode.value.x509IssuerSerialOrX509SKIOrX509SubjectName.get(0).value.toByteArray()
                        )
                    )
                    return ObjectFactory().createX509Data(x509Data)
                }
            } catch (ex: Exception) {
            }
            try {
                val jsonNode = objectMapper.convertValue<CustomXmlKeyValueElement>(any)
                if (jsonNode.declaredType == XMLKeyValueType::class.java) {
                    jsonNode.value.content.removeIf { it is String }
                    val keyType = objectMapper.convertValue<CustomKeyValueElement>(jsonNode.value.content[0])
                    if (keyType.declaredType == XMLRSAKeyValueType::class.java) {

                        val valueType = ObjectFactory().createXMLRSAKeyValueType()
                        valueType.exponent = keyType.value.exponent.toByteArray()
                        valueType.modulus = keyType.value.modulus.toByteArray()
                        ObjectFactory().createRSAKeyValue(valueType)
                        val keyValueType = ObjectFactory().createXMLKeyValueType()
                        val rsaKeyValueTYpe = ObjectFactory().createRSAKeyValue(valueType)
                        keyValueType.content.add(rsaKeyValueTYpe)
                        return ObjectFactory().createKeyValue(keyValueType)
                    }
                }
            } catch (ex: Exception) {
            }

            throw RuntimeException("Error")
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

        fun getAdjustedXml(message: SimpleLegeerklaeringKafkaMessage): XMLEIFellesformat {
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(message.receivedLegeerklaering.fellesformat)) as XMLEIFellesformat
            return getMappedDumpXml(objectMapper.writeValueAsString(fellesformat))
        }

        fun getMappedDumpXml(dumpmessage: String): XMLEIFellesformat {
            return mapToXml(dumpmessage)
        }
    }
}
