package no.nav.syfo.aksessering.db.oracle

import java.io.StringReader
import java.sql.ResultSet
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.db.toList
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.toSykmelding
import no.nav.syfo.utils.fellesformatUnmarshaller

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
                    ORDER BY m.melding_id DESC;
                        """
        ).use {
            it.setString(1, aktor_id)
            it.executeQuery().toList { toReceivedSykmelding() }
        }
    }

fun ResultSet.toReceivedSykmelding(): ReceivedSykmelding =
    ReceivedSykmelding(
            sykmelding = unmarshallerToHealthInformation(getString("MELDING_XML")).toSykmelding(
            sykmeldingId = UUID.randomUUID().toString(),
            pasientAktoerId = "",
            legeAktoerId = "",
            msgId = "",
            signaturDato = LocalDateTime.now()
        ),
        personNrPasient = "",
        tlfPasient = "",
        personNrLege = "",
        navLogId = "",
        msgId = "",
        legekontorOrgNr = "",
        legekontorOrgName = "",
        legekontorHerId = "",
        legekontorReshId = "",
        mottattDato = LocalDateTime.now(),
        rulesetVersion = null,
        fellesformat = "",
        tssid = ""
    )

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
                    ORDER BY m.melding_id DESC;
                        """
        ).use {
            it.executeQuery().toList { toAntallSykmeldinger() }
        }
    }

fun unmarshallerToHealthInformation(healthInformation: String): HelseOpplysningerArbeidsuforhet =
    fellesformatUnmarshaller.unmarshal(StringReader(healthInformation)) as HelseOpplysningerArbeidsuforhet
