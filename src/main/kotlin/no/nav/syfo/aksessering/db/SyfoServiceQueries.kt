package no.nav.syfo.aksessering.db

import java.io.StringReader
import java.sql.ResultSet
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.toSykmelding
import no.nav.syfo.utils.fellesformatUnmarshaller

fun DatabaseInterface.hentSykmeldinger(aktor_id: String): List<ReceivedSykmelding> =
        connection.use { connection ->
            connection.prepareStatement(
                    """
                        SELECT *
                        FROM SYKMELDING_DOK
                        WHERE aktor_id=?
                        """
            ).use {
                it.setString(1, aktor_id)
                it.executeQuery().toList { toReceivedSykmelding() }
            }
        }

fun ResultSet.toReceivedSykmelding(): ReceivedSykmelding =
    ReceivedSykmelding(
        sykmelding = unmarshallerToHealthInformation(getString("dokument")).toSykmelding(
            sykmeldingId = getString("melding_id"),
            pasientAktoerId = getString("aktor_id"),
            legeAktoerId = "",
            msgId = "",
            signaturDato = LocalDateTime.now()
        ),
        personNrPasient = unmarshallerToHealthInformation(getString("dokument")).pasient.fodselsnummer.id,
        tlfPasient = unmarshallerToHealthInformation(getString("dokument")).pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
        personNrLege = "",
        navLogId = getString("mottak_id"),
        msgId = "",
        legekontorOrgNr = "",
        legekontorOrgName = "",
        legekontorHerId = "",
        legekontorReshId = "",
        mottattDato = LocalDateTime.now(),
        rulesetVersion = unmarshallerToHealthInformation(getString("dokument")).regelSettVersjon,
        fellesformat = "",
        tssid = ""
    )

fun unmarshallerToHealthInformation(healthInformation: String): HelseOpplysningerArbeidsuforhet =
    fellesformatUnmarshaller.unmarshal(StringReader(healthInformation)) as HelseOpplysningerArbeidsuforhet
