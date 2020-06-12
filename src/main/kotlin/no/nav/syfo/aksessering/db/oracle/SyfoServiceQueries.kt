package no.nav.syfo.aksessering.db.oracle

import java.io.StringReader
import java.io.StringWriter
import java.lang.Boolean.TRUE
import java.sql.ResultSet
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBException
import javax.xml.bind.Marshaller.JAXB_ENCODING
import javax.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT
import javax.xml.bind.Marshaller.JAXB_FRAGMENT
import javax.xml.transform.stream.StreamResult
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.db.toList
import no.nav.syfo.log

data class DatabaseResult<T>(
    val lastIndex: Int,
    val rows: List<T>
)

fun DatabaseInterfaceOracle.hentFravaerForSykmelding(sporsmalId: Int): DatabaseResult<MutableMap<String, Any?>> {
    connection.use { connection ->
        connection.prepareStatement(
            """
                 select * from sm_sporsmal_periode where sm_sporsmal_id = ?
                """
        ).use {
            it.setInt(1, sporsmalId)
            val resultSet = it.executeQuery()
            val databaseResult = resultSet.toJsonStringSyfoServiceFravaer()
            return databaseResult
        }
    }
}

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

fun DatabaseInterfaceOracle.updateDiagnose(dokument: HelseOpplysningerArbeidsuforhet, sykmeldingId: String) {
    connection.use { connection ->
        connection.prepareStatement(
            """
                update SYFOSERVICE.SYKMELDING_DOK set dokument = ?
                WHERE MELDING_ID = ?
                """
        ).use {
            it.setString(2, getStringForDokument(dokument))
            it.setString(2, sykmeldingId)
            val updated = it.executeUpdate()
            log.info("Updated {} sykmeldingsdokument", updated)
        }
        connection.commit()
    }
}

fun DatabaseInterfaceOracle.hentSykmeldingFraSyfoService(sykmeldingIds: List<String>): DatabaseResult<MutableMap<String, Any?>> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT sd.SYKMELDING_DOK_ID, 
                sd.AKTOR_ID, 
                sd.MELDING_ID, 
                sd.DOKUMENT, 
                sd.CREATED, 
                sd.ARBEIDSSITUASJON, 
                sd.SYKETILFELLE_START_DATO,
                sd.UNNTATT_INNSYN,
                sd.OPPFOLGINGSBEHOV, 
                sd.STATUS, 
                sd.ORGNUMMER, 
                sd.SENDT_TIL_ARBEIDSGIVER_DATO,
                sd.BEHANDLET_DATO, 
                sd.OPPFOELGING, 
                sd.MOTTAK_ID, 
                sd.ARBEIDSGIVER_ID,
                sp.SM_SPORSMAL_ID as SPM_SM_SPORSMAL_ID, 
                sp.SYKMELDING_ID as SPM_SYKMELDING_ID, 
                sp.ARBEIDSSITUASJON as SPM_ARBEIDSSITUASJON, 
                sp.HAR_FORSIKRING as SPM_HAR_FORSIKRING, 
                sp.HAR_FRAVAER as SPM_HAR_FRAVAER,
                sa.ARBEIDSGIVER_ID as ARB_ARBEIDSGIVER_ID, 
                sa.ORGNUMMER as ARB_ORGNUMMER,  
                sa.JURIDISK_ORGNUMMER as ARB_JURIDISK_ORGNUMMER,  
                sa.NAVN as ARB_NAVN FROM SYFOSERVICE.SYKMELDING_DOK sd 
                left outer join sm_arbeidsgiver sa on (sa.arbeidsgiver_id = sd.arbeidsgiver_id)
                left outer join sm_sporsmal sp on (sd.sykmelding_dok_id = sp.sykmelding_id)
                WHERE MELDING_ID = ANY ('50c04985-7b25-47c5-9d90-7178cccbab5e')
                """
        ).use {
            val resultSet = it.executeQuery()
            val databaseResult = resultSet.toJsonStringSyfoService(0)
            return databaseResult
        }
    }

fun DatabaseInterfaceOracle.hentSykmeldingerSyfoService(
    lastIndex: Int,
    limit: Int
): DatabaseResult<MutableMap<String, Any?>> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT sd.SYKMELDING_DOK_ID, 
                sd.AKTOR_ID, 
                sd.MELDING_ID, 
                sd.DOKUMENT, 
                sd.CREATED, 
                sd.ARBEIDSSITUASJON, 
                sd.SYKETILFELLE_START_DATO,
                sd.UNNTATT_INNSYN,
                sd.OPPFOLGINGSBEHOV, 
                sd.STATUS, 
                sd.ORGNUMMER, 
                sd.SENDT_TIL_ARBEIDSGIVER_DATO,
                sd.BEHANDLET_DATO, 
                sd.OPPFOELGING, 
                sd.MOTTAK_ID, 
                sd.ARBEIDSGIVER_ID,
                sp.SM_SPORSMAL_ID as SPM_SM_SPORSMAL_ID, 
                sp.SYKMELDING_ID as SPM_SYKMELDING_ID, 
                sp.ARBEIDSSITUASJON as SPM_ARBEIDSSITUASJON, 
                sp.HAR_FORSIKRING as SPM_HAR_FORSIKRING, 
                sp.HAR_FRAVAER as SPM_HAR_FRAVAER,
                sa.ARBEIDSGIVER_ID as ARB_ARBEIDSGIVER_ID, 
                sa.ORGNUMMER as ARB_ORGNUMMER,  
                sa.JURIDISK_ORGNUMMER as ARB_JURIDISK_ORGNUMMER,  
                sa.NAVN as ARB_NAVN FROM SYFOSERVICE.SYKMELDING_DOK sd 
                left outer join sm_arbeidsgiver sa on (sa.arbeidsgiver_id = sd.arbeidsgiver_id)
                left outer join sm_sporsmal sp on (sd.sykmelding_dok_id = sp.sykmelding_id)
                WHERE SYKMELDING_DOK_ID > ?
                AND SYKMELDING_DOK_ID <= ?
                ORDER BY SYKMELDING_DOK_ID ASC
                FETCH NEXT ? ROWS ONLY
                """
        ).use {
            it.setInt(1, lastIndex)
            it.setInt(2, limit + lastIndex)
            it.setInt(3, limit)
            val resultSet = it.executeQuery()
            val databaseResult = resultSet.toJsonStringSyfoService(lastIndex)
            return databaseResult
        }
    }

