package no.nav.syfo.aksessering.db.oracle

import java.io.StringReader
import java.sql.ResultSet
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.db.toList
import no.nav.syfo.model.Eia
import no.nav.syfo.utils.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.utils.extractOrganisationHerNumberFromSender
import no.nav.syfo.utils.extractOrganisationNumberFromSender
import no.nav.syfo.utils.extractOrganisationRashNumberFromSender
import no.nav.syfo.utils.fellesformatUnmarshaller
import no.nav.syfo.utils.get

fun DatabaseInterfaceOracle.hentSykmeldingerEia(): List<Eia> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                    SELECT m.EDILOGGID, m.PASIENT_ID, m.AVSENDERID, mx.MELDING_XML
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
            it.executeQuery().toList { toEia() }
        }
    }

fun ResultSet.toEia(): Eia {

    val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(getString("MELDING_XML"))) as XMLEIFellesformat
    val msgHead = fellesformat.get<XMLMsgHead>()
    val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
    val ediLoggId = getString("EDILOGGID")
    val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
    val legekontorReshId = extractOrganisationRashNumberFromSender(fellesformat)?.id
    val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
    val legekontorOrgName = msgHead.msgInfo.sender.organisation.organisationName
    val personNumberPatient = getString("PASIENT_ID")
    val personNumberDoctor = getString("AVSENDERID")

    return Eia(
        pasientfnr = personNumberPatient,
        legefnr = personNumberDoctor,
        mottakid = ediLoggId,
        legekontorOrgnr = legekontorOrgNr,
        legekontorOrgnavn = legekontorOrgName,
        legekontorHer = legekontorHerId,
        legekontorResh = legekontorReshId,
        epjSystemNavn = healthInformation.avsenderSystem.systemNavn,
        epjSystemVersjon = healthInformation.avsenderSystem.systemVersjon
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
