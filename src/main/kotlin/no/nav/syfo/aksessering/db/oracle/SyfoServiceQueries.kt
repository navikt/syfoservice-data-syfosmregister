package no.nav.syfo.aksessering.db.oracle

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.log
import java.io.StringReader
import java.io.StringWriter
import java.lang.Boolean.TRUE
import java.sql.Date
import java.sql.ResultSet
import java.time.LocalDate
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBException
import javax.xml.bind.Marshaller.JAXB_ENCODING
import javax.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT
import javax.xml.bind.Marshaller.JAXB_FRAGMENT
import javax.xml.transform.stream.StreamResult

data class DatabaseResult<T>(
    val lastIndex: Int,
    val rows: List<T>
)

fun DatabaseInterfaceOracle.getSykmeldingsDokument(sykmeldingId: String): DatabaseResult<HelseOpplysningerArbeidsuforhet?> {
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT sd.dokument FROM SYFOSERVICE.SYKMELDING_DOK sd 
                WHERE MELDING_ID = ?
                """
        ).use {
            it.setString(1, sykmeldingId)
            val resultSet = it.executeQuery()
            val databaseResult = resultSet.toHelseOpplysningerArbeidsuforhet()
            return databaseResult
        }
    }
}

private fun ResultSet.toHelseOpplysningerArbeidsuforhet(): DatabaseResult<HelseOpplysningerArbeidsuforhet?> {

    if (next()) {
        val context = JAXBContext.newInstance(
            HelseOpplysningerArbeidsuforhet::class.java
        )
        return DatabaseResult(0, listOf(context.createUnmarshaller().unmarshal(StringReader(getString("dokument"))) as HelseOpplysningerArbeidsuforhet))
    }
    return DatabaseResult(0, emptyList())
}

fun getStringForDokument(dokument: HelseOpplysningerArbeidsuforhet): String {
    try {
        val writer = StringWriter()
        val context = JAXBContext.newInstance(
            HelseOpplysningerArbeidsuforhet::class.java
        )
        val marshaller = context.createMarshaller()
        marshaller.setProperty(JAXB_FORMATTED_OUTPUT, TRUE)
        marshaller.setProperty(JAXB_ENCODING, "UTF-8")
        marshaller.setProperty(JAXB_FRAGMENT, true)
        marshaller.marshal(dokument, StreamResult(writer))
        return writer.toString()
    } catch (e: JAXBException) {
        throw RuntimeException(e)
    }
}

fun DatabaseInterfaceOracle.updateDocument(dokument: HelseOpplysningerArbeidsuforhet, sykmeldingId: String) {
    connection.use { connection ->
        connection.prepareStatement(
            """
                update SYFOSERVICE.SYKMELDING_DOK set dokument = ?
                WHERE MELDING_ID = ?
                """
        ).use {
            it.setString(1, getStringForDokument(dokument))
            it.setString(2, sykmeldingId)
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterfaceOracle.updateDocumentAndBehandletDato(dokument: HelseOpplysningerArbeidsuforhet, behandletDato: LocalDate, sykmeldingId: String) {
    connection.use { connection ->
        connection.prepareStatement(
            """
                update SYFOSERVICE.SYKMELDING_DOK set dokument = ?, behandlet_dato = ?
                WHERE MELDING_ID = ?
                """
        ).use {
            it.setString(1, getStringForDokument(dokument))
            it.setDate(2, Date.valueOf(behandletDato))
            it.setString(3, sykmeldingId)
            it.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterfaceOracle.settTilNy(sykmeldingId: String) {
    connection.use { connection ->
        connection.prepareStatement(
            """
                update SYKMELDING_DOK set status = 'NY', sendt_til_arbeidsgiver_dato = NULL
                WHERE MELDING_ID = ?
                """
        ).use {
            it.setString(1, sykmeldingId)
            val updated = it.executeUpdate()
            log.info("Updated {} status", updated)
        }
        connection.commit()
    }
}

fun DatabaseInterfaceOracle.settTilSlettet(sykmeldingId: String) {
    connection.use { connection ->
        connection.prepareStatement(
            """
                update SYKMELDING_DOK set status = 'SLETTET', sendt_til_arbeidsgiver_dato = NULL
                WHERE MELDING_ID = ?
                """
        ).use {
            it.setString(1, sykmeldingId)
            val updated = it.executeUpdate()
            log.info("Updated {} status", updated)
        }
        connection.commit()
    }
}

data class AntallSykmeldinger(
    val antall: String
)

fun ResultSet.toAntallSykmeldinger(): AntallSykmeldinger =
    AntallSykmeldinger(
        antall = getString("antall")
    )