fun ResultSet.toJsonStringSyfoServiceFravaer(previusIndex: Int = 0): DatabaseResult<MutableMap<String, Any?>> {
    val listOfRows = ArrayList<HashMap<String, Any?>>()

    val metadata = this.metaData
    val columns = metadata.columnCount
    while (this.next()) {
        val rowMap = HashMap<String, Any?>()
        for (i in 1..columns) {
            var data: Any?
            if (metadata.getColumnClassName(i).contains("oracle.sql.TIMESTAMP")) {
                data = getTimestamp(i)
            } else {
                data = getObject(i)
            }
            rowMap[metadata.getColumnName(i)] = data
        }
        listOfRows.add(rowMap)
    }
    return DatabaseResult(0, listOfRows)
}

fun ResultSet.toJsonStringSyfoService(previusIndex: Int): DatabaseResult<MutableMap<String, Any?>> {
    val listOfRows = ArrayList<MutableMap<String, Any?>>()

    val metadata = this.metaData
    val columns = metadata.columnCount
    var lastIndex = previusIndex
    while (this.next()) {
        val rowMap = HashMap<String, Any?>()
        lastIndex = getInt("SYKMELDING_DOK_ID")
        for (i in 1..columns) {
            var data: Any?
            if (metadata.getColumnClassName(i).contains("oracle.sql.TIMESTAMP")) {
                data = getTimestamp(i)
            } else if (metadata.getColumnClassName(i).contains("oracle.sql.CLOB") || metadata.getColumnName(i) == "DOKUMENT") {
                data = getString(i)
            } else if (metadata.getColumnName(i) == "HAR_FRAVAER") {
                data = getString(i)
            } else if (metadata.getColumnName(i) == "SM_SPORSMAL_ID") {
                data = getInt(i)
            } else {
                data = getObject(i)
            }
            rowMap[metadata.getColumnName(i)] = data
        }
        listOfRows.add(rowMap)
    }
    return DatabaseResult(lastIndex, listOfRows)
}

fun DatabaseInterfaceOracle.hentAntallSykmeldingerSyfoService(): List<AntallSykmeldinger> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                        SELECT COUNT(MOTTAK_ID) AS antall
                        FROM SYKMELDING_DOK
                        """
        ).use {
            it.executeQuery().toList { toAntallSykmeldinger() }
        }
    }

data class AntallSykmeldinger(
    val antall: String
)

fun ResultSet.toAntallSykmeldinger(): AntallSykmeldinger =
    AntallSykmeldinger(
        antall = getString("antall")
    )
