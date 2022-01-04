package no.nav.syfo.legeerklaring.util

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.legeerklaering.Legeerklaring
import no.nav.helse.msgHead.XMLIdent
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.objectMapper
import java.security.MessageDigest

fun sha256hashstring(legeerklaring: Legeerklaring): String =
    MessageDigest.getInstance("SHA-256")
        .digest(objectMapper.writeValueAsBytes(legeerklaring))
        .fold("") { str, it -> str + "%02x".format(it) }

fun extractOrganisationNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find {
        it.typeId.v == "ENH"
    }

fun extractOrganisationHerNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find {
        it.typeId.v == "HER"
    }

fun extractOrganisationRashNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find {
        it.typeId.v == "RSH"
    }

fun extractLegeerklaering(fellesformat: XMLEIFellesformat): Legeerklaring =
    fellesformat.get<XMLMsgHead>().document[0].refDoc.content.any[0] as Legeerklaring

fun extractSenderOrganisationName(fellesformat: XMLEIFellesformat): String =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation?.organisationName ?: ""

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T

fun extractPersonIdent(legeerklaering: Legeerklaring): String? =
    legeerklaering.pasientopplysninger.pasient.fodselsnummer
