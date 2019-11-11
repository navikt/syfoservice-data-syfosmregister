package no.nav.syfo.aksessering.db.oracle

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import java.io.StringReader
import java.sql.ResultSet
import java.util.UUID
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.db.toList
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.toSykmelding
import no.nav.syfo.utils.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.utils.extractOrganisationHerNumberFromSender
import no.nav.syfo.utils.extractOrganisationNumberFromSender
import no.nav.syfo.utils.extractOrganisationRashNumberFromSender
import no.nav.syfo.utils.fellesformatUnmarshaller
import no.nav.syfo.utils.get
import java.time.ZoneOffset

fun DatabaseInterfaceOracle.hentSykmeldingerEia(aktor_id: String): List<ReceivedSykmelding> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                    SELECT M.MELDING_ID
                    , m.PASIENT_ID
                    , m.MELDING_TYPE_KODE, bfxt.MELDING_XML_TYPE
                    , m.EDILOGGID , m.PASIENT_ID
                    , m.AVSENDERID ,msh.STATUS_KODE
                    , m.AKTIV_BEHANDLING, bf.BEHANDLING_FORSOK_ID
                    , msh.MELDING_STATUS_ID, mx.MELDING_XML_ID
                    , mx.MELDING_XML, bfxt.xml_skjema
                    FROM melding m, behandling_forsok bf, melding_status_historikk msh, 
                    behandling_forsok_xml_type bfxt, melding_xml mx
                    WHERE bf.BEHANDLING_FORSOK_ID = m.AKTIV_BEHANDLING
                    AND bf.AKTIV_STATUS = msh.MELDING_STATUS_ID
                    AND bfxt.BEHANDLING_FORSOK_ID = bf.BEHANDLING_FORSOK_ID
                    AND bfxt.MELDING_XML_ID = mx.MELDING_XML_ID
                    AND m.melding_type_kode = 'SYKMELD'
                    AND bfxt.MELDING_XML_TYPE = 'MELDING'
                    AND trunc( m.REGISTRERT_DATO) >= to_date('2016-05-11','YYYY-MM-DD')
                    ORDER BY m.melding_id DESC
                        """
        ).use {
            it.setString(1, aktor_id)
            it.executeQuery().toList { toReceivedSykmelding() }
        }
    }

fun ResultSet.toReceivedSykmelding(): ReceivedSykmelding {

    val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(getString("MELDING_XML"))) as XMLEIFellesformat
    val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
    val msgHead = fellesformat.get<XMLMsgHead>()
    val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
    val msgId = msgHead.msgInfo.msgId
    val ediLoggId = receiverBlock.ediLoggId
    val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
    val legekontorReshId = extractOrganisationRashNumberFromSender(fellesformat)?.id
    val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
    val legekontorOrgName = msgHead.msgInfo.sender.organisation.organisationName
    val personNumberPatient = healthInformation.pasient.fodselsnummer.id
    val personNumberDoctor = receiverBlock.avsenderFnrFraDigSignatur

    return ReceivedSykmelding(
        sykmelding = unmarshallerToHealthInformation(getString("MELDING_XML")).toSykmelding(
            sykmeldingId = UUID.randomUUID().toString(),
            pasientAktoerId = "",
            legeAktoerId = "",
            msgId = msgId,
            signaturDato = msgHead.msgInfo.genDate
        ),
        personNrPasient = personNumberPatient,
        tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
        personNrLege = personNumberDoctor,
        navLogId = ediLoggId,
        msgId = msgId,
        legekontorOrgNr = legekontorOrgNr,
        legekontorOrgName = legekontorOrgName,
        legekontorHerId = legekontorHerId,
        legekontorReshId = legekontorReshId,
        mottattDato = receiverBlock.mottattDatotid.toGregorianCalendar().toZonedDateTime().withZoneSameInstant(
            ZoneOffset.UTC
        ).toLocalDateTime(),
        rulesetVersion = healthInformation.regelSettVersjon,
        fellesformat = "",
        tssid = ""
    )
}

fun DatabaseInterfaceOracle.hentAntallSykmeldingerEia(): List<AntallSykmeldinger> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                    SELECT COUNT(M.MELDING_ID) as antall
                    FROM melding m, behandling_forsok bf, melding_status_historikk msh, 
                    behandling_forsok_xml_type bfxt, melding_xml mx
                    WHERE bf.BEHANDLING_FORSOK_ID = m.AKTIV_BEHANDLING
                    AND bf.AKTIV_STATUS = msh.MELDING_STATUS_ID
                    AND bfxt.BEHANDLING_FORSOK_ID = bf.BEHANDLING_FORSOK_ID
                    AND bfxt.MELDING_XML_ID = mx.MELDING_XML_ID
                    AND m.melding_type_kode = 'SYKMELD'
                    AND bfxt.MELDING_XML_TYPE = 'MELDING'
                    AND trunc( m.REGISTRERT_DATO) >= to_date('2016-05-11','YYYY-MM-DD')
                    ORDER BY m.melding_id DESC
                        """
        ).use {
            it.executeQuery().toList { toAntallSykmeldinger() }
        }
    }

fun unmarshallerToHealthInformation(healthInformation: String): HelseOpplysningerArbeidsuforhet =
    fellesformatUnmarshaller.unmarshal(StringReader(healthInformation)) as HelseOpplysningerArbeidsuforhet
