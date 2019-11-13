package no.nav.syfo.aksessering.db.oracle

import java.io.StringReader
import java.sql.ResultSet
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.db.toList
import no.nav.syfo.log
import no.nav.syfo.model.Eia
import no.nav.syfo.utils.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.utils.extractOrganisationHerNumberFromSender
import no.nav.syfo.utils.extractOrganisationNumberFromSender
import no.nav.syfo.utils.extractOrganisationRashNumberFromSender
import no.nav.syfo.utils.fellesformatUnmarshaller
import no.nav.syfo.utils.get

fun DatabaseInterfaceOracle.hentSykmeldingerEia(lastIndex: Int, limit: Int): DatabaseResult<Eia> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                    SELECT m.MELDING_ID, m.EDILOGGID, m.PASIENT_ID, m.AVSENDERID, mx.MELDING_XML
                    FROM melding m, behandling_forsok bf, melding_status_historikk msh, 
                    behandling_forsok_xml_type bfxt, melding_xml mx
                    WHERE bf.BEHANDLING_FORSOK_ID = m.AKTIV_BEHANDLING
                    AND bf.AKTIV_STATUS = msh.MELDING_STATUS_ID
                    AND bfxt.BEHANDLING_FORSOK_ID = bf.BEHANDLING_FORSOK_ID
                    AND bfxt.MELDING_XML_ID = mx.MELDING_XML_ID
                    AND m.melding_type_kode = 'SYKMELD'
                    AND bfxt.MELDING_XML_TYPE = 'MELDING'
                    AND msh.STATUS_KODE != 'AVVIST'
                    AND bfxt.xml_skjema = 'http://www.kith.no/xmlstds/HelseOpplysningerArbeidsuforhet/2013-10-01'
                    AND trunc( m.REGISTRERT_DATO) >= to_date('2016-05-11','YYYY-MM-DD')
                    AND m.MELDING_ID > ?
                    ORDER BY m.melding_id ASC
                    FETCH NEXT ? ROWS ONLY
                        """
        ).use {
            it.setInt(1, lastIndex)
            it.setInt(2, limit)
            val currentMillies = System.currentTimeMillis()
            val resultSet = it.executeQuery()
            val databaseEndMillies = System.currentTimeMillis()
            val databaseResult = resultSet.toEia(lastIndex)
            val processingMillies = System.currentTimeMillis()

            databaseResult.databaseTime = (databaseEndMillies - currentMillies) / 1000.0
            databaseResult.processingTime = (processingMillies - databaseEndMillies) / 1000.0
            return databaseResult
        }
    }

fun ResultSet.toEia(previusIndex: Int): DatabaseResult<Eia> {

    val listEia = ArrayList<Eia>()
    var lastIndex = previusIndex

    while (next()) {

        val ediLoggId = getString("EDILOGGID")
        log.info("Ediloggid for mapping: {}", ediLoggId)
        lastIndex = getInt("MELDING_ID")
        try {
            val fellesformat =
                fellesformatUnmarshaller.unmarshal(StringReader(getString("MELDING_XML"))) as XMLEIFellesformat

            val msgHead = fellesformat.get<XMLMsgHead>()
            val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
            val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
            val legekontorReshId = extractOrganisationRashNumberFromSender(fellesformat)?.id
            val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
            val legekontorOrgName = msgHead.msgInfo.sender.organisation.organisationName
            val personNumberPatient = getString("PASIENT_ID")
            val personNumberDoctor = getString("AVSENDERID")

            listEia.add(
                Eia(
                    pasientfnr = personNumberPatient,
                    legefnr = setPersonNumberDoctor(personNumberDoctor),
                    mottakid = ediLoggId,
                    legekontorOrgnr = legekontorOrgNr,
                    legekontorOrgnavn = legekontorOrgName,
                    legekontorHer = legekontorHerId,
                    legekontorResh = legekontorReshId,
                    epjSystemNavn = healthInformation.avsenderSystem.systemNavn,
                    epjSystemVersjon = healthInformation.avsenderSystem.systemVersjon
                )
            )
        } catch (e: Exception) {
            log.warn("Sykmelding feiler på mapping med Ediloggid: {}", ediLoggId)
        }
    }
    return DatabaseResult(lastIndex, listEia)
}

private fun setPersonNumberDoctor(personNumberDoctor: String?): String {
    return if (personNumberDoctor.isNullOrEmpty()) {
        ""
    } else {
        personNumberDoctor
    }
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
                    AND msh.STATUS_KODE != 'AVVIST'
                    AND bfxt.xml_skjema = 'http://www.kith.no/xmlstds/HelseOpplysningerArbeidsuforhet/2013-10-01'
                    AND trunc( m.REGISTRERT_DATO) >= to_date('2016-05-11','YYYY-MM-DD')
                    ORDER BY m.melding_id DESC
                        """
        ).use {
            it.executeQuery().toList { toAntallSykmeldinger() }
        }
    }
